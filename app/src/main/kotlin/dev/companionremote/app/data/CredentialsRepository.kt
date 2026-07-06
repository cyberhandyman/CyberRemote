package dev.companionremote.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "companion_remote")

/**
 * Stores pyatv-format credential strings per device name, wrapped with the
 * Android Keystore (see [KeystoreCrypto]).
 */
class CredentialsRepository(context: Context) {

    private val appContext = context.applicationContext

    private fun credentialsKey(deviceName: String) = stringPreferencesKey("creds_$deviceName")

    suspend fun load(deviceName: String): String? {
        val preferences = appContext.dataStore.data.first()
        val wrapped = preferences[credentialsKey(deviceName)] ?: return null
        return KeystoreCrypto.decrypt(wrapped)
    }

    suspend fun save(deviceName: String, credentials: String) {
        val wrapped = KeystoreCrypto.encrypt(credentials)
        appContext.dataStore.edit { it[credentialsKey(deviceName)] = wrapped }
    }

    suspend fun delete(deviceName: String) {
        appContext.dataStore.edit { it.remove(credentialsKey(deviceName)) }
    }

    suspend fun pairedDeviceNames(): List<String> {
        val preferences = appContext.dataStore.data.first()
        return preferences.asMap().keys
            .map { it.name }
            .filter { it.startsWith("creds_") }
            .map { it.removePrefix("creds_") }
    }
}
