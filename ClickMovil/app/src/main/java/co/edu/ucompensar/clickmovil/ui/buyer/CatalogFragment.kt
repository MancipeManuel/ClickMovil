package co.edu.ucompensar.clickmovil.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.FragmentCatalogBinding
import co.edu.ucompensar.clickmovil.model.Categories
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.ui.FeaturedProductAdapter
import co.edu.ucompensar.clickmovil.util.toast
import com.google.android.material.chip.Chip

class CatalogFragment : Fragment() {
    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { Repository(requireContext()) }
    private val adapter = FeaturedProductAdapter(
        onOpen = { product ->
            startActivity(
                Intent(requireContext(), ProductDetailActivity::class.java)
                    .putExtra(ProductDetailActivity.EXTRA_ID, product.id)
            )
        },
        onAdd = { product ->
            val user = SessionStore(requireContext()).currentUser() ?: return@FeaturedProductAdapter
            when (val result = repository.addToCart(user.id, product.id, 1)) {
                is OpResult.Ok -> requireContext().toast("Agregado al carrito")
                is OpResult.Err -> requireContext().toast(result.message)
            }
        }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.list.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.list.adapter = adapter
        val labels = listOf("Todos") + Categories.all
        labels.forEachIndexed { index, label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                id = View.generateViewId()
                isChecked = index == 0
            }
            binding.chips.addView(chip)
        }
        binding.chips.setOnCheckedStateChangeListener { _, _ -> reload() }
        binding.search.doAfterTextChanged { reload() }
        reload()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) reload()
    }

    private fun reload() {
        val selectedId = binding.chips.checkedChipId
        val label = binding.chips.findViewById<Chip>(selectedId)?.text?.toString()
        val category = label?.takeIf { it != "Todos" }
        val query = binding.search.text?.toString()
        val items = repository.products(category, query, sellerId = null, includeInactive = false)
        adapter.submit(items)
        binding.catalogHint.text = if (items.isEmpty()) {
            getString(R.string.empty_catalog)
        } else {
            getString(R.string.catalog_count, items.size)
        }
        binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
