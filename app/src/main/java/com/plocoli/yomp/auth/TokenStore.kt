package com.plocoli.yomp.auth

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stores the token as JSON in SharedPreferences. (Simple storage — it's a dedicated kids' device.) */
object TokenStore {
    private const val PREFS = "yomp_auth"
    private const val KEY_TOKEN = "token"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Token? {
        val raw = prefs(context).getString(KEY_TOKEN, null) ?: return null
        return runCatching { json.decodeFromString<Token>(raw) }.getOrNull()
    }

    fun save(context: Context, token: Token) {
        prefs(context).edit().putString(KEY_TOKEN, json.encodeToString(token)).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).apply()
    }
}
