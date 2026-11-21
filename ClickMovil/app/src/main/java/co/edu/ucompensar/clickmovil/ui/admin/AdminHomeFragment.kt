package co.edu.ucompensar.clickmovil.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.FragmentAdminHomeBinding
import co.edu.ucompensar.clickmovil.ui.HomeActivity
import co.edu.ucompensar.clickmovil.ui.LoginActivity
import co.edu.ucompensar.clickmovil.util.Dates
import co.edu.ucompensar.clickmovil.util.Money
import kotlinx.coroutines.launch

/** Panel del administrador: totales, accesos a cada CRUD y pedidos recientes. */
class AdminHomeFragment : Fragment() {
    private var _binding: FragmentAdminHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val session = SessionStore(requireContext())
        val user = session.currentUser()
        binding.greeting.text = getString(R.string.home_greeting, user?.name ?: "Admin")
        binding.openUsers.setOnClickListener { (activity as? HomeActivity)?.openTab(R.id.nav_users) }
        binding.openProducts.setOnClickListener { (activity as? HomeActivity)?.openTab(R.id.nav_admin_products) }
        binding.openReport.setOnClickListener { (activity as? HomeActivity)?.openTab(R.id.nav_admin_orders) }
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = Repository(requireContext())
            val users = repository.users()
            val totals = repository.salesTotals()
            val orders = repository.allOrders()
            if (_binding == null) return@launch
            binding.usersCount.text = "${users.count { it.active }}\nUsuarios"
            binding.productsCount.text = "${repository.products(null, null, null, true).size}\nProductos"
            binding.ordersCount.text = "${orders.size}\nPedidos"
            binding.revenue.text = Money.format(totals.revenue)
            binding.revenueDetail.text =
                "${totals.paid} pagados · ${totals.shipped} enviados · ${totals.delivered} entregados"
            binding.recentOrders.text = if (orders.isEmpty()) {
                getString(R.string.admin_no_orders)
            } else {
                orders.take(4).joinToString("\n\n") { order ->
                    "${order.code} · ${order.status}\n${order.buyerName} · ${Money.format(order.total)} · ${Dates.format(order.createdAt)}"
                }
            }
        }
        binding.logoutButton.setOnClickListener {
            session.logout()
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
