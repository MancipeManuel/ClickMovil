package co.edu.ucompensar.clickmovil.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import java.io.File
import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Password {
    fun hash(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object Money {
    private val format: NumberFormat = NumberFormat.getInstance(Locale("es", "CO")).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    fun format(value: Long): String = "$ ${format.format(value)}"
}

object Dates {
    private val format = SimpleDateFormat("d MMM yyyy", Locale("es", "CO"))
    fun format(millis: Long): String = format.format(Date(millis))
}

fun shippingFor(city: String): Long {
    val normalized = city.trim().lowercase(Locale.ROOT)
    return if (normalized.contains("bogot")) 8_000L else 12_000L
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun decodeSampled(path: String, maxSize: Int = 480): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSize && bounds.outHeight / sample > maxSize) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, opts)
}

fun copyImageToApp(context: Context, source: Uri): String? {
    val dir = File(context.filesDir, "productos").apply { mkdirs() }
    val dest = File(dir, "p_${System.currentTimeMillis()}.jpg")
    return try {
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        dest.absolutePath
    } catch (_: Exception) {
        null
    }
}

fun newCameraFile(context: Context): File {
    val dir = File(context.filesDir, "productos").apply { mkdirs() }
    return File(dir, "p_${System.currentTimeMillis()}.jpg").apply { createNewFile() }
}

fun categoryColor(category: String): Int = when (category) {
    "Fundas" -> 0xFF1D4ED8.toInt()
    "Carga" -> 0xFF0F766E.toInt()
    "Audio" -> 0xFF7C3AED.toInt()
    "Soportes" -> 0xFFB45309.toInt()
    "Energía" -> 0xFF0E7490.toInt()
    else -> 0xFF0B3A6E.toInt()
}
