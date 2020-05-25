package q19.kenes_widget.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.*

/**
 * Created by Akexorcist on 10/19/2017 AD.
 */
internal object LocalizationUtility {

    fun applyLocalizationContext(baseContext: Context): Context {
        val baseLocale = getLocaleFromConfiguration(baseContext.resources.configuration)
        val currentLocale = LanguageSetting.getLanguageWithDefault(baseContext, LanguageSetting.getDefaultLanguage(baseContext))
        if (!baseLocale.toString().equals(currentLocale.toString(), ignoreCase = true)) {
            val context = LocalizationContext(baseContext)
            val config = context.resources.configuration
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    config.setLocale(currentLocale)
                    val localeList = LocaleList(currentLocale)
                    LocaleList.setDefault(localeList)
                    config.setLocales(localeList)
                    context.createConfigurationContext(config)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                    config.setLocale(currentLocale)
                    context.createConfigurationContext(config)
                }
                else -> {
                    config.locale = currentLocale
                    context.resources.updateConfiguration(config, context.resources.displayMetrics)
                    context
                }
            }
        } else {
            return baseContext
        }
    }

    fun getLocaleFromConfiguration(configuration: Configuration): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            configuration.locales.get(0)
        } else {
            configuration.locale
        }
    }

}