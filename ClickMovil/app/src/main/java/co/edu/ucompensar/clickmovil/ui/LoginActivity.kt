package co.edu.ucompensar.clickmovil.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivityLoginBinding
import co.edu.ucompensar.clickmovil.util.toast

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

    private fun login() {
        val user = repository.login(
            binding.email.text?.toString().orEmpty(),
            binding.password.text?.toString().orEmpty()
        )
        if (user == null) {
            toast("Correo o contraseña incorrectos.")
            return
        }
        session.start(user.id)
        openHome()
    }

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
                    val user = repository.userById(userId)
                    if (user == null || !user.active) {
                        toast("La cuenta ya no está disponible.")
                        return
                    }
                    session.start(user.id)
                    openHome()
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
