package co.edu.ucompensar.clickmovil.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.edu.ucompensar.clickmovil.databinding.ItemProductBinding
import co.edu.ucompensar.clickmovil.model.Product
import co.edu.ucompensar.clickmovil.util.Money
import co.edu.ucompensar.clickmovil.util.categoryColor
import co.edu.ucompensar.clickmovil.util.decodeSampled

class ProductAdapter(
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.Holder>() {

    private val items = mutableListOf<Product>()

    fun submit(data: List<Product>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product) {
            binding.name.text = product.name
            binding.meta.text = "${product.category} · ${product.compatibility}"
            binding.price.text = Money.format(product.price)
            if (product.stock <= 0 || !product.active) {
                binding.stock.text = if (!product.active) "Retirado" else "Agotado"
                binding.stock.setTextColor(0xFFB91C1C.toInt())
            } else {
                binding.stock.text = "${product.stock} disponibles"
                binding.stock.setTextColor(0xFF047857.toInt())
            }
            val bitmap = product.imagePath?.let { decodeSampled(it, 160) }
            if (bitmap != null) {
                binding.photo.setImageBitmap(bitmap)
                binding.photo.visibility = View.VISIBLE
                binding.fallback.visibility = View.GONE
            } else {
                binding.photo.visibility = View.GONE
                binding.fallback.visibility = View.VISIBLE
                binding.fallback.text = product.name.take(1).uppercase()
                binding.fallback.setBackgroundColor(categoryColor(product.category))
            }
            binding.root.setOnClickListener { onClick(product) }
        }
    }
}
