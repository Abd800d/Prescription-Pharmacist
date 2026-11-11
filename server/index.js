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
 * POST {baseUrl}/chat — RAG drug assistant (Arabic + English).
 *
 * Body: {
 *   "messages": [{"role":"user|assistant","content":"…"}],
 *   "evidence": [{"id":1,"text":"…","sourceName":"…","sourceUrl":"…","sourceDate":"…"}],
 *   "language": "ar|en"
 * }
 * Resp: {"reply":"…","citations":[{"sourceName":"…","sourceUrl":"…","sourceDate":"…"}]}
 *
 * The model answers FROM the provided evidence only. Citations can only point
 * at provided evidence items — fabricated sources/links are stripped. With no
 * evidence the endpoint answers with an explicit refusal and never invents
 * information. Rate limited per caller (uid or IP).
 */
const CHAT_RATE_PER_MINUTE = 10;
const CHAT_RATE_PER_HOUR = 60;
const chatBuckets = new Map();

function chatRateOk(key) {
  const now = Date.now();
  const bucket = chatBuckets.get(key) ?? [];
  const recent = bucket.filter((t) => now - t < 60 * 60 * 1000);
  if (
    recent.length >= CHAT_RATE_PER_HOUR ||
    recent.filter((t) => now - t < 60 * 1000).length >= CHAT_RATE_PER_MINUTE
  ) {
    chatBuckets.set(key, recent);
    return false;
  }
  recent.push(now);
  chatBuckets.set(key, recent);
  return true;
}

const URL_REGEX = /https?:\/\/[^\s)\]>"'`]+/g;
const MARKDOWN_LINK_REGEX = /\[([^\]]+)]\((https?:\/\/[^)\s]+)\)/g;
const CITED_ID_REGEX = /\[(\d{1,2})]/g;

function stripForeignLinks(text, allowedUrls) {
  let out = String(text ?? "").replace(MARKDOWN_LINK_REGEX, (m, label, url) =>
    allowedUrls.has(url) ? m : label
  );
  out = out.replace(URL_REGEX, (m) => (allowedUrls.has(m.replace(/\.$/, "")) ? m : ""));
  return out;
}

