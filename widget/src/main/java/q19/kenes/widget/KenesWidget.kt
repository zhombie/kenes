package q19.kenes.widget

import android.content.Context
import android.content.Intent
import kz.q19.common.locale.core.LanguageSetting
import kz.q19.domain.model.language.Language.Companion.KAZAKH
import kz.q19.domain.model.language.Language.Companion.RUSSIAN
import q19.kenes.widget.ui.presentation.KenesWidgetActivity

object KenesWidget {

    class Builder constructor(private var hostname: String) {

        enum class Language {
            KK,
            RU
        }

        data class User constructor(
            val firstName: String? = null,
            val lastName: String? = null,
            val phoneNumber: String? = null
        )

        private var language: Language? = null
        private var user: User? = null

        fun getHostname(): String {
            return hostname
        }

        fun setHostname(hostname: String): Builder {
            this.hostname = hostname
            return this
        }

        fun getLanguage(): Language? {
            return language
        }

        fun setLanguage(language: Language): Builder {
            this.language = language
            return this
        }

        fun getUser(): User? {
            return user
        }

        fun setUser(user: User): Builder {
            this.user = user
            return this
        }

        fun build(context: Context): Intent {
            val language = when (language) {
                Language.KK -> KAZAKH
                Language.RU -> RUSSIAN
                else -> null
            }
            if (language?.locale != null) {
                LanguageSetting.setLanguage(context, language.locale)
            }
            return KenesWidgetActivity.newIntent(
                context,
                hostname = hostname,
                language = language,
                firstName = user?.firstName,
                lastName = user?.lastName,
                phoneNumber = user?.phoneNumber
            )
        }
    }

}