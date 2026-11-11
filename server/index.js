/**
 * Prescription Pharmacist — secure analysis backend (Firebase Cloud Functions).
 *
 * Implements the /analyze contract consumed by the Android app:
 *
 *   POST {baseUrl}/analyze
 *   Content-Type: multipart/form-data  (field "image": JPEG)
 *   Authorization: Bearer <Firebase ID token>   (optional but recommended)
 *
 *   200 OK ->
 *   {
 *     "requestId": "…",
 *     "language": "ar",
 *     "notes": "…",
 *     "medications": [
 *       {
 *         "name": "بنادول اكسترا",
 *         "scientificName": "Paracetamol + Caffeine",
 *         "type": "BRAND",            // BRAND | GENERIC | UNKNOWN
 *         "strength": "500mg",
 *         "form": "tablet",
 *         "confidence": 0.92,          // 0..1
 *         "rawText": "بنادول اكسترا 500"
 *       }
 *     ]
 *   }
 *
 *   Error -> 4xx/5xx with {"error": {"code": "...", "message": "..."}}
 *
 * The vision API key is a Cloud Function SECRET (never in source control):
 *   firebase functions:secrets:set GEMINI_API_KEY
 */

const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const Busboy = require("busboy");
const crypto = require("crypto");

admin.initializeApp();

const GEMINI_API_KEY = defineSecret("GEMINI_API_KEY");
const MAX_IMAGE_BYTES = 8 * 1024 * 1024;

/**
 * POST {baseUrl}/chat — AI pharmacist assistant.
 * Body: {"messages":[{"role":"user|assistant","content":"..."}]}
 * Resp: {"reply":"..."}
 */
exports.chat = onRequest(
  {
    region: "europe-west1",
    cors: true,
    secrets: [GEMINI_API_KEY],
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: { code: "method_not_allowed", message: "Use POST" } });
      return;
    }

    const hasAuthHeader = Boolean(req.headers.authorization);
    const uid = await verifyToken(req);
    if (hasAuthHeader && !uid) {
      res.status(401).json({
        error: { code: "unauthorized", message: "Invalid or expired ID token" },
      });
      return;
    }

    let messages;
    try {
      const body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
      messages = body?.messages;
      if (!Array.isArray(messages) || messages.length === 0) throw new Error("messages required");
    } catch (e) {
      res.status(400).json({ error: { code: "bad_request", message: e.message } });
      return;
    }

    try {
      const endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
      const payload = {
        systemInstruction: {
          parts: [
            {
              text:
                "You are a clinical pharmacist assistant. Give concise, accurate, " +
                "evidence-based information about medications. Always remind the user " +
                "that a licensed pharmacist must confirm any dispensing decision. " +
                "Answer in the language of the question (Arabic or English).",
            },
          ],
        },
        contents: messages.map((m) => ({
          role: m.role === "assistant" ? "model" : "user",
          parts: [{ text: String(m.content ?? "") }],
        })),
        generationConfig: { temperature: 0.3 },
      };

      const response = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": GEMINI_API_KEY.value(),
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        const detail = await response.text();
        console.error("chat upstream error", response.status, detail);
        res.status(502).json({
          error: { code: "analysis_failed", message: "Upstream analysis failed" },
        });
        return;
      }

      const data = await response.json();
      const reply = data?.candidates?.[0]?.content?.parts?.[0]?.text;
      if (!reply) {
        res.status(502).json({
          error: { code: "analysis_failed", message: "Empty upstream reply" },
        });
        return;
      }
      res.status(200).json({ reply });
    } catch (e) {
      console.error("chat failed", e);
      res.status(502).json({
        error: { code: "analysis_failed", message: "Upstream analysis failed" },
      });
    }
  }
);

/**
 * Parses a multipart/form-data request and returns the first "image" field
 * as a Buffer. Plain Node — no Express, no temporary files.
 */
function readMultipartImage(req) {
  return new Promise((resolve, reject) => {
    const contentType = req.headers["content-type"] || "";
    if (!contentType.includes("multipart/form-data")) {
      reject(new Error("Expected multipart/form-data"));
      return;
    }
    const busboy = Busboy({
      headers: req.headers,
      limits: { files: 1, fileSize: MAX_IMAGE_BYTES },
    });
    let resolved = false;
    busboy.on("file", (name, stream, info) => {
      if (name !== "image") {
        stream.resume();
        return;
      }
      const chunks = [];
      stream.on("data", (c) => chunks.push(c));
      stream.on("limit", () => {
        if (!resolved) {
          resolved = true;
          reject(new Error("Image exceeds 8 MB"));
        }
      });
      stream.on("end", () => {
        if (!resolved) {
          resolved = true;
          resolve({
            buffer: Buffer.concat(chunks),
            mimeType: info.mimeType || "image/jpeg",
          });
        }
      });
    });
    busboy.on("error", reject);
    busboy.end(req.rawBody);
  });
}

