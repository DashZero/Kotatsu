package org.koitharu.kotatsu.comments

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.UUID

object CommentsSettings {

    private const val PREFS_NAME = "comments_prefs"
    private lateinit var sharedPreferences: SharedPreferences

    private val _isEnabled = mutableStateOf(true)
    val isEnabled: State<Boolean> = _isEnabled

    private val _displayName = mutableStateOf("Anonymous")
    val displayName: State<String> = _displayName

    private val _avatarUrl = mutableStateOf<String?>(null)
    val avatarUrl: State<String?> = _avatarUrl

    private val _enableModeration = mutableStateOf(true)
    val enableModeration: State<Boolean> = _enableModeration

    private val _relayUrls = mutableStateOf(listOf("wss://gun-manhattan.herokuapp.com/gun"))
    val relayUrls: State<List<String>> = _relayUrls

    private val _userId = mutableStateOf<String?>(null)
    val userId: State<String?> = _userId

    private val _bannedUntil = mutableStateOf<Long>(0L)
    val bannedUntil: State<Long> = _bannedUntil

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Moderation.init(context) // Initialize Moderation with context
        loadSettings()
    }

    private fun loadSettings() {
        _isEnabled.value = sharedPreferences.getBoolean("is_enabled", true)
        _displayName.value = sharedPreferences.getString("display_name", "Anonymous") ?: "Anonymous"
        _avatarUrl.value = sharedPreferences.getString("avatar_url", null)
        _enableModeration.value = sharedPreferences.getBoolean("enable_moderation", true)
        _relayUrls.value = sharedPreferences.getStringSet("relay_urls", setOf("wss://gun-manhattan.herokuapp.com/gun"))?.toList() ?: listOf("wss://gun-manhattan.herokuapp.com/gun")
        _bannedUntil.value = sharedPreferences.getLong("banned_until", 0L)

        // Generate userId if not exists
        var currentUserId = sharedPreferences.getString("user_id", null)
        if (currentUserId == null) {
            currentUserId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("user_id", currentUserId).apply()
        }
        _userId.value = currentUserId
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        sharedPreferences.edit().putBoolean("is_enabled", enabled).apply()
    }

    fun setDisplayName(name: String) {
        _displayName.value = name
        sharedPreferences.edit().putString("display_name", name).apply()
    }

    fun setAvatarUrl(url: String?) {
        _avatarUrl.value = url
        sharedPreferences.edit().putString("avatar_url", url).apply()
    }

    fun setEnableModeration(enabled: Boolean) {
        _enableModeration.value = enabled
        sharedPreferences.edit().putBoolean("enable_moderation", enabled).apply()
    }

    fun setRelayUrls(urls: List<String>) {
        _relayUrls.value = urls
        sharedPreferences.edit().putStringSet("relay_urls", urls.toSet()).apply()
    }

    fun setBannedUntil(timestamp: Long) {
        _bannedUntil.value = timestamp
        sharedPreferences.edit().putLong("banned_until", timestamp).apply()
    }

    fun getDiceBearAvatarUrl(userName: String): String {
        // Simple DiceBear URL generation based on username hash
        val hash = userName.hashCode().toString()
        return "https://api.dicebear.com/8.x/identicon/svg?seed=$hash"
    }

    fun isUserBanned(): Boolean {
        return System.currentTimeMillis() < _bannedUntil.value
    }

    fun getBannedUntil(): Long {
        return _bannedUntil.value
    }
}
