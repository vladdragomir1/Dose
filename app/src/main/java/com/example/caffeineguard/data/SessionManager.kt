package com.example.caffeineguard.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_user_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val LOGGED_IN_KEY = "logged_in_key"
    private val CURRENT_USER_KEY = "current_user_key"

    // Helper: Trim username to prevent "user1 " errors
    private fun passwordKey(username: String) = "password_${username.trim()}"

    fun saveCredentials(username: String, password: String) {
        sharedPreferences.edit()
            .putString(passwordKey(username), password)
            .putString(CURRENT_USER_KEY, username.trim())
            .putBoolean(LOGGED_IN_KEY, true)
            .apply()
    }

    fun userExists(username: String): Boolean {
        return sharedPreferences.contains(passwordKey(username))
    }

    fun validateCredentials(username: String, password: String): Boolean {
        val stored = sharedPreferences.getString(passwordKey(username), null)
        return stored != null && stored == password
    }

    fun getCurrentUsername(): String? {
        return sharedPreferences.getString(CURRENT_USER_KEY, null)
    }

    fun setLoggedIn(username: String) {
        sharedPreferences.edit()
            .putString(CURRENT_USER_KEY, username.trim())
            .putBoolean(LOGGED_IN_KEY, true)
            .apply()
    }

    fun logoutCurrentUser() {
        sharedPreferences.edit()
            .putBoolean(LOGGED_IN_KEY, false)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(LOGGED_IN_KEY, false)
    }

    fun deleteUser(username: String) {
        val editor = sharedPreferences.edit()
        editor.remove(passwordKey(username))

        val current = getCurrentUsername()
        if (current == username.trim()) {
            editor.remove(CURRENT_USER_KEY)
            editor.putBoolean(LOGGED_IN_KEY, false)
        }

        editor.apply()
    }

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}