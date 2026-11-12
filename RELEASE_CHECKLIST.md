# قائمة الإصدار — Google Play (Release Checklist)

## 1) قبل البناء (Pre-build)
- [ ] تحديث `versionCode` / `versionName` في `app/build.gradle.kts`.
- [ ] مراجعة نصوص EN/AR (توازن المفاتيح يُتحقق تلقائياً في CI).
- [ ] لا توجد بيانات اختبار في التطبيق (فحص: لا بذور/حسابات تجريبية — ✅ مُتحقق 2026-11-12).
- [ ] لا ادّعاءات "دقة مطلقة" في أي نص (✅ مُتحقق — والتنصيص في شاشة الموافقة وصفحة المتجر).
- [ ] تحديث `PRIVACY_POLICY.md` إن تغيرت الممارسات + رفعها على رابط عام.

## 2) التوقيع الآمن (Signing)
- [ ] إنشاء مفتاح التوقيع **خارج المستودع نهائياً**:
  ```bash
  keytool -genkeypair -v -keystore ~/keystrokes/pp-release.jks -keyalg RSA \
    -keysize 4096 -validity 10000 -alias pp-release
  ```
- [ ] حفظ بيانات المفتاح في مدير كلمات مرور (ليس في Git إطلاقاً).
- [ ] تزويد Gradle عبر `local.properties` أو متغيرات بيئة فقط:
  ```properties
  RELEASE_STORE_FILE=~/keystrokes/pp-release.jks
  RELEASE_STORE_PASSWORD=***   # من مدير الأسرار
  RELEASE_KEY_ALIAS=pp-release
  RELEASE_KEY_PASSWORD=***
  ```
- [ ] تفعيل **Play App Signing** عند الرفع الأول (Google يحفظ مفتاح التوقيع).
- [ ] أخذ نسخة احتياطية مشفّرة للمفتاح في مكانين آمنين.

## 3) البناء (Build)
- [ ] `./gradlew testDebugUnitTest` — كل الاختبارات خضراء (340+).
- [ ] `./gradlew assembleStaging` — اختبار QA على أجهزة حقيقية.
- [ ] `./gradlew assembleRelease bundleRelease` — APK + AAB بتوقيع.
- [ ] التحقق من R8: `app/build/outputs/mapping/release/mapping.txt` مولّد، والتطبيق يعمل بعد التصغير.
- [ ] اختبار الترقية فوق النسخة المثبتة (لا فقدان بيانات).

## 4) المتجر (Play Console)
- [ ] رفع **AAB** (`app-release.aab`).
- [ ] أيقونة 512 (`play/assets/icon-512.png`) + جرافيك مميز (`play/assets/feature-graphic-1024x500.png`).
- [ ] **لقطات شاشة حقيقية من جهاز** (استبدال `play/screenshots/` التمثيلية عبر `fastlane screengrab` أو تصوير يدوي).
- [ ] نسخ الوصف من `play/listing/PLAY_LISTING.md` (عربي + إنجليزي).
- [ ] رابط سياسة الخصوصية (`PRIVACY_POLICY.md` مستضاف).
- [ ] استبيان تصنيف المحتوى (IARC): بدون إعلانات، أداة مرجعية صحية.
- [ ] إعلان نموذج بيانات السلامة (Data Safety): الصور تُرسَل للتحليل فقط؛ لا مشاركة مع أطراف ثالثة؛ الحذف متاح.
- [ ] منطقة التوزيع + السعر (مجاني).

## 5) الإصدار (Rollout)
- [ ] الإصدار التدريجي (staged rollout 10% → 50% → 100%).
- [ ] مراقبة Android vitals (الأعطال/ANR) أول 48 ساعة.
- [ ] اختبار التحديث من المتجر على جهاز فعلي.

## 6) بعد الإصدار
- [ ] الرد على مراجعات المتجر.
- [ ] تحديث `SECURITY_REPORT.md` مع كل إصدار.
- [ ] أرشفة ملف mapping.txt مع الإصدار (لتفسير الأعطال).
