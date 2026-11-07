package co.edu.ucompensar.clickmovil.data

import android.content.Context
import co.edu.ucompensar.clickmovil.model.User

class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val repository = Repository(context.applicationContext)

    fun currentUser(): User? {
        val id = prefs.getLong(KEY_USER, -1L)
        if (id < 0) return null
        return repository.userById(id)?.takeIf { it.active }
    }

    fun start(userId: Long) {
        prefs.edit().putLong(KEY_USER, userId).apply()
    }

    fun logout() {
        prefs.edit().remove(KEY_USER).apply()
    }

    fun enableBiometric(userId: Long) {
        prefs.edit().putLong(KEY_BIO, userId).apply()
    }

    fun biometricUserId(): Long? {
        val id = prefs.getLong(KEY_BIO, -1L)
        return if (id < 0) null else id
    }

    companion object {
        private const val PREFS = "clickmovil_session"
        private const val KEY_USER = "user_id"
        private const val KEY_BIO = "bio_user_id"
    }
}
