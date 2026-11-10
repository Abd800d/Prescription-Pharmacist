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
