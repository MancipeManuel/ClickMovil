package co.edu.ucompensar.clickmovil.ui.seller

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivityProductFormBinding
import co.edu.ucompensar.clickmovil.model.Categories
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.model.Role
import co.edu.ucompensar.clickmovil.R
import co.edu.ucompensar.clickmovil.util.bindBackToolbar
import co.edu.ucompensar.clickmovil.util.copyImageToApp
import co.edu.ucompensar.clickmovil.util.decodeSampled
import co.edu.ucompensar.clickmovil.util.newCameraFile
import co.edu.ucompensar.clickmovil.util.toast
import java.io.File

class ProductFormActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductFormBinding
    private var productId: Long? = null
    private var photoPath: String? = null
    private var pendingCamera: File? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            photoPath = pendingCamera?.absolutePath
            showPhoto()
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            photoPath = copyImageToApp(this, uri)
            showPhoto()
        }
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else toast("Sin permiso de cámara puedes elegir una foto de la galería.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = SessionStore(this).currentUser()
        if (user == null || user.role == Role.COMPRADOR) {
            toast("No tienes permiso para publicar.")
            finish()
            return
        }
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.category.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Categories.all)
        val incoming = intent.getLongExtra(EXTRA_ID, -1L)
        bindBackToolbar(
            binding.toolbar.root,
            if (incoming > 0) getString(R.string.products) else getString(R.string.new_product)
        )
        if (incoming > 0) {
            val product = Repository(this).product(incoming)
            if (product == null) {
                toast("Producto no encontrado.")
                finish()
                return
            }
            if (user.role == Role.VENDEDOR && product.sellerId != user.id) {
                toast("No puedes editar productos de otro vendedor.")
                finish()
                return
            }
            productId = product.id
            photoPath = product.imagePath
            binding.name.setText(product.name)
            binding.description.setText(product.description)
            binding.price.setText(product.price.toString())
            binding.stock.setText(product.stock.toString())
            binding.compatibility.setText(product.compatibility)
            val index = Categories.all.indexOf(product.category).coerceAtLeast(0)
            binding.category.setSelection(index)
            binding.saveButton.text = "Guardar cambios"
            binding.retireButton.visibility = View.VISIBLE
            showPhoto()
        }
        binding.cameraButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cámara")
                .setMessage("La foto se usa solo para la publicación del producto.")
                .setPositiveButton("Continuar") { _, _ ->
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        binding.galleryButton.setOnClickListener { pickImage.launch("image/*") }
        binding.saveButton.setOnClickListener { save() }
        binding.retireButton.setOnClickListener { retire() }
    }

    private fun openCamera() {
        val file = newCameraFile(this)
        pendingCamera = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        takePicture.launch(uri)
    }

    private fun showPhoto() {
        val bitmap = photoPath?.let { decodeSampled(it, 800) }
        if (bitmap != null) {
            binding.photo.setImageBitmap(bitmap)
            binding.photo.visibility = View.VISIBLE
            binding.fallback.visibility = View.GONE
        }
    }

    private fun save() {
        val user = SessionStore(this).currentUser() ?: return
        val price = binding.price.text?.toString()?.toLongOrNull()
        val stock = binding.stock.text?.toString()?.toIntOrNull()
        if (price == null || stock == null) {
            toast("Revisa el precio y las existencias.")
            return
        }
        when (
            val result = Repository(this).saveProduct(
                actor = user,
                productId = productId,
                name = binding.name.text?.toString().orEmpty(),
                description = binding.description.text?.toString().orEmpty(),
                price = price,
                category = binding.category.selectedItem.toString(),
                compatibility = binding.compatibility.text?.toString().orEmpty(),
                stock = stock,
                imagePath = photoPath
            )
        ) {
            is OpResult.Ok -> {
                toast("Publicación guardada.")
                finish()
            }
            is OpResult.Err -> toast(result.message)
        }
    }

    private fun retire() {
        val user = SessionStore(this).currentUser() ?: return
        val id = productId ?: return
        when (val result = Repository(this).retireProduct(user, id)) {
            is OpResult.Ok -> {
                toast("Publicación retirada. Los pedidos anteriores se conservan.")
                finish()
            }
            is OpResult.Err -> toast(result.message)
        }
    }

    companion object {
        const val EXTRA_ID = "product_id"
    }
}
