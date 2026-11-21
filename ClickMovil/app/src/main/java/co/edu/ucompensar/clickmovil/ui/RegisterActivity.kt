package co.edu.ucompensar.clickmovil.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivityRegisterBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.model.Role
import co.edu.ucompensar.clickmovil.util.bindBackToolbar
import co.edu.ucompensar.clickmovil.util.toast
import kotlinx.coroutines.launch

/** Registro público de comprador o vendedor. El rol administrador no se ofrece aquí. */
class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private val roles = listOf(Role.COMPRADOR, Role.VENDEDOR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindBackToolbar(binding.toolbar.root, getString(R.string.register_title))
        binding.role.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            roles.map { it.label }
        )
        binding.registerButton.setOnClickListener { register() }
    }

    /** Valida el formulario, crea la cuenta en Auth y deja la sesión iniciada. */
    private fun register() {
        val name = binding.name.text?.toString().orEmpty().trim()
        val email = binding.email.text?.toString().orEmpty().trim()
        val password = binding.password.text?.toString().orEmpty()
        val phone = binding.phone.text?.toString().orEmpty().trim()
        val city = binding.city.text?.toString().orEmpty().trim()
        if (name.isBlank() || email.isBlank() || city.isBlank()) {
            toast("Completa nombre, correo y ciudad.")
            return
        }
        if (!email.contains("@") || !email.contains(".") || email.contains(" ")) {
            toast("El correo no tiene un formato válido.")
            return
        }
        if (password.length < 6) {
            toast("La contraseña debe tener al menos 6 caracteres.")
            return
        }
        if (!binding.privacy.isChecked) {
            toast("Debes autorizar el tratamiento de tus datos.")
            return
        }
        val role = roles[binding.role.selectedItemPosition]
        binding.registerButton.isEnabled = false
        lifecycleScope.launch {
            when (val result = Repository(this@RegisterActivity).register(name, email, password, phone, city, role)) {
                is OpResult.Ok -> {
                    SessionStore(this@RegisterActivity).start(result.value)
                    startActivity(
                        Intent(this@RegisterActivity, HomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                }
                is OpResult.Err -> {
                    binding.registerButton.isEnabled = true
                    toast(result.message)
                }
            }
        }
    }
}
