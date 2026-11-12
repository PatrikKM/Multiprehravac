package com.example.multiprehrava

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "MultiprehravaPrefs"
    private const val KEY_LANGUAGE = "language_code"

    fun init(context: Context) {
        val lang = getLanguage(context)
        setLocale(context, lang)
    }

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    }

    fun saveLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    fun setLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        val appContext = context.applicationContext
        appContext.resources.updateConfiguration(config, appContext.resources.displayMetrics)
    }
}
