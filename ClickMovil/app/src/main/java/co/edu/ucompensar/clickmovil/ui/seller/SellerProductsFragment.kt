package co.edu.ucompensar.clickmovil.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.FragmentSellerProductsBinding
import co.edu.ucompensar.clickmovil.ui.ProductAdapter

class SellerProductsFragment : Fragment() {
    private var _binding: FragmentSellerProductsBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { Repository(requireContext()) }
    private val session by lazy { SessionStore(requireContext()) }
    private val adapter = ProductAdapter { product ->
        startActivity(
            Intent(requireContext(), ProductFormActivity::class.java)
                .putExtra(ProductFormActivity.EXTRA_ID, product.id)
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSellerProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.newButton.setOnClickListener {
            startActivity(Intent(requireContext(), ProductFormActivity::class.java))
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) reload()
    }

    private fun reload() {
        val user = session.currentUser() ?: return
        val products = repository.products(null, null, user.id, includeInactive = false)
        val open = repository.ordersForSeller(user.id).count { it.status == "PAGADO" || it.status == "ENVIADO" }
        binding.published.text = "${products.size}\nPublicados"
        binding.openOrders.text = "$open\nPedidos"
        binding.lowStock.text = "${repository.lowStockCount(user.id)}\nStock bajo"
        adapter.submit(products)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
