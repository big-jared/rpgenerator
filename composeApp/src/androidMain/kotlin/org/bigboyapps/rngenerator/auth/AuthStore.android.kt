package org.bigboyapps.rngenerator.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

@SuppressLint("StaticFieldLeak")
actual class AuthStore actual constructor() {

    companion object {
        private const val TAG = "AuthStore"
        private var appContext: Context? = null

        fun init(context: Context) {
            appContext = context.applicationContext
            Log.d(TAG, "init: context set")
        }
    }

    private val prefs: SharedPreferences?
        get() = appContext?.getSharedPreferences("rpgen_auth", Context.MODE_PRIVATE)

    actual fun save(name: String, email: String, token: String) {
        Log.d(TAG, "save: name=$name, email=$email, tokenLen=${token.length}")
        val result = prefs?.edit()
            ?.putString("name", name)
            ?.putString("email", email)
            ?.putString("token", token)
            ?.commit() // use commit() instead of apply() to confirm write
        Log.d(TAG, "save: commit result=$result, prefs=${prefs != null}")
    }

    actual fun load(): Triple<String, String, String>? {
        val p = prefs
        Log.d(TAG, "load: prefs=${p != null}")
        if (p == null) return null
        val name = p.getString("name", null)
        val email = p.getString("email", null)
        val token = p.getString("token", null)
        Log.d(TAG, "load: name=$name, email=$email, tokenLen=${token?.length}")
        if (name == null || email == null || token == null) return null
        return Triple(name, email, token)
    }

    actual fun clear() {
        Log.d(TAG, "clear")
        prefs?.edit()?.clear()?.commit()
    }
}
