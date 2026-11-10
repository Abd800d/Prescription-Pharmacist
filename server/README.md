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
