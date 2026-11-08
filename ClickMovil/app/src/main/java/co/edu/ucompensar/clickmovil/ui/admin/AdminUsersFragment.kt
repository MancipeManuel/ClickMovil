package co.edu.ucompensar.clickmovil.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.FragmentUsersBinding
import co.edu.ucompensar.clickmovil.databinding.ItemUserBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.model.Role
import co.edu.ucompensar.clickmovil.model.User
import co.edu.ucompensar.clickmovil.util.toast

class AdminUsersFragment : Fragment() {
    private var _binding: FragmentUsersBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { Repository(requireContext()) }
    private val session by lazy { SessionStore(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.createButton.setOnClickListener {
            startActivity(Intent(requireContext(), UserFormActivity::class.java))
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) reload()
    }

    private fun reload() {
        val actor = session.currentUser() ?: return
        val users = repository.users()
        binding.empty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        binding.list.adapter = UserAdapter(
            users,
            onEdit = { user ->
                startActivity(
                    Intent(requireContext(), UserFormActivity::class.java)
                        .putExtra(UserFormActivity.EXTRA_ID, user.id)
                )
            },
            onRole = { user -> pickRole(actor.id, user) },
            onActive = { user -> toggle(actor.id, user) },
            onDelete = { user -> confirmDelete(actor.id, user) }
        )
    }

    private fun pickRole(actorId: Long, user: User) {
        val labels = Role.entries.map { it.label }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.change_role))
            .setItems(labels) { _, which ->
                when (val result = repository.setRole(actorId, user.id, Role.entries[which])) {
                    is OpResult.Ok -> reload()
                    is OpResult.Err -> requireContext().toast(result.message)
                }
            }
            .show()
    }

    private fun toggle(actorId: Long, user: User) {
        when (val result = repository.setActive(actorId, user.id, !user.active)) {
            is OpResult.Ok -> reload()
            is OpResult.Err -> requireContext().toast(result.message)
        }
    }

    private fun confirmDelete(actorId: Long, user: User) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_user))
            .setMessage(user.name)
            .setPositiveButton(getString(R.string.delete_user)) { _, _ ->
                when (val result = repository.deleteUser(actorId, user.id)) {
                    is OpResult.Ok -> reload()
                    is OpResult.Err -> requireContext().toast(result.message)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private class UserAdapter(
    private val users: List<User>,
    private val onEdit: (User) -> Unit,
    private val onRole: (User) -> Unit,
    private val onActive: (User) -> Unit,
    private val onDelete: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = users.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(users[position])

    inner class Holder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.name.text = user.name
            binding.meta.text = "${user.email}\n${user.role.label} · ${if (user.active) "Activa" else "Inactiva"}"
            binding.roleButton.setText(R.string.change_role)
            binding.activeButton.setText(if (user.active) R.string.deactivate else R.string.activate)
            binding.editButton.setOnClickListener { onEdit(user) }
            binding.roleButton.setOnClickListener { onRole(user) }
            binding.activeButton.setOnClickListener { onActive(user) }
            binding.deleteButton.setOnClickListener { onDelete(user) }
        }
    }
}
