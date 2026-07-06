package dev.companionremote.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.companionremote.app.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "cyberremote_settings")

/** Light/dark theme preference. */
enum class ThemeMode { System, Light, Dark }

/** Persists app-level preferences (language, theme, app-icon fetching). */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val languageKey = stringPreferencesKey("language")
    private val themeKey = stringPreferencesKey("theme")
    private val fetchIconsKey = booleanPreferencesKey("fetch_app_icons")

    val language: Flow<AppLanguage> = appContext.settingsDataStore.data.map { prefs ->
        when (prefs[languageKey]) {
            "en" -> AppLanguage.English
            "zh" -> AppLanguage.Chinese
            else -> AppLanguage.System
        }
    }

    val themeMode: Flow<ThemeMode> = appContext.settingsDataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            "light" -> ThemeMode.Light
            "dark" -> ThemeMode.Dark
            else -> ThemeMode.System
        }
    }

    /** Whether to fetch real app icons over the network (default off). */
    val fetchAppIcons: Flow<Boolean> = appContext.settingsDataStore.data.map { prefs ->
        prefs[fetchIconsKey] ?: false
    }

    suspend fun setLanguage(language: AppLanguage) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[languageKey] = when (language) {
                AppLanguage.English -> "en"
                AppLanguage.Chinese -> "zh"
                AppLanguage.System -> "system"
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[themeKey] = when (mode) {
                ThemeMode.Light -> "light"
                ThemeMode.Dark -> "dark"
                ThemeMode.System -> "system"
            }
        }
    }

    suspend fun setFetchAppIcons(enabled: Boolean) {
        appContext.settingsDataStore.edit { prefs -> prefs[fetchIconsKey] = enabled }
    }
}
