# سياسة الخصوصية — صيدلي الوصفة (Prescription Pharmacist)

**آخر تحديث:** 2026-11-12 · **الإصدار:** 1.0.0

> English version below the Arabic text.

## العربية

### 1. نظرة عامة
صيدلي الوصفة تطبيق يدعم الصيادلة في قراءة الوصفات الطبية وإدارة قاعدة الأدوية. خصوصيتك وبياناتك الصحية أولوية التصميم: **بياناتك تبقى على جهازك أولاً**، وكل ما يغادر الجهاز يكون بموافقتك ومشفّراً.

### 2. البيانات التي نجمعها
| البيانات | أين تُخزَّن | تغادر الجهاز؟ |
|----------|-----------|---------------|
| صور الوصفات | الجهاز (مشفّرة AES-256-GCM) | فقط عند تشغيل التحليل، إلى خادم التحليل الآمن |
| بيانات الأدوية والوصفات | قاعدة بيانات محلية | فقط عند تفعيل المزامنة (اختياري) |
| سجلات النشاط الأمني | محلي (معرّفات ورموز فقط) | لا |
| حساب المصادقة | Firebase Authentication | عند تسجيل الدخول فقط |

### 3. معالجة الصور والذكاء الاصطناعي
- تُرسَل صور الوصفات إلى خادم التحليل الآمن **فقط عند تشغيل التحليل**، وتُستخدم **لإنتاج نتيجة الاستخراج فقط**.
- **لا نبيع بياناتك ولا نشاركها مع معلنين أو أطراف ثالثة للتسويق.**
- **النتائج قد تكون ناقصاً أو غير دقيقة** — يجب مراجعة كل نتيجة من صيدلاني مرخّص. التطبيق **لا يدّعي دقة مطلقة** ولا يحل محل الاحتراف المهني.

### 4. التخزين السحابي (اختياري)
المزامنة والنسخ الاحتياطي السحابي **اختياريان**، مشفّران (PBKDF2 + AES-256-GCM)، ويخزنان في مساحتك الخاصة في Firebase (مجلدات `users/{uid}/…` المالك-فقط).

### 5. الإعلانات والتتبع
**لا يحتوي التطبيق على إعلانات أو أدوات تتبّع أو SDKs تسويقية.**

### 6. حقوقك
- **الاطلاع:** كل بياناتك مرئية داخل التطبيق.
- **الحذف:** حذف أي سجل فوراً؛ أو «حذف بياناتي على هذا الجهاز» (الخصوصية) يمسح كل شيء محلياً؛ طلب حذف الحساب والنسخ السحابية يُنفَّذ **خلال 30 يوماً**.
- **السحب:** يمكنك سحب موافقتك على معالجة البيانات في أي وقت.

### 7. الأطفال
التطبيق مخصص للصيادلة المحترفين وليس للأطفال دون 13 عاماً.

### 8. التواصل
من داخل التطبيق: الدعم الفني (واتساب/البريد) — `7a8b1d@gmail.com`.

---

## English

**Prescription Pharmacist — Privacy Policy** (updated 2026-11-12, v1.0.0)

1. **Overview.** A pharmacist-support app for prescription reading and drug-database management. Your health data stays on your device first; anything that leaves does so with your consent and encrypted.
2. **Data we store.** Prescription images (encrypted at rest, AES-256-GCM), drug/prescription records (local database), security audit entries (opaque IDs only), and — only if you sign in — a Firebase auth account.
3. **AI image processing.** Images are sent to the app's secure analysis backend **only when you run an analysis**, and only to produce the extraction result. We **never sell** your data or share it with advertisers. **AI results may be incomplete or incorrect** and always require review by a licensed pharmacist — the app **does not claim absolute accuracy** and never replaces professional judgement.
4. **Cloud storage (optional).** Sync and backup are optional, end-product encrypted (PBKDF2 + AES-256-GCM), and stored only in your private `users/{uid}/…` space.
5. **No ads, no tracking SDKs.**
6. **Your rights.** View everything in-app; delete any record; wipe all local data (Settings → Privacy); request account + cloud deletion (completed within 30 days); withdraw consent anytime.
7. **Children.** For professional pharmacists, not children under 13.
8. **Contact.** In-app technical support (WhatsApp/email) — `7a8b1d@gmail.com`.
