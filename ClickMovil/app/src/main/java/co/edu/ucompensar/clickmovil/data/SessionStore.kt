package co.edu.ucompensar.clickmovil.data

import android.content.Context
import co.edu.ucompensar.clickmovil.model.Role
import co.edu.ucompensar.clickmovil.model.User
import com.google.firebase.auth.FirebaseAuth

class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val auth = FirebaseAuth.getInstance()

    fun currentUser(): User? {
        val uid = auth.currentUser?.uid ?: prefs.getString(KEY_USER, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val role = prefs.getString(KEY_ROLE, null) ?: return null
        if (prefs.getString(KEY_USER, null) != uid) return null
        return User(
            id = uid,
            name = name,
            email = email,
            phone = prefs.getString(KEY_PHONE, "").orEmpty(),
            city = prefs.getString(KEY_CITY, "").orEmpty(),
            role = Role.from(role),
            active = prefs.getBoolean(KEY_ACTIVE, true)
        ).takeIf { it.active }
    }

    fun start(user: User) {
        prefs.edit()
            .putString(KEY_USER, user.id)
            .putString(KEY_NAME, user.name)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_PHONE, user.phone)
            .putString(KEY_CITY, user.city)
            .putString(KEY_ROLE, user.role.name)
            .putBoolean(KEY_ACTIVE, user.active)
            .apply()
    }

    fun logout() {
        auth.signOut()
        prefs.edit()
            .remove(KEY_USER)
            .remove(KEY_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_PHONE)
            .remove(KEY_CITY)
            .remove(KEY_ROLE)
            .remove(KEY_ACTIVE)
            .apply()
    }

    fun enableBiometric(userId: String) {
        prefs.edit().putString(KEY_BIO, userId).apply()
    }

    fun biometricUserId(): String? = prefs.getString(KEY_BIO, null)

    companion object {
        private const val PREFS = "clickmovil_session"
        private const val KEY_USER = "user_id"
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_PHONE = "user_phone"
        private const val KEY_CITY = "user_city"
        private const val KEY_ROLE = "user_role"
        private const val KEY_ACTIVE = "user_active"
        private const val KEY_BIO = "bio_user_id"
    }
}