function buildCitations(reply, evidence) {
  const ids = new Set();
  String(reply ?? "").replace(CITED_ID_REGEX, (m, n) => {
    ids.add(Number(n));
    return m;
  });
  const picked = ids.size
    ? evidence.filter((e) => ids.has(Number(e.id)))
    : evidence;
  const seen = new Set();
  const out = [];
  for (const e of picked) {
    const key = `${e.sourceName}|${e.sourceUrl}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({
      sourceName: String(e.sourceName ?? ""),
      sourceUrl: String(e.sourceUrl ?? ""),
      sourceDate: String(e.sourceDate ?? ""),
    });
    if (out.length >= 6) break;
  }
  return out;
}

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

    // ---- rate limiting (per user, falling back to client IP) ----
    const callerKey = uid || req.ip || "anonymous";
    if (!chatRateOk(`chat:${callerKey}`)) {
      res.status(429).json({
        error: { code: "rate_limited", message: "Too many requests — try again later" },
      });
      return;
    }

    let messages;
    let evidence = [];
    let language = "en";
    try {
      const body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
      messages = body?.messages;
      if (!Array.isArray(messages) || messages.length === 0) throw new Error("messages required");
      if (Array.isArray(body?.evidence)) {
        evidence = body.evidence
          .filter((e) => e && Number.isInteger(Number(e.id)) && String(e.text ?? "").trim())
          .map((e, i) => ({
            id: Number(e.id) || i + 1,
            text: String(e.text).slice(0, 500),
            sourceName: String(e.sourceName ?? "").slice(0, 120),
            sourceUrl: String(e.sourceUrl ?? "").slice(0, 300),
            sourceDate: String(e.sourceDate ?? "").slice(0, 40),
          }))
          .slice(0, 30);
      }
      if (body?.language === "ar" || body?.language === "en") language = body.language;
    } catch (e) {
      res.status(400).json({ error: { code: "bad_request", message: e.message } });
      return;
    }

    const refusal =
      language === "ar"
        ? "لا توجد معلومات موثوقة كافية للإجابة عن هذا السؤال. يرجى الرجوع إلى الطبيب أو الصيدلي."
        : "There is not enough reliable information to answer this question. Please consult a doctor or pharmacist.";

    // ---- no evidence → explicit refusal, never a free-form guess ----
    if (evidence.length === 0) {
      res.status(200).json({ reply: refusal, citations: [] });
      return;
    }

    const allowedUrls = new Set(
      evidence.map((e) => e.sourceUrl).filter(Boolean)
    );
    const evidenceBlock = evidence
      .map((e) => `[${e.id}] ${e.text} (source: ${e.sourceName}${e.sourceDate ? ", " + e.sourceDate : ""})`)
      .join("\n");

    try {
      const endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
      const payload = {
        systemInstruction: {
          parts: [
            {
              text:
                "You are a clinical pharmacist assistant answering STRICTLY from the " +
                "evidence provided below. Rules:\n" +
                "1. Use ONLY facts stated in the evidence. Never use outside knowledge.\n" +
                "2. Cite evidence ids inline like [1], [2] for every factual claim.\n" +
                "3. If the evidence does not contain the answer, reply exactly: \"" +
                refusal +
                "\"\n" +
                "4. Never provide a final diagnosis. Never tell the user to change a dose " +
                "or stop a medication — always refer them to their doctor for any change.\n" +
                "5. Never invent sources, names, links or dates. Only cite the evidence ids.\n" +
                "6. Answer in " + (language === "ar" ? "Arabic" : "English") + ".\n\n" +
                "Evidence:\n" + evidenceBlock,
            },
          ],
        },
        contents: messages.map((m) => ({
          role: m.role === "assistant" ? "model" : "user",
          parts: [{ text: String(m.content ?? "") }],
        })),
        generationConfig: { temperature: 0.2 },
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
      let reply = data?.candidates?.[0]?.content?.parts?.[0]?.text;
      if (!reply) {
        res.status(502).json({
          error: { code: "analysis_failed", message: "Empty upstream reply" },
        });
        return;
      }

      // ---- guard: strip fabricated links; citations from evidence only ----
      reply = stripForeignLinks(reply, allowedUrls);
      const citations = buildCitations(reply, evidence);
      res.status(200).json({ reply, citations });
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

/**
 * POST {baseUrl}/druginfo/search — official drug-information sources.
 * Body: {"query": "Panadol"}
 * Resp: {"facts": [{"field", "value", "sourceName", "sourceUrl", "retrievedAt"}], "note"}
 *
 * Aggregation point for OFFICIAL sources only (never general web search):
 *  - openFDA (keyless) is proxied server-side;
 *  - authority APIs (ministry of health, local drug authority) are called
 *    with their API key read from Firebase secrets — NO key ever lives in
 *    the Android app. Add new authorities inside callAuthoritySources().
 */
const AUTHORITY_API_KEY = defineSecret("AUTHORITY_API_KEY");

async function callOpenFdaFacts(query) {
  const search = `(openfda.brand_name:"${query}" OR openfda.generic_name:"${query}")`;
  const url =
    "https://api.fda.gov/drug/label.json?limit=1&search=" + encodeURIComponent(search);
  const resp = await fetch(url, { signal: AbortSignal.timeout(10000) });
  if (resp.status === 404) return [];
  if (!resp.ok) throw new Error("openFDA http " + resp.status);
  const body = await resp.json();
  const drug = body.results && body.results[0];
  if (!drug) return [];
  const now = Date.now();
  const src = {
    sourceName: "openFDA (US FDA)",
    sourceUrl: "https://open.fda.gov/apis/drug/label/",
    retrievedAt: now,
  };
  const facts = [];
  const add = (field, value) => {
    if (value && String(value).trim()) {
      facts.push({ field, value: String(value).trim().slice(0, 900), ...src });
    }
  };
  const info = drug.openfda || {};
  add("tradeName", (info.brand_name || [])[0]);
  add("genericName", (info.generic_name || [])[0]);
  add("manufacturer", (info.manufacturer_name || [])[0]);
  add("dosageForm", (info.route || [])[0]);
  add("indications", (drug.indications_and_usage || [])[0]);
  add("dosage", (drug.dosage_and_administration || [])[0]);
  add("warnings", (drug.warnings || [])[0]);
  add("description", (drug.description || [])[0]);
  return facts;
}

/**
 * Seam for national authority APIs (ministry of health, local drug
 * authority). Each source returns facts already attributed to itself.
 * The API key comes from Firebase secrets — never from the app.
 */
async function callAuthoritySources(query, apiKey) {
  if (!apiKey) return [];
  // Example (uncomment and adapt when a national API is available):
  // const resp = await fetch("https://api.example-moh.gov.ye/drugs?q=" + encodeURIComponent(query), {
  //   headers: { Authorization: "Bearer " + apiKey },
  //   signal: AbortSignal.timeout(10000),
  // });
  // if (!resp.ok) throw new Error("authority http " + resp.status);
  // const body = await resp.json();
  // return (body.items || []).map((item) => ({
  //   field: item.field,
  //   value: item.value,
  //   sourceName: "Ministry of Health — Drug Authority",
  //   sourceUrl: "https://api.example-moh.gov.ye",
  //   retrievedAt: Date.now(),
  // }));
  return [];
}

exports.druginfo = onRequest(
  {
    region: "europe-west1",
    cors: true,
    secrets: [AUTHORITY_API_KEY],
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

    let query;
    try {
      const body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
      query = String(body?.query ?? "").trim();
      if (!query) throw new Error("query required");
      if (query.length > 200) throw new Error("query too long");
    } catch (e) {
      res.status(400).json({ error: { code: "bad_request", message: e.message } });
      return;
    }

    try {
      const [openFda, authority] = await Promise.allSettled([
        callOpenFdaFacts(query),
        callAuthoritySources(query, AUTHORITY_API_KEY.value()),
      ]);
      const facts = [];
      const notes = [];
      if (openFda.status === "fulfilled") facts.push(...openFda.value);
      else notes.push("openFDA unavailable");
      if (authority.status === "fulfilled") facts.push(...authority.value);
      else notes.push("authority source unavailable");
      res.status(200).json({ facts, note: notes.join("; ") || undefined });
    } catch (e) {
      console.error("druginfo failed", e);
      res.status(502).json({
        error: { code: "upstream_failed", message: "Official sources are unavailable" },
      });
    }
  }
);

/**
 * Role helpers — roles live in the Firebase ID-token custom claim `role`
 * (user | pharmacist | admin). They are set ONLY through the admin endpoint
 * below, never by clients. Firestore rules read the same claim.
 */
async function requireRole(req, allowed) {
  const uid = await verifyToken(req);
  if (!uid) return null;
  const user = await admin.auth().getUser(uid);
  const role = (user.customClaims && user.customClaims.role) || "user";
  const allowedList = Array.isArray(allowed) ? allowed : [allowed];
  if (!allowedList.includes(role)) return null;
  return { uid, role };
}

/**
 * POST {baseUrl}/setrole — admin only.
 * Body: {"uid": "...", "role": "user|pharmacist|admin"}
 * Sets the target user's `role` custom claim (used by Firestore rules and
 * the app). Bootstrap the first admin with the Firebase Admin SDK or the
 * console — see README "أدوار المستخدمين".
 */
exports.setrole = onRequest(
  {
    region: "europe-west1",
    cors: true,
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: { code: "method_not_allowed", message: "Use POST" } });
      return;
    }

    const adminCaller = await requireRole(req, "admin");
    if (!adminCaller) {
      res.status(403).json({
        error: { code: "forbidden", message: "Admin role required" },
      });
      return;
    }

    let uid, role;
    try {
      const body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
      uid = String(body?.uid ?? "").trim();
      role = String(body?.role ?? "").trim().toLowerCase();
      if (!uid) throw new Error("uid required");
      if (!["user", "pharmacist", "admin"].includes(role)) throw new Error("invalid role");
    } catch (e) {
      res.status(400).json({ error: { code: "bad_request", message: e.message } });
      return;
    }

    try {
      await admin.auth().setCustomUserClaims(uid, { role });
      res.status(200).json({ uid, role, updatedBy: adminCaller.uid });
    } catch (e) {
      console.error("setrole failed", e);
      res.status(502).json({
        error: { code: "role_update_failed", message: "Could not update the user role" },
      });
    }
  }
);
