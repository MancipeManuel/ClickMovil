package co.edu.ucompensar.clickmovil.ui.buyer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.FragmentListBinding
import co.edu.ucompensar.clickmovil.databinding.ItemOrderBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.model.StoreOrder
import co.edu.ucompensar.clickmovil.util.Dates
import co.edu.ucompensar.clickmovil.util.Money
import co.edu.ucompensar.clickmovil.util.toast

class OrdersFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { Repository(requireContext()) }
    private val session by lazy { SessionStore(requireContext()) }
    private val mode by lazy { requireArguments().getString(ARG_MODE) ?: MODE_BUYER }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        reload()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) reload()
    }

    private fun reload() {
        val user = session.currentUser() ?: return
        val orders = when (mode) {
            MODE_SELLER -> repository.ordersForSeller(user.id)
            MODE_ADMIN -> repository.allOrders()
            else -> repository.ordersForBuyer(user.id)
        }
        binding.empty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
        binding.empty.text = if (mode == MODE_BUYER) "Todavía no tienes pedidos." else "No hay pedidos para mostrar."
        binding.list.adapter = OrderAdapter(orders, mode) { order, action ->
            when (action) {
                ACTION_DETAIL -> showDetail(order)
                ACTION_CANCEL -> cancel(order)
                ACTION_SHIP -> ship(order)
                ACTION_DELIVER -> deliver(order)
            }
        }
    }

    private fun showDetail(order: StoreOrder) {
        val lines = order.items.joinToString("\n") {
            "${it.productName} x${it.quantity} · ${Money.format(it.unitPrice * it.quantity)}"
        }
        val retract = if (mode == MODE_BUYER) {
            "\n\nRetracto: puedes cancelar mientras el pedido esté pagado y no haya sido enviado. " +
                "En un comercio real la devolución del dinero no supera 15 días calendario. " +
                "Reclamos: soporte@clickmovil.com"
        } else {
            ""
        }
        AlertDialog.Builder(requireContext())
            .setTitle(order.code)
            .setMessage(
                "Estado: ${order.status}\nComprador: ${order.buyerName}\nCiudad: ${order.city}\n" +
                    "Envío: ${Money.format(order.shipping)}\nTotal: ${Money.format(order.total)}\n" +
                    "Pago: ${order.paymentRef}\n\n$lines$retract"
            )
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun cancel(order: StoreOrder) {
        val user = session.currentUser() ?: return
        when (val result = repository.cancelOrder(user.id, order.id)) {
            is OpResult.Ok -> {
                requireContext().toast("Pedido cancelado. El registro se conserva y el inventario volvió.")
                reload()
            }
            is OpResult.Err -> requireContext().toast(result.message)
        }
    }

    private fun ship(order: StoreOrder) {
        val user = session.currentUser() ?: return
        when (val result = repository.markShipped(user.id, order.id)) {
            is OpResult.Ok -> reload()
            is OpResult.Err -> requireContext().toast(result.message)
        }
    }

    private fun deliver(order: StoreOrder) {
        when (val result = repository.markDelivered(order.id)) {
            is OpResult.Ok -> reload()
            is OpResult.Err -> requireContext().toast(result.message)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_MODE = "mode"
        const val MODE_BUYER = "buyer"
        const val MODE_SELLER = "seller"
        const val MODE_ADMIN = "admin"
        private const val ACTION_DETAIL = "detail"
        private const val ACTION_CANCEL = "cancel"
        private const val ACTION_SHIP = "ship"
        private const val ACTION_DELIVER = "deliver"

        fun newBuyer() = OrdersFragment().apply { arguments = bundleOf(ARG_MODE to MODE_BUYER) }
        fun newSeller() = OrdersFragment().apply { arguments = bundleOf(ARG_MODE to MODE_SELLER) }
        fun newAdmin() = OrdersFragment().apply { arguments = bundleOf(ARG_MODE to MODE_ADMIN) }
    }
}

private class OrderAdapter(
    private val orders: List<StoreOrder>,
    private val mode: String,
    private val onAction: (StoreOrder, String) -> Unit
) : RecyclerView.Adapter<OrderAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = orders.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(orders[position])

    inner class Holder(private val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(order: StoreOrder) {
            binding.code.text = order.code
            binding.status.text = order.status
            binding.status.setTextColor(
                when (order.status) {
                    "PAGADO" -> 0xFF047857.toInt()
                    "ENVIADO" -> 0xFF1D4ED8.toInt()
                    "ENTREGADO" -> 0xFF0F766E.toInt()
                    else -> 0xFFB45309.toInt()
                }
            )
            val items = order.items.joinToString { "${it.productName} x${it.quantity}" }
            binding.meta.text = "${Dates.format(order.createdAt)} · ${Money.format(order.total)}\n$items\n${order.buyerName} · ${order.city}"
            binding.root.setOnClickListener { onAction(order, "detail") }
            val (label, action) = when {
                mode == OrdersFragment.MODE_BUYER && order.status == "PAGADO" -> "Cancelar pedido" to "cancel"
                mode == OrdersFragment.MODE_SELLER && order.status == "PAGADO" -> "Marcar enviado" to "ship"
                mode == OrdersFragment.MODE_ADMIN && order.status != "ENTREGADO" && order.status != "CANCELADO" -> "Marcar entregado" to "deliver"
                else -> null to null
            }
            if (label == null || action == null) {
                binding.action.visibility = View.GONE
            } else {
                binding.action.visibility = View.VISIBLE
                binding.action.text = label
                binding.action.setOnClickListener { onAction(order, action) }
            }
        }
    }
}
