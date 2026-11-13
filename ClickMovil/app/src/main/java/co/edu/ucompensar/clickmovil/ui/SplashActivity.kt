package co.edu.ucompensar.clickmovil.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.edu.ucompensar.clickmovil.data.Repository
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).root)
        lifecycleScope.launch {
            // Siembra cuentas demo + catálogo en Firestore (una sola vez).
            runCatching { Repository(this@SplashActivity).ensureDemoData() }
            delay(1200)
            if (isFinishing) return@launch
            val destination = if (SessionStore(this@SplashActivity).currentUser() != null) {
                HomeActivity::class.java
            } else {
                LoginActivity::class.java
            }
            startActivity(
                Intent(this@SplashActivity, destination).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }
}
