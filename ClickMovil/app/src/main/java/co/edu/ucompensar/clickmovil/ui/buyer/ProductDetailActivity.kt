package co.edu.ucompensar.clickmovil.ui.buyer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivityProductDetailBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.model.Product
import co.edu.ucompensar.clickmovil.model.Role
import co.edu.ucompensar.clickmovil.util.Money
import co.edu.ucompensar.clickmovil.util.bindProductImage
import co.edu.ucompensar.clickmovil.util.categoryColor
import co.edu.ucompensar.clickmovil.util.toast
import kotlinx.coroutines.launch

class ProductDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductDetailBinding
    private var quantity = 1
    private var product: Product? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }
        val id = intent.getStringExtra(EXTRA_ID)
        if (id.isNullOrBlank()) {
            toast("Producto no encontrado.")
            finish()
            return
        }
        lifecycleScope.launch {
            val loaded = Repository(this@ProductDetailActivity).product(id)
            if (loaded == null) {
                toast("Producto no encontrado.")
                finish()
                return@launch
            }
            product = loaded
            bind(loaded)
        }
    }

    private fun bind(product: Product) {
        lifecycleScope.launch {
            val seller = Repository(this@ProductDetailActivity).userById(product.sellerId)
            val user = SessionStore(this@ProductDetailActivity).currentUser()
            binding.name.text = product.name
            binding.seller.text = "Vendedor: ${seller?.name ?: "Tienda"}"
            binding.price.text = Money.format(product.price)
            binding.stock.text = if (product.stock > 0) "Disponibles: ${product.stock}" else "Agotado"
            binding.description.text = product.description
            binding.fallback.text = product.name
            binding.fallback.setBackgroundColor(categoryColor(product.category))
            if (!product.imagePath.isNullOrBlank()) {
                binding.photo.bindProductImage(product.imagePath, 800)
                binding.photo.visibility = View.VISIBLE
            }
            val canBuy = user?.role == Role.COMPRADOR && product.active && product.stock > 0
            if (!canBuy) {
                binding.qtyRow.visibility = View.GONE
                binding.addButton.visibility = View.GONE
            }
            binding.qty.text = quantity.toString()
            binding.minus.setOnClickListener {
                if (quantity > 1) {
                    quantity -= 1
                    binding.qty.text = quantity.toString()
                }
            }
            binding.plus.setOnClickListener {
                if (quantity < product.stock) {
                    quantity += 1
                    binding.qty.text = quantity.toString()
                } else {
                    toast("Solo hay ${product.stock} unidades.")
                }
            }
            binding.addButton.setOnClickListener {
                val buyer = SessionStore(this@ProductDetailActivity).currentUser() ?: return@setOnClickListener
                lifecycleScope.launch {
                    when (val result = Repository(this@ProductDetailActivity).addToCart(buyer.id, product.id, quantity)) {
                        is OpResult.Ok -> {
                            toast("Producto agregado al carrito.")
                            finish()
                        }
                        is OpResult.Err -> toast(result.message)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID = "product_id"
    }
}
