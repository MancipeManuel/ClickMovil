package co.edu.ucompensar.clickmovil.util

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

fun AppCompatActivity.bindBackToolbar(toolbar: View, title: CharSequence) {
    val bar = toolbar as MaterialToolbar
    bar.title = title
    bar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
}
