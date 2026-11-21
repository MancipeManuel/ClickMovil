package co.edu.ucompensar.clickmovil.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivityLoginBinding
import co.edu.ucompensar.clickmovil.util.toast
import kotlinx.coroutines.launch

/**
 * Ingreso con correo y contraseña, o con huella si esa cuenta ya se activó en el perfil.
 * Después de cerrar sesión, Auth ya no tiene usuario y la huella pide volver a escribir la clave.
 */
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val session by lazy { SessionStore(this) }
    private val repository by lazy { Repository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (session.currentUser() != null) {
            openHome()
            return
        }
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.loginButton.setOnClickListener { login() }
        binding.registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.bioButton.setOnClickListener { loginWithBiometric() }
        binding.forgotButton.setOnClickListener {
            startActivity(Intent(this, RecoverPasswordActivity::class.java))
        }
    }

    /** Valida credenciales en Firebase Auth y abre el panel del rol. */
    private fun login() {
        val email = binding.email.text?.toString().orEmpty()
        val password = binding.password.text?.toString().orEmpty()
        binding.loginButton.isEnabled = false
        lifecycleScope.launch {
            val user = repository.login(email, password)
            binding.loginButton.isEnabled = true
            if (user == null) {
                toast("Correo o contraseña incorrectos.")
                return@launch
            }
            session.start(user)
            openHome()
        }
    }

    /** Pide la huella del dispositivo y reutiliza la sesión de Firebase si sigue abierta. */
    private fun loginWithBiometric() {
        val userId = session.biometricUserId()
        if (userId == null) {
            toast("Primero inicia sesión y activa la huella desde el perfil.")
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lifecycleScope.launch {
                        val authUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                        if (authUid == null || authUid != userId) {
                            toast("La sesión de Firebase expiró. Entra con correo y contraseña.")
                            return@launch
                        }
                        val user = repository.userById(userId)
                        if (user == null || !user.active) {
                            toast("La cuenta ya no está disponible.")
                            return@launch
                        }
                        session.start(user)
                        openHome()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        toast(errString.toString())
                    }
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Ingresar a ClickMóvil")
                .setSubtitle("La aplicación no guarda la huella. Usa la del dispositivo.")
                .setNegativeButtonText("Cancelar")
                .build()
        )
    }

    private fun openHome() {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
