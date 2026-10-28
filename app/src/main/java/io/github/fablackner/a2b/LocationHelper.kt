package io.github.fablackner.a2b

import android.app.Activity
import android.content.IntentSender
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

object LocationHelper {

    private const val REQUEST_CHECK_SETTINGS = 102

    /**
     * Checks whether the device's location settings are good enough for the
     * app and, if not, shows the system dialog that lets the user fix them.
     */
    fun ensureLocationEnabled(activity: Activity) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10)
            .setMinUpdateIntervalMillis(10)
            .setMinUpdateDistanceMeters(10f)
            .build()
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()

        LocationServices.getSettingsClient(activity)
            .checkLocationSettings(settingsRequest)
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS)
                    } catch (_: IntentSender.SendIntentException) {
                        // The dialog could not be shown; nothing we can do.
                    }
                }
            }
    }
}
