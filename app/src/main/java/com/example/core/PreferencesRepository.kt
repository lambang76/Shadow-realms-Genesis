package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nocturne_prefs")

data class UserSettings(
    val language: AppLanguage = AppLanguage.INDONESIAN,
    val bgmVolume: Float = 0.65f,
    val sfxVolume: Float = 0.8f,
    val floatingJoystick: Boolean = true
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val BGM_VOLUME = floatPreferencesKey("bgm_volume")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val FLOATING_STICK = booleanPreferencesKey("floating_stick")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.INDONESIAN.code
        val lang = if (langCode == AppLanguage.ENGLISH.code) AppLanguage.ENGLISH else AppLanguage.INDONESIAN
        UserSettings(
            language = lang,
            bgmVolume = prefs[Keys.BGM_VOLUME] ?: 0.65f,
            sfxVolume = prefs[Keys.SFX_VOLUME] ?: 0.8f,
            floatingJoystick = prefs[Keys.FLOATING_STICK] ?: true
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setBgmVolume(volume: Float) {
        context.dataStore.edit { it[Keys.BGM_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setSfxVolume(volume: Float) {
        context.dataStore.edit { it[Keys.SFX_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setFloatingJoystick(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FLOATING_STICK] = enabled }
    }
}
