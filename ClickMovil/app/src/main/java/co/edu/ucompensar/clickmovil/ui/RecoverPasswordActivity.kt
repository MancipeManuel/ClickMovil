package co.edu.ucompensar.clickmovil.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.databinding.ActivityRecoverBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.util.bindBackToolbar
import co.edu.ucompensar.clickmovil.util.toast
import kotlinx.coroutines.launch

class RecoverPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecoverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecoverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindBackToolbar(binding.toolbar.root, getString(R.string.recover_title))
        binding.sendCodeButton.setOnClickListener { sendResetEmail() }
        binding.changeButton.visibility = View.GONE
        binding.codeLayout.visibility = View.GONE
        binding.passwordLayout.visibility = View.GONE
        binding.confirmLayout.visibility = View.GONE
    }

    private fun sendResetEmail() {
        lifecycleScope.launch {
            when (val result = Repository(this@RecoverPasswordActivity).startRecovery(binding.email.text?.toString().orEmpty())) {
                is OpResult.Ok -> {
                    binding.codeHint.visibility = View.VISIBLE
                    binding.codeHint.text =
                        "Te enviamos un enlace de recuperación a tu correo (Firebase Auth).\n\n${result.value}\n\n" +
                            "Abre el enlace desde el mensaje para definir una nueva contraseña."
                }
                is OpResult.Err -> toast(result.message)
            }
        }
    }
}
