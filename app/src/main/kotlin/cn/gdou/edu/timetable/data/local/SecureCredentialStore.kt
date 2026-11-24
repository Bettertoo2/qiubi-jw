package cn.gdou.edu.timetable.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SavedSessionInfo(
    val username: String = "",
    val rememberPassword: Boolean = true,
    val isLoggedIn: Boolean = false,
    val isDemoMode: Boolean = false
)

class SecureCredentialStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _sessionState = MutableStateFlow(readSessionInfo())
    val sessionState: StateFlow<SavedSessionInfo> = _sessionState.asStateFlow()

    private fun readSessionInfo(): SavedSessionInfo = SavedSessionInfo(
        username = prefs.getString(KEY_USERNAME, "") ?: "",
        rememberPassword = prefs.getBoolean(KEY_REMEMBER_PASSWORD, true),
        isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false),
        isDemoMode = prefs.getBoolean(KEY_IS_DEMO_MODE, false)
    )

    fun saveCredentials(username: String, password: String, rememberPassword: Boolean) {
        val encryptedPwd = if (rememberPassword && password.isNotEmpty()) {
            runCatching { KeystoreCrypto.encrypt(password) }.getOrDefault("")
        } else {
            ""
        }
        prefs.edit()
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD_ENC, encryptedPwd)
            .putBoolean(KEY_REMEMBER_PASSWORD, rememberPassword)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putBoolean(KEY_IS_DEMO_MODE, false)
            .apply()
        _sessionState.value = readSessionInfo()
    }

    fun markWebLoginSuccess(usernameHint: String) {
        prefs.edit()
            .apply {
                if (usernameHint.isNotBlank()) putString(KEY_USERNAME, usernameHint.trim())
            }
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putBoolean(KEY_IS_DEMO_MODE, false)
            .apply()
        _sessionState.value = readSessionInfo()
    }

    fun markDemoModeLoggedIn(demoStudentId: String) {
        prefs.edit()
            .putString(KEY_USERNAME, demoStudentId)
            .putString(KEY_PASSWORD_ENC, "")
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putBoolean(KEY_IS_DEMO_MODE, true)
            .apply()
        _sessionState.value = readSessionInfo()
    }

    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""

    fun getSavedPassword(): String {
        val enc = prefs.getString(KEY_PASSWORD_ENC, "") ?: ""
        if (enc.isBlank()) return ""
        return KeystoreCrypto.decrypt(enc)
    }

    fun hasSavedPassword(): Boolean = getSavedPassword().isNotEmpty()

    fun saveCookiesJson(json: String) {
        val encrypted = runCatching { KeystoreCrypto.encrypt(json) }.getOrDefault("")
        prefs.edit().putString(KEY_COOKIES_ENC, encrypted).apply()
    }

    fun loadCookiesJson(): String {
        val enc = prefs.getString(KEY_COOKIES_ENC, "") ?: ""
        if (enc.isBlank()) return ""
        return KeystoreCrypto.decrypt(enc)
    }

    fun clearCookies() {
        prefs.edit().remove(KEY_COOKIES_ENC).apply()
    }

    fun clearSession(keepUsername: Boolean = true) {
        val prevUser = if (keepUsername) getUsername() else ""
        prefs.edit()
            .putString(KEY_USERNAME, prevUser)
            .remove(KEY_PASSWORD_ENC)
            .remove(KEY_COOKIES_ENC)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .putBoolean(KEY_IS_DEMO_MODE, false)
            .apply()
        _sessionState.value = readSessionInfo()
    }

    companion object {
        private const val PREFS_NAME = "gdou_secure_auth_prefs"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD_ENC = "password_enc"
        private const val KEY_COOKIES_ENC = "cookies_enc"
        private const val KEY_REMEMBER_PASSWORD = "remember_password"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_DEMO_MODE = "is_demo_mode"
    }
}
