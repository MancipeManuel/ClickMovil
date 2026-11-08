package co.edu.ucompensar.clickmovil.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.databinding.FragmentSalesReportBinding
import co.edu.ucompensar.clickmovil.model.StoreOrder
import co.edu.ucompensar.clickmovil.util.Dates
import co.edu.ucompensar.clickmovil.util.Money

class SalesReportFragment : Fragment() {
    private var _binding: FragmentSalesReportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSalesReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repository = Repository(requireContext())
        val totals = repository.salesTotals()
        binding.revenue.text = getString(R.string.revenue) + "\n" + Money.format(totals.revenue)
        binding.paid.text = "${totals.paid}\n${getString(R.string.paid)}"
        binding.shipped.text = "${totals.shipped}\n${getString(R.string.shipped)}"
        binding.delivered.text = "${totals.delivered}\n${getString(R.string.delivered)}"
        binding.cancelled.text = "${totals.cancelled}\n${getString(R.string.cancelled)}"
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = ReportAdapter(repository.allOrders())
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private class ReportAdapter(private val orders: List<StoreOrder>) : RecyclerView.Adapter<ReportAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_order, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = orders.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(orders[position])

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val code: TextView = view.findViewById(R.id.code)
        private val status: TextView = view.findViewById(R.id.status)
        private val meta: TextView = view.findViewById(R.id.meta)
        fun bind(order: StoreOrder) {
            code.text = order.code
            status.text = order.status
            val items = order.items.joinToString { "${it.productName} x${it.quantity}" }
            meta.text = "${Dates.format(order.createdAt)} · ${Money.format(order.total)}\n$items\n${order.buyerName}"
            itemView.findViewById<View>(R.id.action).visibility = View.GONE
        }
    }
}
