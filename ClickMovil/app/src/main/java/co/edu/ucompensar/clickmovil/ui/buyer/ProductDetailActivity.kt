package co.edu.ucompensar.clickmovil.ui.buyer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivityProductDetailBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.model.Role
import co.edu.ucompensar.clickmovil.util.Money
import co.edu.ucompensar.clickmovil.util.categoryColor
import co.edu.ucompensar.clickmovil.util.decodeSampled
import co.edu.ucompensar.clickmovil.util.toast

class ProductDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductDetailBinding
    private var quantity = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }
        val product = Repository(this).product(intent.getLongExtra(EXTRA_ID, -1L))
        if (product == null) {
            toast("Producto no encontrado.")
            finish()
            return
        }
        val seller = Repository(this).userById(product.sellerId)
        val user = SessionStore(this).currentUser()
        binding.name.text = product.name
        binding.seller.text = "Vendedor: ${seller?.name ?: "Tienda"}"
        binding.price.text = Money.format(product.price)
        binding.stock.text = if (product.stock > 0) "Disponibles: ${product.stock}" else "Agotado"
        binding.description.text = product.description
        binding.fallback.text = product.name
        binding.fallback.setBackgroundColor(categoryColor(product.category))
        val bitmap = product.imagePath?.let { decodeSampled(it, 800) }
        if (bitmap != null) {
            binding.photo.setImageBitmap(bitmap)
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
            val buyer = SessionStore(this).currentUser() ?: return@setOnClickListener
            when (val result = Repository(this).addToCart(buyer.id, product.id, quantity)) {
                is OpResult.Ok -> {
                    toast("Producto agregado al carrito.")
                    finish()
                }
                is OpResult.Err -> toast(result.message)
            }
        }
    }

    companion object {
        const val EXTRA_ID = "product_id"
    }
}
