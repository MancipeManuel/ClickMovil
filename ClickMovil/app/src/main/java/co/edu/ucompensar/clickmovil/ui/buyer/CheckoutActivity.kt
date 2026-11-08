package co.edu.ucompensar.clickmovil.ui.buyer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivityCheckoutBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.util.Money
import co.edu.ucompensar.clickmovil.util.bindBackToolbar
import co.edu.ucompensar.clickmovil.util.shippingFor
import co.edu.ucompensar.clickmovil.util.toast

class CheckoutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCheckoutBinding
    private var paying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindBackToolbar(binding.toolbar.root, "Pasarela de pago")
        val user = SessionStore(this).currentUser()
        if (user == null) {
            finish()
            return
        }
        val city = intent.getStringExtra(EXTRA_CITY).orEmpty()
        val lines = Repository(this).cart(user.id)
        val subtotal = lines.sumOf { it.subtotal }
        val shipping = shippingFor(city)
        binding.summary.text = buildString {
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
        binding.payButton.setOnClickListener {
            if (paying) return@setOnClickListener
            paying = true
            binding.payButton.isEnabled = false
            when (val result = Repository(this).checkout(user.id, city)) {
                is OpResult.Ok -> {
                    val order = result.value
                    binding.result.text = "Pago aceptado\n${order.code}\nReferencia ${order.paymentRef}\nEstado: ${order.status}"
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
