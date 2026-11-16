package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pulseTalkDataStore by preferencesDataStore(name = "pulsetalk_prefs")

data class LocalAppPreferences(
    val darkMode: String = "system", // "system", "dark", "light"
    val screenshotGuard: Boolean = false,
    val compactChatList: Boolean = false,
    val enterToSend: Boolean = true,
    val autoBackupFrequency: String = "Daily",
    val aiReplyTone: String = "Balanced",
    val wallpaperEnabled: Boolean = true,
    val wallpaperPreset: String = "Constellation", // "Constellation", "Emerald Mist", "Midnight Ink", "Minimal"
    val fontSizeScale: String = "Medium", // "Small", "Medium", "Large"
    val hapticFeedback: Boolean = true,
    val highContrastCipherBadges: Boolean = false,
    val callNoiseSuppression: Boolean = true,
    val chatLockPinHash: String = "",
    val draftsMap: Map<String, String> = emptyMap(),
    val quickReplyTemplates: List<String> = listOf(
        "On my way!",
        "In a meeting, I'll call you right back.",
        "Sounds great, approved!",
        "Please share the encrypted document."
    )
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val SCREENSHOT_GUARD = booleanPreferencesKey("screenshot_guard")
        val COMPACT_CHAT_LIST = booleanPreferencesKey("compact_chat_list")
        val ENTER_TO_SEND = booleanPreferencesKey("enter_to_send")
        val AUTO_BACKUP_FREQ = stringPreferencesKey("auto_backup_freq")
        val AI_REPLY_TONE = stringPreferencesKey("ai_reply_tone")
        val WALLPAPER_ENABLED = booleanPreferencesKey("wallpaper_enabled")
        val WALLPAPER_PRESET = stringPreferencesKey("wallpaper_preset")
        val FONT_SIZE_SCALE = stringPreferencesKey("font_size_scale")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val HIGH_CONTRAST_BADGES = booleanPreferencesKey("high_contrast_badges")
        val CALL_NOISE_SUPPRESSION = booleanPreferencesKey("call_noise_suppression")
        val CHAT_LOCK_PIN_HASH = stringPreferencesKey("chat_lock_pin_hash")
        val DRAFTS_JSON = stringPreferencesKey("drafts_json")
        val QUICK_REPLIES = stringPreferencesKey("quick_replies_pipe")
    }

    val preferencesFlow: Flow<LocalAppPreferences> = context.pulseTalkDataStore.data.map { prefs ->
        val rawDrafts = prefs[Keys.DRAFTS_JSON].orEmpty()
        val parsedDrafts = parseDraftsJson(rawDrafts)
        val rawQuick = prefs[Keys.QUICK_REPLIES].orEmpty()
        val quickList = if (rawQuick.isBlank()) {
            LocalAppPreferences().quickReplyTemplates
        } else {
            rawQuick.split("||").filter { it.isNotBlank() }
        }
        LocalAppPreferences(
            darkMode = prefs[Keys.DARK_MODE] ?: "system",
            screenshotGuard = prefs[Keys.SCREENSHOT_GUARD] ?: false,
            compactChatList = prefs[Keys.COMPACT_CHAT_LIST] ?: false,
            enterToSend = prefs[Keys.ENTER_TO_SEND] ?: true,
            autoBackupFrequency = prefs[Keys.AUTO_BACKUP_FREQ] ?: "Daily",
            aiReplyTone = prefs[Keys.AI_REPLY_TONE] ?: "Balanced",
            wallpaperEnabled = prefs[Keys.WALLPAPER_ENABLED] ?: true,
            wallpaperPreset = prefs[Keys.WALLPAPER_PRESET] ?: "Constellation",
            fontSizeScale = prefs[Keys.FONT_SIZE_SCALE] ?: "Medium",
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            highContrastCipherBadges = prefs[Keys.HIGH_CONTRAST_BADGES] ?: false,
            callNoiseSuppression = prefs[Keys.CALL_NOISE_SUPPRESSION] ?: true,
            chatLockPinHash = prefs[Keys.CHAT_LOCK_PIN_HASH] ?: "",
            draftsMap = parsedDrafts,
            quickReplyTemplates = quickList
        )
    }

    suspend fun updateDarkMode(mode: String) {
        context.pulseTalkDataStore.edit { it[Keys.DARK_MODE] = mode }
    }

    suspend fun setScreenshotGuard(enabled: Boolean) {
        context.pulseTalkDataStore.edit { it[Keys.SCREENSHOT_GUARD] = enabled }
    }

    suspend fun setCompactChatList(enabled: Boolean) {
        context.pulseTalkDataStore.edit { it[Keys.COMPACT_CHAT_LIST] = enabled }
    }

    suspend fun setEnterToSend(enabled: Boolean) {
        context.pulseTalkDataStore.edit { it[Keys.ENTER_TO_SEND] = enabled }
    }

    suspend fun setAutoBackupFrequency(freq: String) {
        context.pulseTalkDataStore.edit { it[Keys.AUTO_BACKUP_FREQ] = freq }
    }

    suspend fun setAiReplyTone(tone: String) {
        context.pulseTalkDataStore.edit { it[Keys.AI_REPLY_TONE] = tone }
    }

    suspend fun setWallpaperEnabled(enabled: Boolean) {
        context.pulseTalkDataStore.edit { it[Keys.WALLPAPER_ENABLED] = enabled }
    }

    suspend fun setWallpaperPreset(preset: String) {
        context.pulseTalkDataStore.edit { it[Keys.WALLPAPER_PRESET] = preset }
    }

    suspend fun setFontSizeScale(scale: String) {
        context.pulseTalkDataStore.edit { it[Keys.FONT_SIZE_SCALE] = scale }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.pulseTalkDataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setHighContrastBadges(enabled: Boolean) {
        context.pulseTalkDataStore.edit { it[Keys.HIGH_CONTRAST_BADGES] = enabled }
    }

    suspend fun setCallNoiseSuppression(enabled: Boolean) {
        context.pulseTalkDataStore.edit { it[Keys.CALL_NOISE_SUPPRESSION] = enabled }
    }

    suspend fun setChatLockPin(rawPin: String) {
        val hash = if (rawPin.isBlank()) "" else E2eeCryptoManager.sha256Hex("PulseLock:$rawPin")
        context.pulseTalkDataStore.edit { it[Keys.CHAT_LOCK_PIN_HASH] = hash }
    }

    suspend fun saveChatDraft(chatId: String, draft: String) {
        context.pulseTalkDataStore.edit { prefs ->
            val map = parseDraftsJson(prefs[Keys.DRAFTS_JSON].orEmpty()).toMutableMap()
            if (draft.isBlank()) {
                map.remove(chatId)
            } else {
                map[chatId] = draft.take(500)
            }
            val json = org.json.JSONObject()
            map.forEach { (k, v) -> json.put(k, v) }
            prefs[Keys.DRAFTS_JSON] = json.toString()
        }
    }

    suspend fun addQuickReplyTemplate(template: String) {
        val clean = template.trim().take(120)
        if (clean.isBlank()) return
        context.pulseTalkDataStore.edit { prefs ->
            val current = prefs[Keys.QUICK_REPLIES].orEmpty()
                .split("||")
                .filter { it.isNotBlank() }
                .ifEmpty { LocalAppPreferences().quickReplyTemplates }
            val updated = (listOf(clean) + current).distinct().take(10)
            prefs[Keys.QUICK_REPLIES] = updated.joinToString("||")
        }
    }

    private fun parseDraftsJson(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val v = obj.optString(key)
                    if (v.isNotBlank()) put(key, v)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
