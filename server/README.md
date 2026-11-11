# خادم التحليل الآمن — Secure Analysis Backend

خادم حقيقي قابل للنشر (Firebase Cloud Functions / Node 20) يطبّق عقد الواجهة الذي يستهلكه تطبيق Android.

## العقد (API contract)

```
POST {baseUrl}/analyze
Content-Type: multipart/form-data   (حقل "image": JPEG ≤ 8MB)
Authorization: Bearer <Firebase ID token>   (اختياري، ويُتحقّق منه إن وُجد)

200 OK:
{
  "requestId": "…",
  "language": "ar",
  "notes": "…",
  "medications": [
    {
      "name": "بنادول اكسترا",
      "scientificName": "Paracetamol + Caffeine",
      "type": "BRAND",
      "strength": "500mg",
      "form": "tablet",
      "confidence": 0.92,
      "rawText": "بنادول اكسترا 500"
    }
  ]
}

خطأ: 4xx/5xx مع {"error": {"code": "...", "message": "..."}}
```

```
POST {baseUrl}/ocr                    (طبقة OCR — المرحلة 3)
Content-Type: multipart/form-data   (حقل "image": JPEG ≤ 8MB)
Authorization: Bearer <Firebase ID token>   (اختياري، ويُتحقّق منه إن وُجد)

200 OK:
{
  "requestId": "…",
  "language": "ar|en|mixed",
  "text": "النص الكامل منسوباً كما هو",
  "lines": [{ "text": "سطر واحد", "confidence": 1.0 }]
}

القواعد: النسخ الحرفي فقط — لا تفسير ولا ترجمة ولا اختراع؛ غير المقروء يُكتب بـ «?».
```

```
POST {baseUrl}/extract                (طبقة الاستخراج بالذكاء الاصطناعي — المراحل 5-8)
Content-Type: application/json      {"text": "… نص OCR …"}

200 OK:
{
  "hints": [
    {
      "name": "بنادول اكسترا",
      "scientificName": "Paracetamol + Caffeine",
      "type": "BRAND",
      "strength": "500mg",
      "form": "tablet",
      "dose": "قرص مرتين يومياً",
      "route": "oral",
      "duration": "5 أيام",
      "rawText": "بنادول اكسترا 500",
      "unclear": false,
      "alternatives": []
    }
  ]
}

القواعد: الاستخراج من النص فقط — ممنوع اختراع اسم أو جرعة؛ عند الغموض unclear=true مع قائمة بدائل.
```

```
POST {baseUrl}/chat                   (المساعد الصيدلي — اختياري)
Body: {"messages":[{"role":"user","content":"…"}]}
200 OK: {"reply":"…"}
```

**ملاحظة أمنية:** مفتاح Gemini يُخزَّن كسرّ بيئة فقط (`firebase functions:secrets:set GEMINI_API_KEY`) — لا يُكتب داخل الكود إطلاقاً.

## النشر

```bash
cd server
npm install

# 1) أنشئ مشروعاً في Firebase Console وفعّل Authentication (Anonymous) و Storage
# 2) ثبّت Firebase CLI ثم:
firebase login
firebase init functions          # اختر المشروع
firebase functions:secrets:set GEMINI_API_KEY    # مفتاح Gemini — لا يوضع في الكود أبداً
firebase deploy --only functions
```

سيكون الرابط الناتج من الشكل:
`https://europe-west1-<project-id>.cloudfunctions.net/analyze`

أدخل هذا الرابط في تطبيق Android من: **الإعدادات ← رابط الخادم الآمن**.

## الأمان

- مفتاح نموذج الرؤية (Gemini) مخزّن كـ **secret** في Cloud Functions، ولا يظهر في الكود أو في التطبيق.
- يُتحقق من Firebase ID token إن أرسله التطبيق.
- الحد الأقصى لحجم الصورة 8MB، ويُعاد استخدام الصورة فقط للتحليل دون تخزين دائم.
