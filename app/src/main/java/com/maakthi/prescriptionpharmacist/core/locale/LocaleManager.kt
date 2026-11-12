package com.maakthi.prescriptionpharmacist.core.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.text.TextUtils
import android.view.View
import com.maakthi.prescriptionpharmacist.domain.model.AppLanguage
import java.util.Locale

/**
 * Applies the user-selected app language (Arabic / English / system) on all
 * API levels. The selection is persisted in SharedPreferences so it can be
 * restored synchronously from [android.app.Activity.attachBaseContext].
 */
object LocaleManager {

    private const val PREFS = "app_locale_prefs"
    private const val KEY_LANGUAGE = "app_language"

    private fun persistedLanguage(context: Context): AppLanguage {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name)
        return runCatching { AppLanguage.valueOf(raw ?: AppLanguage.SYSTEM.name) }
            .getOrDefault(AppLanguage.SYSTEM)
    }

    private fun localeFor(language: AppLanguage): Locale? = when (language) {
        AppLanguage.SYSTEM -> null
        AppLanguage.ARABIC -> Locale("ar")
        AppLanguage.ENGLISH -> Locale.ENGLISH
    }

    /** Wraps the base context with the persisted locale. */
    fun wrap(base: Context): Context {
        val locale = localeFor(persistedLanguage(base)) ?: return base
        val config = Configuration(base.resources.configuration)
        setLocaleOnConfig(config, locale)
        return base.createConfigurationContext(config)
    }

    /** Localizes a configuration used by [android.app.Activity.applyOverrideConfiguration]. */
    fun localizeConfiguration(base: Context, override: Configuration?): Configuration? {
        val locale = localeFor(persistedLanguage(base)) ?: return override
        val config = override ?: Configuration(base.resources.configuration)
        setLocaleOnConfig(config, locale)
        return config
    }

    /** Persists the choice and notifies the system (per-app locales on Android 13+). */
    fun persistAndApply(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language.name).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val systemLocaleManager =
                context.getSystemService(LocaleManager::class.java)
            val locale = localeFor(language)
            systemLocaleManager?.applicationLocales =
                if (locale == null) LocaleList.getEmptyLocaleList()
                else LocaleList(locale)
        }
    }

    /** True when the effective locale for [language] is right-to-left. */
    fun isRtl(language: AppLanguage, context: Context): Boolean {
        val locale = localeFor(language) ?: Locale.getDefault()
        return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
    }

    private fun setLocaleOnConfig(config: Configuration, locale: Locale) {
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
    }
}
