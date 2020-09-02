package q19.kenes_widget.data.model

import java.util.*

class Language(val key: String, val value: String) {

    companion object {
        private const val KEY_KAZAKH = "kk"
        private const val KEY_RUSSIAN = "ru"
        private const val KEY_ENGLISH = "en"

        val DEFAULT: Language
            get() = by(Locale.getDefault().language)

        val KAZAKH: Language
            get() = Language(KEY_KAZAKH, "Қаз")

        val RUSSIAN: Language
            get() = Language(KEY_RUSSIAN, "Рус")

        val ENGLISH: Language
            get() = Language(KEY_ENGLISH, "Eng")

        fun getSupportedLanguages(): Array<Language> {
            return arrayOf(KAZAKH, RUSSIAN)
        }

        fun from(locale: Locale): Language {
            return by(locale.language)
        }

        fun by(language: String): Language {
            return when (language) {
                KEY_KAZAKH -> KAZAKH
                KEY_RUSSIAN -> RUSSIAN
//                KEY_ENGLISH -> ENGLISH
                else -> RUSSIAN
            }
        }
    }

    val locale: Locale
        get() = Locale(key)

    override fun toString(): String {
        return key
    }

}