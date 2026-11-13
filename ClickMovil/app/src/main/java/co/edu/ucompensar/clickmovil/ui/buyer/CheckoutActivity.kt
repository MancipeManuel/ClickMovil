package co.edu.ucompensar.clickmovil.ui.buyer

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
 * Pasarela de pago (integración académica con flujo tipo ePayco).
 * Confirma el cobro, genera referencia EPAYCO-xxxxx y crea el pedido en Firestore.
 */
class CheckoutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCheckoutBinding
    private var paying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindBackToolbar(binding.toolbar.root, "Pasarela ePayco")
        val user = SessionStore(this).currentUser()
        if (user == null) {
            finish()
            return
        }
        val city = intent.getStringExtra(EXTRA_CITY).orEmpty()
        lifecycleScope.launch {
            val lines = Repository(this@CheckoutActivity).cart(user.id)
            val subtotal = lines.sumOf { it.subtotal }
            val shipping = shippingFor(city)
            binding.summary.text = buildString {
                append("Pasarela certificada: ePayco\n\n")
                if (lines.isEmpty()) {
                    append("No hay productos en el carrito.")
                } else {
                    lines.forEach { append("${it.product.name} x${it.quantity}  ${Money.format(it.subtotal)}\n") }
                    append("\nSubtotal: ${Money.format(subtotal)}")
                    append("\nEnvío a $city: ${Money.format(shipping)}")
                    append("\nTotal: ${Money.format(subtotal + shipping)}")
                }
            }
            binding.payButton.isEnabled = lines.isNotEmpty()
            binding.payButton.text = "Pagar con ePayco"
            binding.payButton.setOnClickListener {
                if (paying) return@setOnClickListener
                AlertDialog.Builder(this@CheckoutActivity)
                    .setTitle("Confirmar pago ePayco")
                    .setMessage(
                        "Se procesará el pago por ${Money.format(subtotal + shipping)} " +
                            "a través de la pasarela ePayco (modo demostración académica).\n\n" +
                            "No se almacenan datos de tarjeta en ClickMóvil."
                    )
                    .setPositiveButton("Pagar") { _, _ -> pay(user.id, city) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun pay(userId: String, city: String) {
        if (paying) return
        paying = true
        binding.payButton.isEnabled = false
        lifecycleScope.launch {
            when (val result = Repository(this@CheckoutActivity).checkout(userId, city)) {
                is OpResult.Ok -> {
                    val order = result.value
                    binding.result.text =
                        "Pago aceptado por ePayco\n${order.code}\nReferencia ${order.paymentRef}\nEstado: ${order.status}"
                    binding.payButton.text = "Listo"
                }
                is OpResult.Err -> {
                    paying = false
                    binding.payButton.isEnabled = true
                    toast(result.message)
                }
            }
        }
    }

    companion object {
        const val EXTRA_CITY = "city"
    }
}
