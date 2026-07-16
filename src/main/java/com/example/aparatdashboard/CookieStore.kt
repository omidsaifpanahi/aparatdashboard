// path: src/main/com/example/aparatdashboard/CookieStore.kt
package com.example.aparatdashboard

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object CookieStore {
    private const val PREF_NAME = "secure_cookie_prefs"
    private const val KEY_COOKIE = "aparat_cookie"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREF_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCookie(context: Context, cookie: String) {
        prefs(context).edit().putString(KEY_COOKIE, cookie.trim()).apply()
    }

    fun getCookie(context: Context): String {
        return prefs(context).getString(KEY_COOKIE, "") ?: ""
    }

    fun clearCookie(context: Context) {
        prefs(context).edit().remove(KEY_COOKIE).apply()
    }

    fun hasCookie(context: Context): Boolean {
        return getCookie(context).isNotBlank()
    }
}
