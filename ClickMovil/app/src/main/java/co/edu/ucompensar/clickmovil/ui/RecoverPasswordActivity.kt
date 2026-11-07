package co.edu.ucompensar.clickmovil.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.databinding.ActivityRecoverBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.util.bindBackToolbar
import co.edu.ucompensar.clickmovil.util.toast

class RecoverPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecoverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecoverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindBackToolbar(binding.toolbar.root, getString(R.string.recover_title))
        binding.sendCodeButton.setOnClickListener { sendCode() }
        binding.changeButton.setOnClickListener { changePassword() }
    }

    private fun sendCode() {
        when (val result = Repository(this).startRecovery(binding.email.text?.toString().orEmpty())) {
            is OpResult.Ok -> {
                binding.codeHint.visibility = View.VISIBLE
                binding.codeHint.text = getString(R.string.recover_help) + "\n\nCódigo de demostración: ${result.value}"
                listOf(binding.codeLayout, binding.passwordLayout, binding.confirmLayout, binding.changeButton)
                    .forEach { it.visibility = View.VISIBLE }
            }
            is OpResult.Err -> toast(result.message)
        }
    }

    private fun changePassword() {
        val password = binding.password.text?.toString().orEmpty()
        val confirm = binding.confirm.text?.toString().orEmpty()
        if (password != confirm) {
            toast("Las contraseñas no coinciden.")
            return
        }
        when (
            val result = Repository(this).resetPassword(
                binding.email.text?.toString().orEmpty(),
                binding.code.text?.toString().orEmpty(),
                password
            )
        ) {
            is OpResult.Ok -> {
                toast("Contraseña actualizada. Ya puedes iniciar sesión.")
                finish()
            }
            is OpResult.Err -> toast(result.message)
        }
    }
}
