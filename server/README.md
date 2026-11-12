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
POST {baseUrl}/druginfo/search            (طبقة البحث الدوائي من المصادر الرسمية)
Content-Type: application/json   Body: {"query": "Panadol"}
Authorization: Bearer <Firebase ID token>   (اختياري)

200 OK:
{
  "facts": [
    {
      "field": "warnings",
      "value": "…",
      "sourceName": "openFDA (US FDA)",
      "sourceUrl": "https://open.fda.gov/apis/drug/label/",
      "retrievedAt": 1700000000000
    }
  ],
  "note": "openFDA unavailable"        (اختياري — عند تعذر مصدر)
}

• يجمّع المصادر الرسمية فقط (openFDA + واجهات وزارات الصحة/هيئات الدواء مستقبلاً).
• مفاتيح واجهات الهيئات تُخزَّن كسرّات Firebase (AUTHORITY_API_KEY) — لا مفتاح داخل التطبيق.
• المصادر الجديدة تُضاف داخل callAuthoritySources() في index.js.
خطأ: 4xx/5xx مع {"error": {"code": "...", "message": "..."}}
```

```
GET {baseUrl}/drugs?q=<query>&dosageForm=<form>&manufacturer=<name>&verified=<true|false>
Authorization: Bearer <Firebase ID token>   (اختياري)

200 OK: قائمة السجلات الدوائية:
[
  {
    "id": "…",
    "tradeNameArabic": "بنادول",
    "tradeNameEnglish": "Panadol",
    "genericNameArabic": "باراسيتامول",
    "genericNameEnglish": "Paracetamol",
    "activeIngredient": "Paracetamol",
    "strength": "500 mg",
    "dosageForm": "tablet",
    "manufacturer": "GSK",
    "barcode": "1234567890",
    "indications": "…", "contraindications": "…", "warnings": "…",
    "sideEffects": "…", "interactions": "…",
    "pregnancyInformation": "…", "lactationInformation": "…",
    "productImageUrl": "…",
    "sourceName": "openFDA", "sourceUrl": "…", "sourceDate": "…",
    "updatedAt": 1700000000000, "createdBy": "…", "isVerified": true
  }
]

GET    {baseUrl}/drugs/{id}   → سجل واحد
POST   {baseUrl}/drugs        → إنشاء (409 عند تكرار الباركود)
PUT    {baseUrl}/drugs/{id}   → تحديث كامل
DELETE {baseUrl}/drugs/{id}   → حذف
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
firebase functions:secrets:set AUTHORITY_API_KEY # مفاتيح واجهات الهيئات الرسمية — الخادم فقط
firebase deploy --only functions
```

سيكون الرابط الناتج من الشكل:
`https://europe-west1-<project-id>.cloudfunctions.net/analyze`

أدخل هذا الرابط في تطبيق Android من: **الإعدادات ← رابط الخادم الآمن**.

## الأمان

- مفتاح نموذج الرؤية (Gemini) مخزّن كـ **secret** في Cloud Functions، ولا يظهر في الكود أو في التطبيق.
- يُتحقق من Firebase ID token إن أرسله التطبيق.
- الحد الأقصى لحجم الصورة 8MB، ويُعاد استخدام الصورة فقط للتحليل دون تخزين دائم.
