package co.edu.ucompensar.clickmovil.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.databinding.ItemFeaturedBinding
import co.edu.ucompensar.clickmovil.model.Product
import co.edu.ucompensar.clickmovil.util.Money
import co.edu.ucompensar.clickmovil.util.categoryColor
import co.edu.ucompensar.clickmovil.util.decodeSampled

class FeaturedProductAdapter(
    private val onOpen: (Product) -> Unit,
    private val onAdd: ((Product) -> Unit)? = null
) : RecyclerView.Adapter<FeaturedProductAdapter.Holder>() {

    private val items = mutableListOf<Product>()

    fun submit(data: List<Product>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemFeaturedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemFeaturedBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product) {
            binding.name.text = product.name
            binding.price.text = Money.format(product.price)
            binding.stock.text = binding.root.context.getString(R.string.stock_available, product.stock.coerceAtLeast(0))
            val bitmap = product.imagePath?.let { decodeSampled(it, 420) }
            if (bitmap != null) {
                binding.photo.setImageBitmap(bitmap)
                binding.photo.visibility = View.VISIBLE
                binding.fallback.visibility = View.GONE
            } else {
                binding.photo.visibility = View.GONE
                binding.fallback.visibility = View.VISIBLE
                binding.fallback.text = "CM"
                val base = categoryColor(product.category)
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = binding.root.resources.getDimension(R.dimen.radius_photo)
                    colors = intArrayOf(base, ColorUtils.blendARGB(base, 0xFF000000.toInt(), 0.18f))
                    orientation = GradientDrawable.Orientation.TL_BR
                }
                binding.fallback.background = bg
            }
            if (onAdd == null) {
                binding.addButton.visibility = View.GONE
            } else {
                binding.addButton.visibility = View.VISIBLE
                binding.addButton.alpha = if (product.stock > 0) 1f else 0.35f
                binding.addButton.isEnabled = product.stock > 0
                binding.addButton.setOnClickListener { onAdd.invoke(product) }
            }
            binding.root.setOnClickListener { onOpen(product) }
        }
    }
}
