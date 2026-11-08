package id.smartkids.screenguard.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import id.smartkids.screenguard.core.GuardSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "guard_settings_prefs")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SLEEP_ENABLED = booleanPreferencesKey("sleep_enabled")
        val DISTANCE_ENABLED = booleanPreferencesKey("distance_enabled")
        val POSTURE_ENABLED = booleanPreferencesKey("posture_enabled")
        val EYE_REST_ENABLED = booleanPreferencesKey("eye_rest_enabled")
        val MIN_DISTANCE_CM = floatPreferencesKey("min_distance_cm")
        val FOCAL_CALIBRATION = floatPreferencesKey("focal_calibration")
        val SLEEP_SENSITIVITY_SEC = intPreferencesKey("sleep_sensitivity_sec")
        val AI_SENSITIVITY = floatPreferencesKey("ai_sensitivity")
        val EYE_REST_INTERVAL_MIN = intPreferencesKey("eye_rest_interval_min")
        val EYE_REST_DURATION_SEC = intPreferencesKey("eye_rest_duration_sec")
        val DAILY_LIMIT_MIN = intPreferencesKey("daily_limit_min")
        val BEDTIME_HOUR = intPreferencesKey("bedtime_hour")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val MAX_PROTECTION_LEVEL = intPreferencesKey("max_protection_level")
    }

    val settingsFlow: Flow<GuardSettings> = context.dataStore.data.map { prefs ->
        GuardSettings(
            sleepDetectionEnabled = prefs[Keys.SLEEP_ENABLED] ?: true,
            distanceGuardEnabled = prefs[Keys.DISTANCE_ENABLED] ?: true,
            postureGuardEnabled = prefs[Keys.POSTURE_ENABLED] ?: true,
            eyeRestEnabled = prefs[Keys.EYE_REST_ENABLED] ?: true,
            minDistanceCm = prefs[Keys.MIN_DISTANCE_CM] ?: 30f,
            cameraFocalCalibration = prefs[Keys.FOCAL_CALIBRATION] ?: 620f,
            sleepSensitivitySec = prefs[Keys.SLEEP_SENSITIVITY_SEC] ?: 6,
            aiSensitivityMultiplier = prefs[Keys.AI_SENSITIVITY] ?: 1.0f,
            eyeRestIntervalMinutes = prefs[Keys.EYE_REST_INTERVAL_MIN] ?: 20,
            eyeRestDurationSeconds = prefs[Keys.EYE_REST_DURATION_SEC] ?: 20,
            dailyLimitMinutes = prefs[Keys.DAILY_LIMIT_MIN] ?: 120,
            bedtimeHour = prefs[Keys.BEDTIME_HOUR] ?: 21,
            soundVolume = prefs[Keys.SOUND_VOLUME] ?: 0.8f,
            maxProtectionLevel = prefs[Keys.MAX_PROTECTION_LEVEL] ?: 3
        )
    }

    suspend fun updateSettings(transform: (GuardSettings) -> GuardSettings) {
        context.dataStore.edit { prefs ->
            val current = GuardSettings(
                sleepDetectionEnabled = prefs[Keys.SLEEP_ENABLED] ?: true,
                distanceGuardEnabled = prefs[Keys.DISTANCE_ENABLED] ?: true,
                postureGuardEnabled = prefs[Keys.POSTURE_ENABLED] ?: true,
                eyeRestEnabled = prefs[Keys.EYE_REST_ENABLED] ?: true,
                minDistanceCm = prefs[Keys.MIN_DISTANCE_CM] ?: 30f,
                cameraFocalCalibration = prefs[Keys.FOCAL_CALIBRATION] ?: 620f,
                sleepSensitivitySec = prefs[Keys.SLEEP_SENSITIVITY_SEC] ?: 6,
                aiSensitivityMultiplier = prefs[Keys.AI_SENSITIVITY] ?: 1.0f,
                eyeRestIntervalMinutes = prefs[Keys.EYE_REST_INTERVAL_MIN] ?: 20,
                eyeRestDurationSeconds = prefs[Keys.EYE_REST_DURATION_SEC] ?: 20,
                dailyLimitMinutes = prefs[Keys.DAILY_LIMIT_MIN] ?: 120,
                bedtimeHour = prefs[Keys.BEDTIME_HOUR] ?: 21,
                soundVolume = prefs[Keys.SOUND_VOLUME] ?: 0.8f,
                maxProtectionLevel = prefs[Keys.MAX_PROTECTION_LEVEL] ?: 3
            )
            val updated = transform(current)
            prefs[Keys.SLEEP_ENABLED] = updated.sleepDetectionEnabled
            prefs[Keys.DISTANCE_ENABLED] = updated.distanceGuardEnabled
            prefs[Keys.POSTURE_ENABLED] = updated.postureGuardEnabled
            prefs[Keys.EYE_REST_ENABLED] = updated.eyeRestEnabled
            prefs[Keys.MIN_DISTANCE_CM] = updated.minDistanceCm
            prefs[Keys.FOCAL_CALIBRATION] = updated.cameraFocalCalibration
            prefs[Keys.SLEEP_SENSITIVITY_SEC] = updated.sleepSensitivitySec
            prefs[Keys.AI_SENSITIVITY] = updated.aiSensitivityMultiplier
            prefs[Keys.EYE_REST_INTERVAL_MIN] = updated.eyeRestIntervalMinutes
            prefs[Keys.EYE_REST_DURATION_SEC] = updated.eyeRestDurationSeconds
            prefs[Keys.DAILY_LIMIT_MIN] = updated.dailyLimitMinutes
            prefs[Keys.BEDTIME_HOUR] = updated.bedtimeHour
            prefs[Keys.SOUND_VOLUME] = updated.soundVolume
            prefs[Keys.MAX_PROTECTION_LEVEL] = updated.maxProtectionLevel
        }
    }
}
