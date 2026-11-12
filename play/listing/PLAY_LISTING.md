# Google Play — Store Listing (AR + EN)

> Material for the Play Console. Screenshots: `play/screenshots/` (replace with real device captures via `fastlane screengrab` before submission — see RELEASE_CHECKLIST.md).
> Assets: `play/assets/icon-512.png`, `play/assets/feature-graphic-1024x500.png`.

---

## العنوان / Title
**AR:** صيدلي الوصفة — إدارة الوصفات والأدوية
**EN:** Prescription Pharmacist — Prescriptions & Drugs

## الوصف القصير / Short description
**AR:** أداة الصيدلي لإدارة الوصفات وقراءتها بالذكاء الاصطناعي ومراجعتها — دون إنترنت أولاً، وبخصوصية كاملة.
**EN:** Pharmacist tool for prescription capture, AI-assisted reading and review — offline-first, privacy by design.

## الوصف الكامل / Full description (AR)

صيدلي الوصفة — تطبيق يدعم الصيادلة في إدارة الوصفات الطبية وقاعدة الأدوية، بواجهة عربية وإنجليزية كاملة.

**المزايا:**
📷 التقاط صور الوصفات وقراءتها بالذكاء الاصطناعي (OCR) مع **مراجعة صيدلاني مطلوبة لكل نتيجة**.
💊 قاعدة أدوية محلية مع بحث عربي/إنجليزي ومصدر رسمي لكل معلومة.
📚 سجل الوصفات مع الحالة (مؤكدة / مرفوضة / قيد المراجعة).
📴 يعمل بالكامل دون إنترنت — بياناتك أولاً على جهازك.
🔄 مزامنة ونسخ احتياطي **مشفّر** اختياريان (AES-256).
🔒 تشفير صور الوصفات عند التخزين، وصلاحيات مالك-فقط للسحابة.
🎧 دعم فني مباشر (واتساب / بريد) مع نظام بلاغات.
🗑️ حذف كامل للبيانات متاح في أي وقت.

**تنبيه مهم:** هذا التطبيق أداة دعم قرار للصيدلاني — **لا يقدّم تشخيصاً طبياً، ولا يحل محل الاحتراف المهني**. نتائج الذكاء الاصطناعي قد تكون ناقصاً أو غير دقيقة و**لا تُعتمد إلا بعد مراجعة صيدلاني مرخّص**. التطبيق **لا يدّعي دقة مطلقة** في قراءة الوصفات.

**الخصوصية:** لا إعلانات ولا تتبّع. صورك وبياناتك الصحية لا تُباع أبداً. راجع سياسة الخصوصية الكاملة.

## Full description (EN)

Prescription Pharmacist supports pharmacists with prescription capture, AI-assisted reading, and a full local drug database — Arabic and English UI.

**Features:**
📷 Capture prescriptions and read them with AI (OCR) — **every result requires pharmacist review**.
💊 Local drug database with AR/EN search and official-source attribution for every fact.
📚 Prescription history with pharmacist-controlled status.
📴 Fully offline-first — your data lives on your device.
🔄 Optional **encrypted** sync & backup (AES-256).
🔒 Prescription images encrypted at rest; owner-only cloud permissions.
🎧 Direct technical support (WhatsApp / email) with a reporting system.
🗑️ Complete data deletion available anytime.

**Important:** This is a pharmacist decision-SUPPORT tool — **not a medical diagnosis service and never a replacement for professional judgement**. AI extraction results may be incomplete or incorrect and **must be reviewed by a licensed pharmacist**. The app **does not claim absolute accuracy**.

**Privacy:** No ads, no trackers. Your images and health data are never sold. See the full privacy policy.

## التصنيف / Category
Medical (HEALTH_AND_FITNESS)

## تصنيف المحتوى / Content rating
Everyone 3+ (no ads, no UGC sharing, health reference tool — answer the IARC questionnaire accordingly).

## متطلبات / Requirements
- Firebase project (Auth/Firestore/Storage) configured by the operator; app works offline without it.
- Privacy policy URL: host `PRIVACY_POLICY.md` content publicly before submission.
