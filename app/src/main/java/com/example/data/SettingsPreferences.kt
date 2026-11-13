package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.OfflineInpaintQuality
import com.example.engine.SaveFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lokafoto_settings"
)

data class StudioSettings(
    val offlineModePreferred: Boolean = true,
    val saveFormat: SaveFormat = SaveFormat.PNG_LOSSLESS,
    val inpaintQuality: OfflineInpaintQuality = OfflineInpaintQuality.DETAILED_TEXTURE,
    val autoRecoveryEnabled: Boolean = true
)

class SettingsPreferences(private val context: Context) {

    private object Keys {
        val OFFLINE_MODE_PREFERRED = booleanPreferencesKey("offline_mode_preferred")
        val SAVE_FORMAT = stringPreferencesKey("save_format")
        val INPAINT_QUALITY = stringPreferencesKey("inpaint_quality")
        val AUTO_RECOVERY_ENABLED = booleanPreferencesKey("auto_recovery_enabled")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        val formatName = prefs[Keys.SAVE_FORMAT] ?: SaveFormat.PNG_LOSSLESS.name
        val qualityName = prefs[Keys.INPAINT_QUALITY] ?: OfflineInpaintQuality.DETAILED_TEXTURE.name
        StudioSettings(
            offlineModePreferred = prefs[Keys.OFFLINE_MODE_PREFERRED] ?: true,
            saveFormat = SaveFormat.entries.firstOrNull { it.name == formatName }
                ?: SaveFormat.PNG_LOSSLESS,
            inpaintQuality = OfflineInpaintQuality.entries.firstOrNull { it.name == qualityName }
                ?: OfflineInpaintQuality.DETAILED_TEXTURE,
            autoRecoveryEnabled = prefs[Keys.AUTO_RECOVERY_ENABLED] ?: true
        )
    }

    suspend fun setOfflineModePreferred(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.OFFLINE_MODE_PREFERRED] = enabled }
    }

    suspend fun setSaveFormat(format: SaveFormat) {
        context.studioDataStore.edit { it[Keys.SAVE_FORMAT] = format.name }
    }

    suspend fun setInpaintQuality(quality: OfflineInpaintQuality) {
        context.studioDataStore.edit { it[Keys.INPAINT_QUALITY] = quality.name }
    }

    suspend fun setAutoRecoveryEnabled(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.AUTO_RECOVERY_ENABLED] = enabled }
    }
}
