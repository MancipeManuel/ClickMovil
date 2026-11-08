package co.edu.ucompensar.clickmovil.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.FragmentProfileBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.ui.LoginActivity
import co.edu.ucompensar.clickmovil.util.toast

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { Repository(requireContext()) }
    private val session by lazy { SessionStore(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val user = session.currentUser() ?: return
        binding.role.text = user.role.label.uppercase()
        binding.name.setText(user.name)
        binding.email.setText(user.email)
        binding.phone.setText(user.phone)
        binding.city.setText(user.city)
        binding.saveButton.setOnClickListener { save() }
        binding.bioButton.setOnClickListener { enableBiometric() }
        binding.policyButton.setOnClickListener { showPolicy() }
        binding.logoutButton.setOnClickListener { logout() }
    }

    private fun save() {
        val user = session.currentUser() ?: return
        when (
            val result = repository.updateProfile(
                user.id,
                binding.name.text?.toString().orEmpty(),
                binding.phone.text?.toString().orEmpty(),
                binding.city.text?.toString().orEmpty()
            )
        ) {
            is OpResult.Ok -> requireContext().toast("Perfil actualizado.")
            is OpResult.Err -> requireContext().toast(result.message)
        }
    }

    private fun enableBiometric() {
        val user = session.currentUser() ?: return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
        val can = BiometricManager.from(requireContext()).canAuthenticate(authenticators)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            requireContext().toast("Este dispositivo no tiene biometría lista. Puedes seguir entrando con contraseña.")
            return
        }
        val executor = ContextCompat.getMainExecutor(requireContext())
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    session.enableBiometric(user.id)
                    requireContext().toast("Listo. La próxima vez puedes entrar con huella. No guardamos la huella.")
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Activar huella")
                .setSubtitle("Confirma que este dispositivo puede identificarte.")
                .setNegativeButtonText("Cancelar")
                .build()
        )
    }

    private fun showPolicy() {
        AlertDialog.Builder(requireContext())
            .setTitle("Datos y condiciones")
            .setMessage(
                "ClickMóvil pide solo los datos de la cuenta, el contacto y la ciudad de entrega. " +
                    "La cámara se usa para fotos de productos y la ubicación solo si tú la autorizas para sugerir la ciudad. " +
                    "La contraseña se guarda cifrada. No almacenamos datos de tarjeta.\n\n" +
                    "Puedes actualizar tu perfil. Los pedidos confirmados se conservan aunque cambie el estado, " +
                    "para no perder la trazabilidad de la compra.\n\n" +
                    "Precios, disponibilidad, garantía de 30 días y condiciones de entrega se muestran antes de pagar. " +
                    "Canal de reclamos: soporte@clickmovil.com"
            )
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun logout() {
        session.logout()
        startActivity(
            Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
