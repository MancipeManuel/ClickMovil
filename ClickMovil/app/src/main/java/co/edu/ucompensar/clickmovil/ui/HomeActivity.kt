package co.edu.ucompensar.clickmovil.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivityHomeBinding
import co.edu.ucompensar.clickmovil.model.Role
import co.edu.ucompensar.clickmovil.ui.admin.AdminHomeFragment
import co.edu.ucompensar.clickmovil.ui.admin.AdminProductsFragment
import co.edu.ucompensar.clickmovil.ui.admin.AdminUsersFragment
import co.edu.ucompensar.clickmovil.ui.admin.SalesReportFragment
import co.edu.ucompensar.clickmovil.ui.buyer.BuyerHomeFragment
import co.edu.ucompensar.clickmovil.ui.buyer.CartFragment
import co.edu.ucompensar.clickmovil.ui.buyer.CatalogFragment
import co.edu.ucompensar.clickmovil.ui.buyer.OrdersFragment
import co.edu.ucompensar.clickmovil.ui.buyer.ProfileFragment
import co.edu.ucompensar.clickmovil.ui.seller.SellerProductsFragment

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private val session by lazy { SessionStore(this) }
    private var rootTabId: Int = 0
    private var currentTabId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = session.currentUser()
        if (user == null) {
            goLogin()
            return
        }
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarSubtitle.text = "${user.name} · ${user.role.label}"
        if (user.role == Role.COMPRADOR) {
            binding.cartShortcut.visibility = View.VISIBLE
            binding.cartShortcut.setOnClickListener { openTab(R.id.nav_cart) }
        }
        val menu = when (user.role) {
            Role.COMPRADOR -> R.menu.menu_buyer
            Role.VENDEDOR -> R.menu.menu_seller
            Role.ADMIN -> R.menu.menu_admin
        }
        binding.bottom.menu.clear()
        binding.bottom.inflateMenu(menu)
        rootTabId = binding.bottom.menu.getItem(0).itemId
        binding.bottom.setOnItemSelectedListener { item ->
            currentTabId = item.itemId
            binding.toolbarTitle.text = item.title
            updateHeaderChrome()
            show(fragmentFor(item.itemId))
            true
        }
        binding.navBack.setOnClickListener { goRootTab() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentTabId != rootTabId) {
                        goRootTab()
                    } else {
                        finish()
                    }
                }
            }
        )
        if (savedInstanceState == null) {
            binding.bottom.selectedItemId = rootTabId
        }
    }

    fun openTab(itemId: Int) {
        binding.bottom.selectedItemId = itemId
    }

    private fun goRootTab() {
        binding.bottom.selectedItemId = rootTabId
    }

    private fun updateHeaderChrome() {
        val onRoot = currentTabId == rootTabId
        binding.navBack.visibility = if (onRoot) View.GONE else View.VISIBLE
        binding.brandMark.visibility = if (onRoot) View.VISIBLE else View.GONE
    }

    private fun fragmentFor(itemId: Int): Fragment {
        return when (itemId) {
            R.id.nav_home -> BuyerHomeFragment()
            R.id.nav_catalog -> CatalogFragment()
            R.id.nav_cart -> CartFragment()
            R.id.nav_orders -> OrdersFragment.newBuyer()
            R.id.nav_profile -> ProfileFragment()
            R.id.nav_products -> SellerProductsFragment()
            R.id.nav_sales -> OrdersFragment.newSeller()
            R.id.nav_admin_home -> AdminHomeFragment()
            R.id.nav_users -> AdminUsersFragment()
            R.id.nav_admin_products -> AdminProductsFragment()
            R.id.nav_admin_orders -> SalesReportFragment()
            else -> ProfileFragment()
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    private fun goLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
