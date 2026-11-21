package co.edu.ucompensar.clickmovil.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Lee la ubicación actual con Fused Location y obtiene la ciudad (geocoder).
 */
object LocationHelper {
    @SuppressLint("MissingPermission")
    /** Obtiene la ciudad actual. Devuelve nulo si no hay permiso, señal o geocodificación. */
    suspend fun resolveCity(context: Context): String? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = try {
            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).await()
                ?: client.lastLocation.await()
        } catch (_: Exception) {
            null
        } ?: return null
        return suspendCancellableCoroutine { cont ->
            try {
                @Suppress("DEPRECATION")
                val city = Geocoder(context, Locale("es", "CO"))
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.locality
                cont.resume(city)
            } catch (_: Exception) {
                cont.resume(null)
            }
        }
    }
}
