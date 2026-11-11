# 🧾 صيدلي الوصفة — Prescription Pharmacist

[![Android CI](https://github.com/Abd800d/Prescription-Pharmacist/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Abd800d/Prescription-Pharmacist/actions/workflows/android-ci.yml)

> **إعداد وتطوير د. عبدالماجد الماخذي** — *Developed by Dr. Abdulmajid Al-Maakthi*

تطبيق Android إنتاجي يساعد الصيدلي على **تصوير وصفة طبية** أو تحميلها من معرض الهاتف، ثم **تحليلها بالذكاء الاصطناعي** لاستخراج أسماء الأدوية (تجارية أو علمية) المكتوبة بخط اليد، مع إظهار **درجة الثقة** لكل نتيجة وطلب **تأكيد الصيدلي** قبل اعتمادها وحفظها.

<p align="center">
  <em>Kotlin • Jetpack Compose • MVVM • Clean Architecture • Material 3 • عربي RTL / English LTR</em>
</p>

---

## ✨ المزايا

| الميزة | الوصف |
|---|---|
| 📸 تصوير الوصفة | كاميرا CameraX مباشرة (ImageCapture) أو اختيار صورة من المعرض (Photo Picker) — نسخة عمل مؤقتة محلية تُنظَّف تلقائياً بعد 24 ساعة |
| ✂️ القص والتحرير | قص بإطار ثابت مع تكبير/سحب ونسب معيارية (حر، 1:1، 4:3، 3:4)، تدوير 90°، قلب أفقي، تحسين السطوع/التباين/الحدة، تحسين تلقائي، وتصحيح الميل (Auto-deskew) |
| 🖼️ معاينة وموافقة | شاشة معاينة نهائية مع مقاييس جودة حقيقية (وضوح، إضاءة، ميل، حجم الملف) — **لا يُرسل أي شيء للخادم قبل موافقة الصيدلي الصريحة** |
| 🤖 تحليل بالذكاء الاصطناعي | إرسال الصورة إلى خادم آمن يستخدم نموذج رؤية (Vision LLM) لاستخراج الأدوية المكتوبة بخط اليد بالعربية والإنجليزية |
| 📊 درجات الثقة | كل دواء مستخرج يظهر مع مستوى الثقة (عالية / متوسطة / منخفضة) ونسبة مئوية |
| ✅ تأكيد الصيدلي | النتيجة **لا تُعتمد** إلا بعد مراجعة الصيدلي وتأكيده — يمكن التعديل والإضافة والحذف قبل الاعتماد |
| 💊 بحث الأدوية | كتالوج محلي (Room) + قاعدة بيانات openFDA العالمية + إضافة/تعديل/حذف الأدوية |
| 🗂️ سجل الوصفات | حفظ محلي (Room) مع الحالات (معتمدة / بانتظار التأكيد / مرفوضة) وإمكانية التعديل لاحقاً |
| 🖼️ صور المنتجات | معرض صور لعلب الأدوية والرفوف (كاميرا + معرض) مع عناوين |
| 📥 استيراد وتصدير | تصدير/استيراد كامل البيانات كملف JSON عبر مستندات النظام |
| 🤖 المساعد الصيدلي الذكي | محادثة حقيقية مع خادم آمن (`/chat`) للاستفسارات الدوائية |
| 🔐 المصادقة والنسخ الاحتياطي | Firebase Auth (بريد + Google + مجهول) و Firebase Storage (اختيارية) — يعمل التطبيق بالكامل بدونهما |
| 🌍 عربي / إنجليزي | دعم كامل للعربية RTL والإنجليزية LTR مع زر تبديل فوري |
| 🌙 الوضع الليلي | فاتح / داكن / تلقائي + ألوان ديناميكية على Android 12+ |
| 📄 الشروط والخصوصية | شاشات السياسات وإخلاء المسؤولية الطبية باللغتين |
| 🔒 أمان | لا توجد مفاتيح API أو كلمات مرور في الكود — الخادم يُعدّ خارجياً |

---

## 🏗️ البنية التقنية

- **Kotlin 2.0** + **Jetpack Compose** (Material 3)
- **MVVM + Clean Architecture** (presentation / data / domain) مع StateFlow وUse Cases
- **Hilt** للتبعيات (Dependency Injection)
- **Room** لقاعدة البيانات المحلية
- **Retrofit + OkHttp + kotlinx.serialization** للاتصال بالخادم الآمن
- **CameraX** للتصوير + Photo Picker للمعرض
- **Coil** لعرض الصور
- **DataStore** لإعدادات المستخدم
- **Firebase Auth / Storage** (اختياري عبر `google-services.json`)
- **KSP** لـ Room وHilt
- اختبارات وحدات (JUnit + kotlinx-coroutines-test)

```
app/src/main/java/com/maakthi/prescriptionpharmacist/
├── core/            # الثيم، اللغة، أدوات الصور
├── domain/          # النماذج + واجهات المستودعات + حالات الاستخدام (خالص Kotlin)
├── data/            # Room، Retrofit، Firebase، تنفيذ المستودعات
├── di/              # وحدات Hilt
└── presentation/    # الشاشات + ViewModels + المكوّنات (Compose)
server/              # خادم التحليل الآمن (Firebase Cloud Function) — انظر server/README.md
```

## 📱 الشاشات (25 شاشة مربوطة بالتنقل والـ ViewModels)

| # | الشاشة | الوظيفة الفعلية |
|---|---|---|
| 1 | Splash | تحديد الوجهة (Onboarding / الرئيسية) |
| 2 | Onboarding | 3 صور تعريفية مع حفظ الإكمال |
| 3 | Login | دخول بالبريد وكلمة المرور (Firebase) |
| 4 | Register | إنشاء حساب (Firebase) |
| 5 | Google Sign-In | دخول Google حقيقي (يتطلب تهيئة Firebase) |
| 6 | Home | لوحة تحكم + إجراءات سريعة + أحدث الوصفات |
| 7 | Capture Prescription | تصوير CameraX |
| 8 | Select from Gallery | اختيار صورة من المعرض مع معاينة |
| 9 | Crop & Edit Image | تدوير/قلب/قص حقيقي للصورة |
| 10 | Prescription Analysis | تحليل حي مع حالات تقدم/خطأ/إعادة محاولة |
| 11 | Extracted Medicines | الأدوية المستخرجة مع درجات الثقة |
| 12 | Confirm & Edit | تعديل/إضافة/حذف + تأكيد الصيدلي |
| 13 | Drug Search | كتالوج محلي + قاعدة openFDA |
| 14 | Drug Details | تفاصيل الدواء (محلي أو openFDA) |
| 15 | Add Drug | إضافة دواء للكتالوج المحلي |
| 16 | Edit Drug | تعديل دواء |
| 17 | Saved Prescriptions | السجل مع الحالات والحذف |
| 18 | Product Images | معرض صور المنتجات (كاميرا/معرض) |
| 19 | Import & Export | تصدير/استيراد JSON عبر SAF |
| 20 | AI Pharmacist Assistant | محادثة حقيقية عبر الخادم الآمن |
| 21 | Settings | مركز الإعدادات + روابط الشاشات الفرعية |
| 22 | Language Settings | تبديل فوري عربي/إنجليزي |
| 23 | Backup & Sync | نسخ احتياطي حقيقي إلى Firebase Storage |
| 24 | Technical Support | بريد/هاتف/FAQ بإجراءات حقيقية |
| 25 | Privacy + Terms | سياسات وإخلاء مسؤولية طبية باللغتين |

---

## ⚙️ المتطلبات

| الأداة | الإصدار |
|---|---|
| JDK | 17+ |
| Android Studio | Ladybug أو أحدث (يُنصح) |
| Android SDK | compileSdk 35 — أدنى إصدار مدعوم: Android 7.0 (API 24) |
| Gradle | 8.10.2 (عبر الـ Wrapper المرفق) |
| AGP | 8.7.3 |

---

## 🚀 التشغيل والبناء

### 1) البناء السريع

```bash
git clone https://github.com/Abd800d/Prescription-Pharmacist.git
cd Prescription-Pharmacist

# بناء APK التصحيح
./gradlew assembleDebug
# الناتج: app/build/outputs/apk/debug/app-debug.apk

# بناء AAB للنشر على Google Play
./gradlew bundleRelease
# الناتج: app/build/outputs/bundle/release/app-release.aab
```

> على Windows استخدم `gradlew.bat` بدل `./gradlew`. عند أول بناء يُحمَّل Gradle تلقائياً.

### 2) إعداد خادم التحليل الآمن (مطلوب لعمل التحليل)

التطبيق **لا يحتوي على أي مفتاح API**. التحليل يتم عبر خادم آمن تملكه:

1. انظر [server/README.md](server/README.md) — خادم جاهز للنشر على Firebase Cloud Functions.
2. بعد النشر احصل على الرابط، ثم أدخله في التطبيق من:
   **الإعدادات ← رابط الخادم الآمن** *(أو ثبّته وقت البناء عبر `local.properties`)*.

```properties
# local.properties (يُنشأ محلياً ولا يُرفع إلى Git)
sdk.dir=/path/to/android/sdk
ANALYSIS_BASE_URL=https://europe-west1-your-project.cloudfunctions.net
```

### 3) تفعيل Firebase (اختياري)

لتفعيل المصادقة والنسخ الاحتياطي:

1. أنشئ مشروعاً في [Firebase Console](https://console.firebase.google.com).
2. فعّل **Authentication → Anonymous** و **Storage**.
3. حمّل ملف `google-services.json` وضعه في مجلد `app/`.
4. أعد البناء — يُفعَّل الإضافات تلقائياً (البناء يعمل بدون الملف في وضع محلي بالكامل).

> ⚠️ ملف `google-services.json` مستثنى من Git عمداً. لا ترفعه أبداً إلى المستودع.

### 4) إعداد التوقيع للإصدار النهائي

```properties
# local.properties
RELEASE_STORE_FILE=keystore/release.jks
RELEASE_STORE_PASSWORD=***
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=***
```

بدون هذه القيم يبقى `bundleRelease` **غير موقّع** (وهو أمر آمن افتراضياً).

---

## 🖼️ نظام إدخال صورة الوصفة (Pipeline حقيقي)

مسار الصورة الكامل: **التقاط (CameraX) / الاختيار من المعرض → نسخة عمل مؤقتة → القص والتحرير → معاينة + موافقة → التحليل**.

## 🧠 نظام تحليل الوصفة متعدد المراحل (قابل للتوسع)

| المرحلة | المكوّن | الوصف |
|---|---|---|
| 1–2 | خط الإدخال | استقبال الصورة وتحسينها (CameraX + معالجة الصور) |
| 3 | `OcrEngine` (واجهة مستقلة) | OCR للعربي والإنجليزي — التنفيذ الحالي `RemoteOcrEngine` (خادم `/ocr`)، قابل للاستبدال بمحرك آخر |
| 4 | `PrescriptionSegmenter` | تقسيم الوصفة إلى أسطر وتصفية الضجيج (ترويسات/تواريخ/توقيعات) |
| 5–8 | `AiExtractionEngine` (واجهة مستقلة) + `MedicationLineParser` | استخراج الاسم والتجاري/العلمي والتركيز والشكل الصيدلاني والجرعة وطريقة الاستخدام والمدة — تنفيذ بعيد (`/extract`) + محرك قواعد محلي كبديل |
| 9 | `DrugCatalogMatcher` | مطابقة النص مع قاعدة الأدوية (Room + openFDA) مع تطبيع عربي كامل |
| 10 | `ConfidenceScorer` | درجة ثقة مركبة لكل نتيجة (OCR + اكتمال الحقول + جودة المطابقة) |
| 11 | البدائل | عرض بدائل محتملة عند عدم وضوح الخط (أسماء متشابهة مرتّبة) |
| 12 | بوابة الصيدلي | **لا اعتماد دون تأكيد الصيدلي** — مربع تعهّد إلزامي + `requiresPharmacistConfirmation` |

**قواعد السلامة (مُختبَرة):**

- **ممنوع اختراع اسم دواء أو جرعة** — `GroundingFilter` يرفض أي حقل غير موجود حرفياً في نص OCR.
- عند انخفاض الثقة تُعرض النتيجة **«النتيجة غير مؤكدة»** (`isUncertain`).
- **لا يتم أي صرف آلي** — التطبيق أداة دعم قرار للصيدلي فقط.
- تحذير دائم: قراءة الخط اليدوي قد تكون غير دقيقة.
- مفاتيح API في متغيرات بيئة آمنة فقط (`GEMINI_API_KEY` كسرّ بيئة في الخادم) — لا شيء داخل الكود.

| المكوّن | المسار | الدور |
|---|---|---|
| `ImagePipeline` | `core/image/ImagePipeline.kt` | واجهة قابلة للحقن والاختبار: التقاط، استيراد، جودة، عرض، ضغط، تنظيف |
| `CropMath` | `core/image/CropMath.kt` | هندسة القص (نافذة مُعيَّرة، تكبير/سحب، نسب) — خوارزميات JVM خالصة |
| `ImageProcessing` | `core/image/ImageProcessing.kt` | قياسات حقيقية: ضبابية (Laplacian)، إضاءة، تباين، تقدير الميل (Projection Profile)، زيادة الحدة، سطوع/تباين |
| `ImagePipelineImpl` | `data/image/ImagePipelineImpl.kt` | تنفيذ حقيقي على `android.graphics`: عرض بدقة ≤ 2560px كـ PNG (بدون تدهور متراكم)، ضغط رفع JPEG بجودة 92 وحد أقصى 2048px يحافظ على وضوح الخط اليدوي |
| صلاحيات آمنة | `CaptureScreen` | طلب إذن الكاميرا فقط عند الحاجة، مع تفسير السبب، ورسالة رفض دائم مع فتح إعدادات التطبيق — دون أي أذونات تخزين غير ضرورية |
| التحقق من الجودة | `PreviewViewModel` | تحذيرات حقيقية قبل التحليل: صورة ضبابية / معتمة / ساطعة / مائلة — مع اقتراح التصحيح التلقائي |

**الخصوصية:** تُحفظ الصور في `filesDir/work_images` كملفات مؤقتة (تُحذف تلقائياً بعد 24 ساعة)، ولا يُسمح بأي اتصال بالخادم قبل الضغط على «بدء التحليل» بعد الموافقة الصريحة.

---

## 💊 قاعدة البيانات الدوائية (قابلة للتعديل)

جدول `drugs` في Room بالحقول المطلوبة كاملة: الأسماء التجارية والعلمية (عربي/إنجليزي)، المادة الفعالة، التركيز، الشكل الصيدلاني، الشركة المصنعة، الباركود، الاستطبابات، موانع الاستعمال، التحذيرات، الآثار الجانبية، التداخلات، معلومات الحمل والرضاعة، صورة المنتج، بيانات المصدر، تاريخ التحديث، المُضيف، وعلامة التوثيق.

| الميزة | التنفيذ |
|---|---|
| إضافة/تعديل/حذف | `AddDrugUseCase` / `UpdateDrugUseCase` / `DeleteDrugUseCase` |
| بحث متعدد الحقول | الاسم التجاري والعلمي (عربي/إنجليزي) + المادة الفعالة + الباركود — عبر فهرسة Room |
| تصفية النتائج | الشكل الصيدلاني + الشركة المصنعة + موثق/يدوي (`DrugFilters`) |
| صورة المنتج | اختيار من المعرض + نسخ إلى `filesDir` عبر `ImageUtils` |
| منع التكرار | فهرس فريد للباركود + مطابقة الاسم/التركيز/المصنّع المطبَّعة (`DrugEntryValidator`) |
| سجل التعديلات | جدول `drug_audit` (إضافة/تعديل/حذف + الحقول المتغيّرة + من + متى) |
| تأكيد قبل الحذف | حوار تأكيد في الشاشتين (`AlertDialog`) |
| موثق مقابل يدوي | `isVerified` + شارة في القوائم + حقول المصدر (اسم/رابط/تاريخ) |
| فهارس البحث | فريد (barcode) + فهارس على الأسماء الأربعة والمادة الفعالة والمصنّع والشكل |
| API | `DrugApi` (Retrofit CRUD: GET/POST/PUT/DELETE `drugs`) + `DrugEntryRepository` |

---

## 🔍 طبقة البحث الدوائي من المصادر الرسمية

بحث معلومات الدواء (النشرات، التحذيرات، الاستطبابات) من **مصادر رسمية موثوقة فقط** — لا يُستخدم البحث العام (Google…) كمصدر طبي نهائي.

| المبدأ | التنفيذ |
|---|---|
| نظام مصادر | كل معلومة تُعرض باسم مصدرها ورابطه ووقت الجلب (`DrugSourceInfo`) |
| موصلات قابلة للإضافة | `DrugKnowledgeProvider` + سجل DI بـ `@IntoSet` — إضافة مصدر جديد = ملف واحد |
| openFDA (اختياري) | `OpenFdaKnowledgeProvider` — مفتاحه العام لا يحتاج مفتاحاً، موثق ومُسمّى المصدر |
| مصادر مستقبلية (وزارة الصحة/هيئة الدواء) | `ServerBackedKnowledgeProvider` ← `POST /druginfo/search` — المفاتيح في سرّات Firebase بالخادم فقط |
| إسناد كل معلومة | بطاقة «المصدر: الاسم • التاريخ • الرابط» تحت كل حقيقة |
| تعارض مصدرين | `DrugKnowledgeAggregator` يكتشف التعارض ويعرضه — **لا يختار نتيجة تلقائياً** (اختيار يدوي بالـ RadioButtons) |
| لا حفظ بدون مراجعة | نتائج البحث لا تلمس قاعدة البيانات المعتمدة إطلاقاً — تُستورد كمسودة في نموذج المراجعة فقط (`PendingDrugDraftStore` في الذاكرة) |
| انقطاع الإنترنت/المهلة/إعادة المحاولة | `FetchPolicy` (مهلة 15ث + محاولتان) + تدهور رشيق: مصدر معطّل = تنبيه ونتيجة جزئية |
| مفاتيح API | في الخادم (`server/index.js` — `AUTHORITY_API_KEY` في Firebase secrets) — لا مفتاح داخل التطبيق |

---

## 🧪 الاختبارات

```bash
# اختبارات الوحدات (منطق المجال + حالات الاستخدام + خط معالجة الصور)
./gradlew testDebugUnitTest

# تقرير التغطية النصي
open app/build/reports/tests/testDebugUnitTest/index.html

# فحص Lint
./gradlew lintDebug
```

**اختبارات خط معالجة الصورة (JVM):**

| الاختبار | يغطي |
|---|---|
| `CropMathTest` | حساب نافذة القص، التكبير/السحب، تقييد النسب |
| `ImageProcessingTest` | قياس الضبابية، السطوع/التباين، زيادة الحدة، تقدير الميل، التصغير |
| `CaptureViewModelTest` | الكاميرا (ملف صحيح/فشل)، المعرض (صورة مقروءة/تالفة)، التنظيف التلقائي |
| `CropEditViewModelTest` | التدوير، القلب، التحسينات، القص عند الحفظ، فشل العرض، تصحيح الميل |
| `PreviewViewModelTest` | **منع الإرسال بدون موافقة**، مقاييس الجودة، حجم الملف، إلغاء الموافقة |
| `TextNormalizerTest` | تطبيع العربي (الهمزات، التشكيل، الأرقام)، التشابه النصي |
| `PrescriptionSegmenterTest` | تقسيم الأسطر، فصل الأدوية المتعددة، تصفية الضجيج |
| `MedicationLineParserTest` | استخراج الاسم/التركيز/الشكل/الجرعة/الطريقة/المدة بالعربية والإنجليزية + **عدم الاختراع** |
| `DrugCatalogMatcherTest` | **الأسماء المتشابهة** (Panadol/Panadol Extra/Panadeine)، التطبيع العربي، عدم الاختراع |
| `GroundingFilterTest` | رفض الحقول غير الموجودة في نص OCR (حارس مضاد للهلوسة) |
| `ConfidenceScorerTest` | تدرّجات الثقة وعتبات «غير مؤكدة» |
| `AnalysisPipelineTest` | **الحالات غير الواضحة** + البدائل + بوابة الصيدلي + التحذيرات الإلزامية |
| `AnalyzePrescriptionUseCaseTest` | التنسيق بين الطبقات، الإخفاقات والبدائل المحلية، الإثراء من openFDA |
| `DrugDatabaseUseCasesTest` | **قاعدة البيانات الدوائية**: الإضافة/التعديل/الحذف، منع التكرار، سجل التعديلات، البحث والتصفية، التمييز بين الموثق واليدوي |
| `DrugKnowledgeServiceTest` | **طبقة البحث الدوائي**: إسناد المصادر، التعارض دون اختيار تلقائي، انقطاع المصدر/المهلة/إعادة المحاولة، غياب Google كمصدر |
| `SearchDrugKnowledgeUseCaseTest` | التحقق من الاستعلام، التقرير الرشيق للانقطاعات، **عدم الحفظ في القاعدة المعتمدة قبل المراجعة** |
| `AuthErrorMapperTest` | **المصادقة**: ترجمة أخطاء Firebase إلى رسائل ودّية + تحليل أدوار `user`/`pharmacist`/`admin` |

**اختبارات آلية (Instrumentation)** يمكن تشغيلها من Android Studio على جهاز/محاكي —
اختبارات الوحدات تعمل بدون أي إعدادات.

---

## 🔐 إعداد Firebase والمصادقة (خطوة بخطوة)

المصادقة حقيقية عبر **Firebase Authentication** (بريد/كلمة مرور + تحقق من البريد + Google + استعادة كلمة المرور + حذف الحساب) مع أدوار `user` / `pharmacist` / `admin`. **لا توجد أسرار أو مفاتيح داخل المستودع** — كل التهيئة تُجلب من Firebase محلياً.

1. **أنشئ مشروعاً** في [Firebase Console](https://console.firebase.google.com) ثم فعّل **Authentication → Sign-in method**: `Email/Password` و `Google`.
2. **الإعداد النموذجي:** انسخ `app/google-services.json.example` إلى `app/google-services.json` (ملف متجاهَل في Git — لا يُرفع أبداً) وضع قيم مشروعك. أو حمّل الملف الحقيقي من Firebase Console → Project settings → Your apps.
3. **Google Sign-In:** أضِف بصمة SHA-1 لحاسوبك (`./gradlew signingReport`) في Firebase Console → Project settings → Android app. معرّف العميل الويب (`default_web_client_id`) يُولَّد تلقائياً من `google-services.json` عند البناء.
4. **تحقّق من البريد وكلمة المرور:** مفعّلان افتراضياً — رسالة التحقق تُرسل تلقائياً بعد التسجيل، ورابط الاستعادة من زر «نسيت كلمة المرور؟».
5. **الأدوار والصلاحيات:**
   - الدور يُخزَّن في **custom claim** اسمه `role` — يضبطه الخادم فقط (`POST /setrole`، للمسؤولين).
   - قواعد Firestore (`firestore.rules`) تُطبّق الدور: `user` يدير بياناته فقط، `pharmacist` يدير قاعدة الأدوية وسجل التعديلات، `admin` يدير كل شيء. النشر: `firebase deploy --only firestore:rules`.
   - **المسؤول الأول:** من Firebase Console → Authentication أو عبر Admin SDK:
     ```js
     admin.auth().setCustomUserClaims(uid, { role: "admin" });
     ```
6. **سرّات الخادم:** `firebase functions:secrets:set GEMINI_API_KEY` و `firebase functions:secrets:set AUTHORITY_API_KEY` — تُخزَّن في Firebase secrets ولا تُكتب في الكود.
7. **بدون Firebase:** يعمل التطبيق بالوضع المحلي (بيانات الجهاز فقط) وتظهر رسالة «Firebase غير مهيأ» في شاشات الدخول.

**حماية الشاشات:** عند تهيئة Firebase، جميع الشاشات الداخلية تتطلب تسجيل دخول — يُعاد التوجيه إلى شاشة الدخول تلقائياً (بوابة `AuthGateViewModel` + قرار `SplashViewModel`).

---

## 🔄 التكامل المستمر (CI)

مستودع `.github/workflows/android-ci.yml` يبني المشروع تلقائياً عند كل دفع (push):

- بناء APK التصحيح ✅
- بناء **AAB** للإصدار النهائي (مع R8/ProGuard) ✅
- تشغيل اختبارات الوحدات ✅
- تشغيل Lint ✅
- رفع APK و AAB والتقارير كـ Artifacts قابلة للتنزيل ✅

---

## 🌐 اللغات والاتجاه

- العربية (RTL) — `app/src/main/res/values-ar/`
- الإنجليزية (LTR) — `app/src/main/res/values/`
- **زر واضح لتغيير اللغة** في الشاشة الرئيسية (عربي ⇄ EN) + شاشة إعدادات اللغة
- التبديل يُطبَّق فوراً مع إعادة إنشاء الواجهة
- `android:supportsRtl="true"` + `localeConfig` للإصدارات الحديثة

---

## 🔐 الأمان — نقاط مهمة

1. **لا مفاتيح في الكود**: مفتاح نموذج الذكاء الاصطناعي يبقى في الخادم (Cloud Function Secret).
2. **تأكيد الصيدلي إلزامي**: نتائج الذكاء الاصطناعي تُعرض بدرجات ثقتها ولا تُعتمد تلقائياً.
3. الصور تُرسل عبر **HTTPS** فقط (التحقق من الرابط في الإعدادات يفرض `https://`).
4. الملفات الحساسة (`local.properties`، `*.jks`، `google-services.json`) مستثناة من Git.

---

## 📜 الحقوق

إعداد وتطوير **د. عبدالماجد الماخذي**

جميع الحقوق محفوظة © 2026
