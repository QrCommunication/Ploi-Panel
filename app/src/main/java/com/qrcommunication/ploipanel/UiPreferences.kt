package com.qrcommunication.ploipanel

/** Display preferences are device-local and independent of encrypted Ploi tokens. */
internal enum class AppTheme { SYSTEM, LIGHT, DARK }
internal enum class AppLanguage { SYSTEM, FRENCH, ENGLISH }

internal class UiPreferences(private val prefs: ProfilePrefs) {
    companion object {
        const val KEY_THEME = "ui.theme"
        const val KEY_LANGUAGE = "ui.language"
    }

    fun theme(): AppTheme = AppTheme.entries.firstOrNull { it.name == prefs.read(KEY_THEME) } ?: AppTheme.SYSTEM
    fun language(): AppLanguage = AppLanguage.entries.firstOrNull { it.name == prefs.read(KEY_LANGUAGE) } ?: AppLanguage.SYSTEM

    fun setTheme(theme: AppTheme) = prefs.write(KEY_THEME, theme.name)
    fun setLanguage(language: AppLanguage) = prefs.write(KEY_LANGUAGE, language.name)
}
