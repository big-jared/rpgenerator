package org.bigboyapps.rngenerator.auth

/**
 * Platform-specific persistent auth storage.
 * On Android: SharedPreferences. On iOS: stub (returns null).
 *
 * Usage: call [AuthStore.init] once from the platform entry point (e.g., MainActivity),
 * then access via [AuthStore.instance].
 */
expect class AuthStore() {
    fun save(name: String, email: String, token: String)
    fun load(): Triple<String, String, String>?
    fun clear()
}

object AuthStoreProvider {
    var instance: AuthStore? = null

    fun init(store: AuthStore) {
        instance = store
    }
}
