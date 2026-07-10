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

/** Visual skin (accent + glass palette). Orthogonal to light/dark. */
enum class AppSkin { Midnight, Graphite, Aurora, Sunset }

/** Haptic (vibration) strength for button feedback. */
enum class HapticStrength { Light, Medium, Strong }

/** Persists app-level preferences (language, theme, skin, haptics, …). */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val languageKey = stringPreferencesKey("language")
    private val themeKey = stringPreferencesKey("theme")
    private val skinKey = stringPreferencesKey("skin")
    private val fetchIconsKey = booleanPreferencesKey("fetch_app_icons")
    private val hapticEnabledKey = booleanPreferencesKey("haptic_enabled")
    private val hapticStrengthKey = stringPreferencesKey("haptic_strength")
    private val introSeenKey = booleanPreferencesKey("intro_seen")
    private val autoCheckUpdatesKey = booleanPreferencesKey("auto_check_updates")
    private val autoDownloadUpdatesKey = booleanPreferencesKey("auto_download_updates")

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

    val skin: Flow<AppSkin> = appContext.settingsDataStore.data.map { prefs ->
        when (prefs[skinKey]) {
            "graphite" -> AppSkin.Graphite
            "aurora" -> AppSkin.Aurora
            "sunset" -> AppSkin.Sunset
            else -> AppSkin.Midnight
        }
    }

    /** Whether to fetch real app icons over the network (default off). */
    val fetchAppIcons: Flow<Boolean> = appContext.settingsDataStore.data.map { prefs ->
        prefs[fetchIconsKey] ?: false
    }

    /** Vibrate on button presses (default on). */
    val hapticEnabled: Flow<Boolean> = appContext.settingsDataStore.data.map { prefs ->
        prefs[hapticEnabledKey] ?: true
    }

    val hapticStrength: Flow<HapticStrength> = appContext.settingsDataStore.data.map { prefs ->
        when (prefs[hapticStrengthKey]) {
            "light" -> HapticStrength.Light
            "strong" -> HapticStrength.Strong
            else -> HapticStrength.Medium
        }
    }

    /** Whether the first-run remote tutorial has been shown. */
    val introSeen: Flow<Boolean> = appContext.settingsDataStore.data.map { prefs ->
        prefs[introSeenKey] ?: false
    }

    /** Check GitHub for a newer APK on launch (default on). */
    val autoCheckUpdates: Flow<Boolean> = appContext.settingsDataStore.data.map { prefs ->
        prefs[autoCheckUpdatesKey] ?: true
    }

    /** Download an available update automatically (default off — save data). */
    val autoDownloadUpdates: Flow<Boolean> = appContext.settingsDataStore.data.map { prefs ->
        prefs[autoDownloadUpdatesKey] ?: false
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

    suspend fun setSkin(skin: AppSkin) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[skinKey] = when (skin) {
                AppSkin.Midnight -> "midnight"
                AppSkin.Graphite -> "graphite"
                AppSkin.Aurora -> "aurora"
                AppSkin.Sunset -> "sunset"
            }
        }
    }

    suspend fun setFetchAppIcons(enabled: Boolean) {
        appContext.settingsDataStore.edit { prefs -> prefs[fetchIconsKey] = enabled }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        appContext.settingsDataStore.edit { prefs -> prefs[hapticEnabledKey] = enabled }
    }

    suspend fun setHapticStrength(strength: HapticStrength) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[hapticStrengthKey] = when (strength) {
                HapticStrength.Light -> "light"
                HapticStrength.Medium -> "medium"
                HapticStrength.Strong -> "strong"
            }
        }
    }

    suspend fun setIntroSeen(seen: Boolean) {
        appContext.settingsDataStore.edit { prefs -> prefs[introSeenKey] = seen }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        appContext.settingsDataStore.edit { prefs -> prefs[autoCheckUpdatesKey] = enabled }
    }

    suspend fun setAutoDownloadUpdates(enabled: Boolean) {
        appContext.settingsDataStore.edit { prefs -> prefs[autoDownloadUpdatesKey] = enabled }
    }
}
