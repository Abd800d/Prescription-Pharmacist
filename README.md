# 🧾 صيدلي الوصفة — Prescription Pharmacist

> **إعداد وتطوير د. عبدالماجد الماخذي** — *Developed by Dr. Abdulmajid Al-Maakthi*

تطبيق Android إنتاجي يساعد الصيدلي على **تصوير وصفة طبية** أو تحميلها من معرض الهاتف، ثم **تحليلها بالذكاء الاصطناعي** لاستخراج أسماء الأدوية (تجارية أو علمية) المكتوبة بخط اليد، مع إظهار **درجة الثقة** لكل نتيجة وطلب **تأكيد الصيدلي** قبل اعتمادها وحفظها.

<p align="center">
  <em>Kotlin • Jetpack Compose • MVVM • Clean Architecture • Material 3 • عربي RTL / English LTR</em>
</p>

---

## ✨ المزايا

| الميزة | الوصف |
|---|---|
| 📸 تصوير الوصفة | كاميرا CameraX مباشرة أو اختيار صورة من المعرض |
| 🤖 تحليل بالذكاء الاصطناعي | إرسال الصورة إلى خادم آمن يستخدم نموذج رؤية (Vision LLM) لاستخراج الأدوية المكتوبة بخط اليد بالعربية والإنجليزية |
| 📊 درجات الثقة | كل دواء مستخرج يظهر مع مستوى الثقة (عالية / متوسطة / منخفضة) ونسبة مئوية |
| ✅ تأكيد الصيدلي | النتيجة **لا تُعتمد** إلا بعد مراجعة الصيدلي وتأكيده — يمكن التعديل والإضافة والحذف قبل الاعتماد |
| 🗂️ سجل الوصفات | حفظ محلي (Room) مع الحالات (معتمدة / بانتظار التأكيد / مرفوضة) وإمكانية التعديل لاحقاً |
| 🔐 المصادقة والنسخ الاحتياطي | Firebase Authentication + Firebase Storage (اختياريان) — يعمل التطبيق بالكامل بدونهما |
| 🌍 عربي / إنجليزي | دعم كامل للعربية RTL والإنجليزية LTR مع تبديل فوري من الإعدادات |
| 🌙 الوضع الليلي | فاتح / داكن / تلقائي + ألوان ديناميكية على Android 12+ |
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

## 🧪 الاختبارات

```bash
# اختبارات الوحدات (منطق المجال + حالات الاستخدام)
./gradlew testDebugUnitTest

# تقرير التغطية النصي
open app/build/reports/tests/testDebugUnitTest/index.html

# فحص Lint
./gradlew lintDebug
```

**اختبارات آلية (Instrumentation)** يمكن تشغيلها من Android Studio على جهاز/محاكي —
اختبارات الوحدات تعمل بدون أي إعدادات.

---

## 🔄 التكامل المستمر (CI)

مستودع `.github/workflows/android-ci.yml` يبني المشروع تلقائياً عند كل دفع (push):

- بناء APK التصحيح ✅
- تشغيل اختبارات الوحدات ✅
- تشغيل Lint ✅
- رفع APK والتقارير كـ Artifacts ✅

---

## 🌐 اللغات والاتجاه

- العربية (RTL) — `app/src/main/res/values-ar/`
- الإنجليزية (LTR) — `app/src/main/res/values/`
- التبديل من **الإعدادات ← اللغة** (تُطبَّق فوراً مع إعادة إنشاء الواجهة)
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
