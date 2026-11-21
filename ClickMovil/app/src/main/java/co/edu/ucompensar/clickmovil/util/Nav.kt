package co.edu.ucompensar.clickmovil.util

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** Asigna título y acción de volver en las pantallas que no usan la barra de inicio. */
fun AppCompatActivity.bindBackToolbar(toolbar: View, title: CharSequence) {
    val bar = toolbar as MaterialToolbar
    bar.title = title
    bar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
}
