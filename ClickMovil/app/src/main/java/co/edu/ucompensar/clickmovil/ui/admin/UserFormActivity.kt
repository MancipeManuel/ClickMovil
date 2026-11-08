package co.edu.ucompensar.clickmovil.ui.admin

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivityUserFormBinding
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.model.Role
import co.edu.ucompensar.clickmovil.util.bindBackToolbar
import co.edu.ucompensar.clickmovil.util.toast

class UserFormActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserFormBinding
    private var userId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actor = SessionStore(this).currentUser()
        if (actor == null || actor.role != Role.ADMIN) {
            finish()
            return
        }
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val roles = Role.entries
        binding.role.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles.map { it.label })
        val incoming = intent.getLongExtra(EXTRA_ID, -1L)
        bindBackToolbar(
            binding.toolbar.root,
            getString(if (incoming > 0) R.string.edit_user else R.string.create_user)
        )
        if (incoming > 0) {
            val user = Repository(this).userById(incoming)
            if (user == null) {
                toast(getString(R.string.empty_orders))
                finish()
                return
            }
            userId = user.id
            binding.name.setText(user.name)
            binding.email.setText(user.email)
            binding.phone.setText(user.phone)
            binding.city.setText(user.city)
            binding.role.setSelection(roles.indexOf(user.role).coerceAtLeast(0))
        }
        binding.saveButton.setOnClickListener { save(actor.id, roles) }
    }

    private fun save(actorId: Long, roles: List<Role>) {
        when (
            val result = Repository(this).saveManagedUser(
                actorId = actorId,
                userId = userId,
                name = binding.name.text?.toString().orEmpty(),
                email = binding.email.text?.toString().orEmpty(),
                password = binding.password.text?.toString().orEmpty(),
                phone = binding.phone.text?.toString().orEmpty(),
                city = binding.city.text?.toString().orEmpty(),
                role = roles[binding.role.selectedItemPosition]
            )
        ) {
            is OpResult.Ok -> {
                toast(getString(R.string.save))
                finish()
            }
            is OpResult.Err -> toast(result.message)
        }
    }

    companion object {
        const val EXTRA_ID = "user_id"
    }
}
