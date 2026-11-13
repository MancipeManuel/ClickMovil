package co.edu.ucompensar.clickmovil.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.databinding.FragmentListBinding
import co.edu.ucompensar.clickmovil.ui.ProductAdapter
import co.edu.ucompensar.clickmovil.ui.seller.ProductFormActivity
import kotlinx.coroutines.launch

class AdminProductsFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = ProductAdapter { product ->
        startActivity(
            Intent(requireContext(), ProductFormActivity::class.java)
                .putExtra(ProductFormActivity.EXTRA_ID, product.id)
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        reload()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) reload()
    }

    private fun reload() {
        viewLifecycleOwner.lifecycleScope.launch {
            val products = Repository(requireContext()).products(null, null, null, includeInactive = true)
            if (_binding == null) return@launch
            adapter.submit(products)
            binding.empty.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
            binding.empty.text = "No hay productos."
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