/** Verifies the Firebase ID token when present; returns uid or null. */
async function verifyToken(req) {
  const header = req.headers.authorization || "";
  const match = header.match(/^Bearer (.+)$/);
  if (!match) return null;
  try {
    const decoded = await admin.auth().verifyIdToken(match[1]);
    return decoded.uid;
  } catch (e) {
    return null;
  }
}

/** Calls the Gemini vision API and asks for strict JSON medication extraction. */
async function callVisionModel(imageBuffer, mimeType, apiKey) {
  const prompt = [
    "You are a clinical pharmacist assistant.",
    "Read the prescription photo (it may be handwritten, in Arabic and/or English).",
    "Extract every medication you can identify, including trade (brand) and/or generic names.",
    "Respond ONLY with a JSON object, no markdown, exactly this shape:",
    '{"language":"ar|en|mixed","notes":"free text summary or empty","medications":[{"name":"...","scientificName":"... or null","type":"BRAND|GENERIC|UNKNOWN","strength":"... or null","form":"... or null","confidence":0.0,"rawText":"... or null"}]}',
    "confidence must be between 0 and 1 and reflect how sure you are of each extraction.",
  ].join("\n");

  const endpoint =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

  const payload = {
    contents: [
      {
        parts: [
          { text: prompt },
          { inline_data: { mime_type: mimeType, data: imageBuffer.toString("base64") } },
        ],
      },
    ],
    generationConfig: {
      response_mime_type: "application/json",
      temperature: 0,
    },
  };

  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-goog-api-key": apiKey,
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Vision API error ${response.status}: ${detail}`);
  }

  const data = await response.json();
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) throw new Error("Vision API returned no content");
  return JSON.parse(text);
}

exports.analyze = onRequest(
  {
    region: "europe-west1",
    cors: true,
    secrets: [GEMINI_API_KEY],
    memory: "512MiB",
    timeoutSeconds: 120,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({
        error: { code: "method_not_allowed", message: "Use POST" },
      });
      return;
    }

    let image;
    try {
      image = await readMultipartImage(req);
    } catch (e) {
      res.status(400).json({
        error: { code: "bad_image", message: e.message },
      });
      return;
    }

    // Optional authentication: if the app sends a token it must be valid.
    const hasAuthHeader = Boolean(req.headers.authorization);
    const uid = await verifyToken(req);
    if (hasAuthHeader && !uid) {
      res.status(401).json({
        error: { code: "unauthorized", message: "Invalid or expired ID token" },
      });
      return;
    }

    try {
      const result = await callVisionModel(
        image.buffer,
        image.mimeType,
        GEMINI_API_KEY.value()
      );
      res.status(200).json({
        requestId: crypto.randomUUID(),
        language: result.language || null,
        notes: result.notes || null,
        medications: Array.isArray(result.medications) ? result.medications : [],
        analyzedBy: uid || "anonymous",
      });
    } catch (e) {
      console.error("analyze failed", e);
      res.status(502).json({
        error: { code: "analysis_failed", message: "Upstream analysis failed" },
      });
    }
  }
);

// ---------------------------------------------------------------------------
// Staged pipeline endpoints: /ocr (stage 3) and /extract (stages 5-8).
// The OCR layer and the AI extraction layer are independent so the app can
// swap providers. API keys live only in environment secrets (GEMINI_API_KEY).
// ---------------------------------------------------------------------------

/** Calls Gemini for STRICT transcription (no interpretation, no invention). */
async function callOcrModel(imageBuffer, mimeType, apiKey) {
  const prompt = [
    "You are an OCR engine for medical prescriptions.",
    "Transcribe ALL visible text in this prescription image (handwritten and/or printed, Arabic and/or English).",
    "STRICT RULES:",
    "- Transcribe only what is written. NEVER interpret, translate, correct or invent anything.",
    "- Keep the original line structure.",
    "- If a character or word is unreadable, replace ONLY that part with ? and continue.",
    "Respond ONLY with a JSON object, no markdown, exactly this shape:",
    '{"language":"ar|en|mixed","text":"full transcription with \\n line breaks","lines":[{"text":"one line"}]}',
  ].join("\n");

  const endpoint =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

  const payload = {
    contents: [
      {
        parts: [
          { text: prompt },
          { inline_data: { mime_type: mimeType, data: imageBuffer.toString("base64") } },
        ],
      },
    ],
    generationConfig: {
      response_mime_type: "application/json",
      temperature: 0,
    },
  };

  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-goog-api-key": apiKey,
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`OCR API error ${response.status}: ${detail}`);
  }

  const data = await response.json();
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) throw new Error("OCR API returned no content");
  return JSON.parse(text);
}

/** Calls Gemini to structure OCR text into grounded medication hints. */
async function callExtractModel(ocrText, apiKey) {
  const prompt = [
    "You are a clinical pharmacist assistant processing OCR output of a prescription.",
    "Extract medication entries STRICTLY from the given text.",
    "STRICT RULES:",
    "- NEVER invent a medication name, strength, dose, route or duration that is not literally supported by the text.",
    "- If a name is ambiguous or unclear, set unclear=true and list plausible alternatives in \"alternatives\".",
    "- Include the source line in rawText.",
    "Respond ONLY with a JSON object, no markdown, exactly this shape:",
    '{"hints":[{"name":"...","scientificName":"... or null","type":"BRAND|GENERIC|UNKNOWN","strength":"... or null","form":"... or null","dose":"... or null","route":"... or null","duration":"... or null","rawText":"...","unclear":false,"alternatives":[]}]}',
    "OCR TEXT:",
    ocrText,
  ].join("\n");

  const endpoint =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

  const payload = {
    contents: [{ parts: [{ text: prompt }] }],
    generationConfig: {
      response_mime_type: "application/json",
      temperature: 0,
    },
  };

  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-goog-api-key": apiKey,
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Extract API error ${response.status}: ${detail}`);
  }

  const data = await response.json();
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) throw new Error("Extract API returned no content");
  return JSON.parse(text);
}

