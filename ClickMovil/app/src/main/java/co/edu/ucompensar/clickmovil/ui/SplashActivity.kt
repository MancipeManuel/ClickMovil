package co.edu.ucompensar.clickmovil.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import co.edu.ucompensar.clickmovil.data.SessionStore
import co.edu.ucompensar.clickmovil.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val openNext = Runnable {
        if (isFinishing) return@Runnable
        val destination = if (SessionStore(this).currentUser() != null) {
            HomeActivity::class.java
        } else {
            LoginActivity::class.java
        }
        startActivity(
            Intent(this, destination).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).root)
        handler.postDelayed(openNext, 1800)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openNext)
        super.onDestroy()
    }
}
