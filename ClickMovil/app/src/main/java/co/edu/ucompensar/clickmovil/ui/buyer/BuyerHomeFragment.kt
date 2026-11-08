package co.edu.ucompensar.clickmovil.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.FragmentBuyerHomeBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.ui.FeaturedProductAdapter
import co.edu.ucompensar.clickmovil.ui.HomeActivity
import co.edu.ucompensar.clickmovil.util.toast

class BuyerHomeFragment : Fragment() {
    private var _binding: FragmentBuyerHomeBinding? = null
    private val binding get() = _binding!!
    private val adapter = FeaturedProductAdapter(
        onOpen = { product ->
            startActivity(
                Intent(requireContext(), ProductDetailActivity::class.java)
                    .putExtra(ProductDetailActivity.EXTRA_ID, product.id)
            )
        },
        onAdd = { product ->
            val user = SessionStore(requireContext()).currentUser() ?: return@FeaturedProductAdapter
            when (val result = Repository(requireContext()).addToCart(user.id, product.id, 1)) {
                is OpResult.Ok -> requireContext().toast("Agregado al carrito")
                is OpResult.Err -> requireContext().toast(result.message)
            }
        }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBuyerHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val user = SessionStore(requireContext()).currentUser()
        binding.greeting.text = getString(R.string.home_greeting, user?.name ?: "")
        binding.featured.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.featured.adapter = adapter
        val openCatalog = { (activity as? HomeActivity)?.openTab(R.id.nav_catalog) }
        val openOrders = { (activity as? HomeActivity)?.openTab(R.id.nav_orders) }
        binding.openCatalog.setOnClickListener { openCatalog() }
        binding.openOrders.setOnClickListener { openOrders() }
        binding.seeAll.setOnClickListener { openCatalog() }
        reload()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) reload()
    }

    private fun reload() {
        val repository = Repository(requireContext())
        val products = repository.products(null, null, null, includeInactive = false)
        binding.catalogCount.text = getString(R.string.catalog_count, products.size)
        val userId = SessionStore(requireContext()).currentUser()?.id
        val moving = if (userId == null) {
            0
        } else {
            repository.ordersForBuyer(userId).count { it.status == "PAGADO" || it.status == "ENVIADO" }
        }
        binding.ordersHint.text = getString(R.string.orders_on_the_way, moving)
        adapter.submit(products.filter { it.stock > 0 }.take(4))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
