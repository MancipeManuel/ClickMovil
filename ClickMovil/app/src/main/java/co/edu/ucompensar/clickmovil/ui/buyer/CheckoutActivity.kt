package co.edu.ucompensar.clickmovil.ui.buyer

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivityCheckoutBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.util.Money
import co.edu.ucompensar.clickmovil.util.bindBackToolbar
import co.edu.ucompensar.clickmovil.util.shippingFor
import co.edu.ucompensar.clickmovil.util.toast
import kotlinx.coroutines.launch

/**
 * Confirmación del pedido. Guarda la orden en Firestore.
 * No cobra en ePayco: la referencia que muestra es interna.
 */
class CheckoutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCheckoutBinding
    private var paying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars()
        bindBackToolbar(binding.toolbar.root, "Confirmar pedido")
        val user = SessionStore(this).currentUser()
        val city = intent.getStringExtra(EXTRA_CITY).orEmpty().trim()
        if (user == null) {
            toast("Debes iniciar sesión.")
            finish()
            return
        }
        if (city.isEmpty()) {
            toast("Escribe la ciudad de entrega.")
            finish()
            return
        }
        binding.summary.text = "Cargando el pedido para $city…"
        binding.payButton.text = "Confirmar pedido"
        binding.payButton.setOnClickListener { confirm(user.id, city) }
        loadSummary(user.id, city)
    }

    /** Deja la barra y el botón debajo de las barras del sistema, sin tapar el resumen. */
    private fun applySystemBars() {
        val root = binding.root
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun loadSummary(userId: String, city: String) {
        lifecycleScope.launch {
            try {
                val lines = Repository(this@CheckoutActivity).cart(userId)
                val subtotal = lines.sumOf { it.subtotal }
                val shipping = shippingFor(city)
                binding.summary.text = buildString {
                    append("El pedido se guarda en ClickMóvil. No se cobra en ePayco.\n\n")
                    if (lines.isEmpty()) {
                        append("No hay productos en el carrito.")
                    } else {
                        lines.forEach { append("${it.product.name} x${it.quantity}  ${Money.format(it.subtotal)}\n") }
                        append("\nSubtotal: ${Money.format(subtotal)}")
                        append("\nEnvío a $city: ${Money.format(shipping)}")
                        append("\nTotal: ${Money.format(subtotal + shipping)}")
                    }
                }
                binding.payButton.isEnabled = lines.isNotEmpty() && !paying
            } catch (error: Exception) {
                binding.payButton.isEnabled = true
                binding.summary.text = "No se pudo leer el carrito. Puedes confirmar de nuevo."
                toast(error.message ?: "No se pudo leer el carrito.")
            }
        }
    }

    private fun confirm(userId: String, city: String) {
        if (paying) return
        lifecycleScope.launch {
            val lines = try {
                Repository(this@CheckoutActivity).cart(userId)
            } catch (error: Exception) {
                toast(error.message ?: "No se pudo leer el carrito.")
                return@launch
            }
            if (lines.isEmpty()) {
                toast("El carrito está vacío.")
                return@launch
            }
            val total = lines.sumOf { it.subtotal } + shippingFor(city)
            if (isFinishing) return@launch
            AlertDialog.Builder(this@CheckoutActivity)
                .setTitle("Confirmar pedido")
                .setMessage(
                    "Se registrará el pedido por ${Money.format(total)} " +
                        "y se descontará el stock.\n\n" +
                        "No hay cobro con tarjeta ni transacción en ePayco."
                )
                .setPositiveButton("Pagar") { _, _ -> pay(userId, city) }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    /** Guarda el pedido y muestra el código y la referencia interna. */
    private fun pay(userId: String, city: String) {
        if (paying) return
        paying = true
        binding.payButton.isEnabled = false
        lifecycleScope.launch {
            try {
                when (val result = Repository(this@CheckoutActivity).checkout(userId, city)) {
                    is OpResult.Ok -> {
                        val order = result.value
                        binding.result.text =
                            "Pedido registrado\n${order.code}\nReferencia interna ${order.paymentRef}\nEstado: ${order.status}\nSin cobro en ePayco"
                        binding.payButton.text = "Listo"
                    }
                    is OpResult.Err -> {
                        paying = false
                        binding.payButton.isEnabled = true
                        binding.result.text = result.message
                        toast(result.message)
                    }
                }
            } catch (error: Exception) {
                paying = false
                binding.payButton.isEnabled = true
                val message = error.message ?: "No se pudo registrar el pedido."
                binding.result.text = message
                toast(message)
            }
        }
    }

    companion object {
        const val EXTRA_CITY = "city"
    }
}
