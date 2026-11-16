package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.HudThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.motoPackDataStore by preferencesDataStore(name = "motopack_prefs")

data class RiderSettings(
    val hudThemeMode: HudThemeMode = HudThemeMode.AUTO,
    val ecoRideMode: Boolean = false,
    val manualOfflineMode: Boolean = false,
    val p2pMeshEnabled: Boolean = false,
    val meshVpnEnabled: Boolean = false,
    val meshRelayHops: Int = 3,
    val separationAlertKm: Double = 2.0,
    val pttAutoPlay: Boolean = true,
    val proactiveWeatherPushEnabled: Boolean = true
)

data class GuestSessionState(
    val firstStartedAtMs: Long = 0L,
    val isActiveSession: Boolean = false
) {
    val hasUsedGuestTrial: Boolean get() = firstStartedAtMs > 0L

    fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (firstStartedAtMs <= 0L) return MotoPackPreferences.GUEST_PREVIEW_DURATION_MS
        val elapsed = (nowMs - firstStartedAtMs).coerceAtLeast(0L)
        return (MotoPackPreferences.GUEST_PREVIEW_DURATION_MS - elapsed).coerceAtLeast(0L)
    }

    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        return firstStartedAtMs > 0L && remainingMs(nowMs) <= 0L
    }

    fun isCurrentlyAllowedInGuest(nowMs: Long = System.currentTimeMillis()): Boolean {
        return isActiveSession && !isExpired(nowMs)
    }
}

class MotoPackPreferences(private val context: Context) {
    private object Keys {
        val HUD_THEME_MODE = stringPreferencesKey("hud_theme_mode")
        val ECO_RIDE_MODE = booleanPreferencesKey("eco_ride_mode")
        val MANUAL_OFFLINE_MODE = booleanPreferencesKey("manual_offline_mode")
        val P2P_MESH_ENABLED = booleanPreferencesKey("p2p_mesh_enabled")
        val MESH_VPN_ENABLED = booleanPreferencesKey("mesh_vpn_enabled")
        val SEPARATION_ALERT_KM = doublePreferencesKey("separation_alert_km")
        val PTT_AUTO_PLAY = booleanPreferencesKey("ptt_auto_play")
        val PROACTIVE_WEATHER_PUSH_ENABLED = booleanPreferencesKey("proactive_weather_push_enabled")
        val GUEST_FIRST_STARTED_AT_MS = androidx.datastore.preferences.core.longPreferencesKey("guest_first_started_at_ms")
        val GUEST_ACTIVE_SESSION = booleanPreferencesKey("guest_active_session")
    }

    val settingsFlow: Flow<RiderSettings> = context.motoPackDataStore.data.map { prefs ->
        val mode = prefs[Keys.HUD_THEME_MODE]?.let {
            runCatching { HudThemeMode.valueOf(it) }.getOrNull()
        } ?: HudThemeMode.AUTO
        RiderSettings(
            hudThemeMode = mode,
            ecoRideMode = prefs[Keys.ECO_RIDE_MODE] ?: false,
            manualOfflineMode = prefs[Keys.MANUAL_OFFLINE_MODE] ?: false,
            p2pMeshEnabled = prefs[Keys.P2P_MESH_ENABLED] ?: false,
            meshVpnEnabled = prefs[Keys.MESH_VPN_ENABLED] ?: false,
            separationAlertKm = prefs[Keys.SEPARATION_ALERT_KM] ?: 2.0,
            pttAutoPlay = prefs[Keys.PTT_AUTO_PLAY] ?: true,
            proactiveWeatherPushEnabled = prefs[Keys.PROACTIVE_WEATHER_PUSH_ENABLED] ?: true
        )
    }

    val guestSessionFlow: Flow<GuestSessionState> = context.motoPackDataStore.data.map { prefs ->
        GuestSessionState(
            firstStartedAtMs = prefs[Keys.GUEST_FIRST_STARTED_AT_MS] ?: 0L,
            isActiveSession = prefs[Keys.GUEST_ACTIVE_SESSION] ?: false
        )
    }

    suspend fun startOrResumeGuestSession(nowMs: Long = System.currentTimeMillis()): Boolean {
        var allowed = false
        context.motoPackDataStore.edit { prefs ->
            val firstStart = prefs[Keys.GUEST_FIRST_STARTED_AT_MS] ?: 0L
            if (firstStart <= 0L) {
                prefs[Keys.GUEST_FIRST_STARTED_AT_MS] = nowMs
                prefs[Keys.GUEST_ACTIVE_SESSION] = true
                allowed = true
            } else {
                val elapsed = (nowMs - firstStart).coerceAtLeast(0L)
                if (elapsed < GUEST_PREVIEW_DURATION_MS) {
                    prefs[Keys.GUEST_ACTIVE_SESSION] = true
                    allowed = true
                } else {
                    prefs[Keys.GUEST_ACTIVE_SESSION] = false
                    allowed = false
                }
            }
        }
        return allowed
    }

    suspend fun exitGuestPreviewToSignIn() {
        context.motoPackDataStore.edit { prefs ->
            prefs[Keys.GUEST_ACTIVE_SESSION] = false
        }
    }

    suspend fun setHudThemeMode(mode: HudThemeMode) {
        context.motoPackDataStore.edit { it[Keys.HUD_THEME_MODE] = mode.name }
    }

    suspend fun setEcoRideMode(enabled: Boolean) {
        context.motoPackDataStore.edit { it[Keys.ECO_RIDE_MODE] = enabled }
    }

    suspend fun setManualOfflineMode(enabled: Boolean) {
        context.motoPackDataStore.edit { it[Keys.MANUAL_OFFLINE_MODE] = enabled }
    }

    suspend fun setP2pMeshEnabled(enabled: Boolean) {
        context.motoPackDataStore.edit { it[Keys.P2P_MESH_ENABLED] = enabled }
    }

    suspend fun setMeshVpnEnabled(enabled: Boolean) {
        context.motoPackDataStore.edit { it[Keys.MESH_VPN_ENABLED] = enabled }
    }

    suspend fun setSeparationAlertKm(km: Double) {
        context.motoPackDataStore.edit { it[Keys.SEPARATION_ALERT_KM] = km.coerceIn(0.5, 25.0) }
    }

    suspend fun setPttAutoPlay(enabled: Boolean) {
        context.motoPackDataStore.edit { it[Keys.PTT_AUTO_PLAY] = enabled }
    }

    suspend fun setProactiveWeatherPushEnabled(enabled: Boolean) {
        context.motoPackDataStore.edit { it[Keys.PROACTIVE_WEATHER_PUSH_ENABLED] = enabled }
    }

    companion object {
        const val GUEST_PREVIEW_DURATION_MS: Long = 30L * 60L * 1000L
    }
}
