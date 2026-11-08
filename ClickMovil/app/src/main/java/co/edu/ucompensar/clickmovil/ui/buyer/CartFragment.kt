package co.edu.ucompensar.clickmovil.ui.buyer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.FragmentCartBinding
import co.edu.ucompensar.clickmovil.databinding.ItemCartBinding
import co.edu.ucompensar.clickmovil.model.CartLine
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.util.Money
import co.edu.ucompensar.clickmovil.util.shippingFor
import co.edu.ucompensar.clickmovil.util.toast
import java.util.Locale

class CartFragment : Fragment() {
    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { Repository(requireContext()) }
    private val session by lazy { SessionStore(requireContext()) }
    private val adapter = CartAdapter(
        onChange = { productId, quantity ->
            val user = session.currentUser() ?: return@CartAdapter
            when (val result = repository.updateCartQuantity(user.id, productId, quantity)) {
                is OpResult.Ok -> reload()
                is OpResult.Err -> {
                    requireContext().toast(result.message)
                    reload()
                }
            }
        }
    )

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) readLocation() else requireContext().toast("Puedes escribir la ciudad de entrega.")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        val user = session.currentUser()
        if (binding.city.text.isNullOrBlank()) {
            binding.city.setText(user?.city.orEmpty())
        }
        binding.locationButton.setOnClickListener { askLocation() }
        binding.payButton.setOnClickListener { goToPay() }
        reload()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) reload()
    }

    private fun reload() {
        val user = session.currentUser() ?: return
        val lines = repository.cart(user.id)
        adapter.submit(lines)
        val subtotal = lines.sumOf { it.subtotal }
        val city = binding.city.text?.toString().orEmpty()
        val shipping = if (lines.isEmpty()) 0L else shippingFor(city.ifBlank { user.city })
        binding.empty.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
        binding.subtotal.text = "Subtotal: ${Money.format(subtotal)}"
        binding.shipping.text = "Envío: ${Money.format(shipping)}"
        binding.total.text = "Total a pagar: ${Money.format(subtotal + shipping)}"
        binding.payButton.isEnabled = lines.isNotEmpty()
    }

    private fun goToPay() {
        val city = binding.city.text?.toString().orEmpty().trim()
        if (city.isBlank()) {
            requireContext().toast("Escribe la ciudad de entrega.")
            return
        }
        startActivity(Intent(requireContext(), CheckoutActivity::class.java).putExtra(CheckoutActivity.EXTRA_CITY, city))
    }

    private fun askLocation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Ubicación")
            .setMessage(getString(R.string.location_why))
            .setPositiveButton("Continuar") { _, _ ->
                locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton("Escribir ciudad", null)
            .show()
    }

    private fun readLocation() {
        val manager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = try {
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
        if (location == null) {
            requireContext().toast("No hay una ubicación reciente. Escribe la ciudad.")
            return
        }
        val city = try {
            @Suppress("DEPRECATION")
            Geocoder(requireContext(), Locale("es", "CO"))
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.locality
        } catch (_: Exception) {
            null
        }
        if (city.isNullOrBlank()) {
            requireContext().toast("Se leyó la ubicación, pero no se obtuvo la ciudad.")
        } else {
            binding.city.setText(city)
            reload()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private class CartAdapter(
    private val onChange: (Long, Int) -> Unit
) : RecyclerView.Adapter<CartAdapter.Holder>() {
    private val items = mutableListOf<CartLine>()

    fun submit(data: List<CartLine>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(line: CartLine) {
            binding.name.text = line.product.name
            binding.price.text = Money.format(line.subtotal)
            binding.qty.text = line.quantity.toString()
            binding.minus.setOnClickListener { onChange(line.product.id, line.quantity - 1) }
            binding.plus.setOnClickListener { onChange(line.product.id, line.quantity + 1) }
            binding.remove.setOnClickListener { onChange(line.product.id, 0) }
        }
    }
}
