package co.edu.ucompensar.clickmovil.data

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Sube fotos de producto a Firebase Storage.
 * Si Storage no está habilitado (p. ej. plan Blaze pendiente), deja la ruta local
 * para que la app siga mostrando la imagen en el dispositivo de demostración.
 */
class ImageUploader(private val context: Context) {
    private val storage = FirebaseStorage.getInstance()

    /**
     * Sube la foto y devuelve la URL.
     * Si el archivo no existe o Storage rechaza la carga, devuelve la ruta local.
     */
    suspend fun uploadProductImage(localPath: String, productId: String): String {
        val file = File(localPath)
        if (!file.exists()) return localPath
        return try {
            val ref = storage.reference.child("products/$productId/${file.name}")
            ref.putFile(Uri.fromFile(file)).await()
            ref.downloadUrl.await().toString()
        } catch (_: Exception) {
            // Fallback académico: conservar archivo local hasta activar Storage/Blaze.
            localPath
        }
    }
}
