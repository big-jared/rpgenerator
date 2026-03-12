package org.bigboyapps.rngenerator.auth

import platform.Foundation.NSUserDefaults

private const val KEY_NAME = "auth_name"
private const val KEY_EMAIL = "auth_email"
private const val KEY_TOKEN = "auth_token"

actual class AuthStore actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun save(name: String, email: String, token: String) {
        defaults.setObject(name, KEY_NAME)
        defaults.setObject(email, KEY_EMAIL)
        defaults.setObject(token, KEY_TOKEN)
        defaults.synchronize()
    }

    actual fun load(): Triple<String, String, String>? {
        val name = defaults.stringForKey(KEY_NAME) ?: return null
        val email = defaults.stringForKey(KEY_EMAIL) ?: return null
        val token = defaults.stringForKey(KEY_TOKEN) ?: return null
        return Triple(name, email, token)
    }

    actual fun clear() {
        defaults.removeObjectForKey(KEY_NAME)
        defaults.removeObjectForKey(KEY_EMAIL)
        defaults.removeObjectForKey(KEY_TOKEN)
        defaults.synchronize()
    }
}
