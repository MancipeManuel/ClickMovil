package co.edu.ucompensar.clickmovil.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.FragmentAdminHomeBinding
import co.edu.ucompensar.clickmovil.ui.LoginActivity

class AdminHomeFragment : Fragment() {
    private var _binding: FragmentAdminHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repository = Repository(requireContext())
        val users = repository.users()
        binding.usersCount.text = "${users.count { it.active }}\nUsuarios"
        binding.productsCount.text = "${repository.products(null, null, null, true).size}\nProductos"
        binding.ordersCount.text = "${repository.allOrders().size}\nPedidos"
        binding.logoutButton.setOnClickListener {
            SessionStore(requireContext()).logout()
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