/**
 * POST {baseUrl}/ocr — stage 3: image -> raw transcription.
 * Resp: {"requestId":"...","language":"ar|en|mixed","text":"...","lines":[{"text":"...","confidence":1.0}]}
 */
exports.ocr = onRequest(
  {
    region: "europe-west1",
    cors: true,
    secrets: [GEMINI_API_KEY],
    memory: "512MiB",
    timeoutSeconds: 120,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: { code: "method_not_allowed", message: "Use POST" } });
      return;
    }

    let image;
    try {
      image = await readMultipartImage(req);
    } catch (e) {
      res.status(400).json({ error: { code: "bad_image", message: e.message } });
      return;
    }

    const hasAuthHeader = Boolean(req.headers.authorization);
    const uid = await verifyToken(req);
    if (hasAuthHeader && !uid) {
      res.status(401).json({
        error: { code: "unauthorized", message: "Invalid or expired ID token" },
      });
      return;
    }

    try {
      const result = await callOcrModel(image.buffer, image.mimeType, GEMINI_API_KEY.value());
      const lines = Array.isArray(result.lines)
        ? result.lines.map((l) => ({ text: String(l?.text ?? ""), confidence: 1.0 }))
        : [];
      res.status(200).json({
        requestId: crypto.randomUUID(),
        language: result.language || null,
        text: String(result.text ?? ""),
        lines,
        transcribedBy: uid || "anonymous",
      });
    } catch (e) {
      console.error("ocr failed", e);
      res.status(502).json({
        error: { code: "analysis_failed", message: "Upstream OCR failed" },
      });
    }
  }
);

/**
 * POST {baseUrl}/extract — stages 5-8: OCR text -> structured medication hints.
 * Body: {"text":"..."}
 * Resp: {"hints":[{name, scientificName, type, strength, form, dose, route, duration, rawText, unclear, alternatives}]}
 */
exports.extract = onRequest(
  {
    region: "europe-west1",
    cors: true,
    secrets: [GEMINI_API_KEY],
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: { code: "method_not_allowed", message: "Use POST" } });
      return;
    }

    const hasAuthHeader = Boolean(req.headers.authorization);
    const uid = await verifyToken(req);
    if (hasAuthHeader && !uid) {
      res.status(401).json({
        error: { code: "unauthorized", message: "Invalid or expired ID token" },
      });
      return;
    }

    let ocrText;
    try {
      const body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
      ocrText = String(body?.text ?? "").trim();
      if (!ocrText) throw new Error("text required");
      if (ocrText.length > 20000) throw new Error("text too long");
    } catch (e) {
      res.status(400).json({ error: { code: "bad_request", message: e.message } });
      return;
    }

    try {
      const result = await callExtractModel(ocrText, GEMINI_API_KEY.value());
      res.status(200).json({
        hints: Array.isArray(result.hints) ? result.hints : [],
        extractedBy: uid || "anonymous",
      });
    } catch (e) {
      console.error("extract failed", e);
      res.status(502).json({
        error: { code: "analysis_failed", message: "Upstream extraction failed" },
      });
    }
  }
);
