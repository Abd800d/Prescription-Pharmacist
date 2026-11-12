# ---- Prescription Pharmacist: R8 / ProGuard rules ----

# Keep serializers used by kotlinx.serialization + Retrofit
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.maakthi.prescriptionpharmacist.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep application & models for debugging crash reports
-keep class com.maakthi.prescriptionpharmacist.domain.model.** { *; }
-keep class com.maakthi.prescriptionpharmacist.data.remote.dto.** { *; }

# ---- Production hardening ----
# OkHttp / Ktor transport
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# Firebase (consumer rules exist; silence optional deps)
-dontwarn com.google.firebase.**

# Coroutines internals
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Enums used via valueOf at runtime (audit, sync, media kinds)
-keepclassmembers enum com.maakthi.prescriptionpharmacist.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
