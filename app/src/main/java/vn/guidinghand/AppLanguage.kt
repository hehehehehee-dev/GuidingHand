package vn.guidinghand

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguage {
    fun context(base: Context): Context {
        val language = FamilyPreferences(base).language
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return base.createConfigurationContext(config)
    }
    fun apply(context: Context, language: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales.toLanguageTags() != language)
                manager.applicationLocales = LocaleList.forLanguageTags(language)
        }
    }
}
