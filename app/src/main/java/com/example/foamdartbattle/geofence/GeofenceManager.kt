package com.example.foamdartbattle.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng

class GeofenceManager(private val context: Context) {
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    @SuppressLint("MissingPermission")
    fun addGeofence(center: LatLng, radiusInMeters: Float, geofenceId: String = "BATTLE_ZONE") {
        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(center.latitude, center.longitude, radiusInMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)?.run {
            addOnSuccessListener {
                Log.d("GeofenceManager", "Geofence added successfully")
            }
            addOnFailureListener {
                Log.e("GeofenceManager", "Failed to add geofence", it)
            }
        }
    }
    
    fun removeGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent)?.run {
            addOnSuccessListener {
                 Log.d("GeofenceManager", "Geofence removed successfully")
            }
            addOnFailureListener {
                Log.e("GeofenceManager", "Failed to remove geofence", it)
            }
        }
    }
    
    fun calculateCenter(points: List<LatLng>): LatLng {
        var lat = 0.0
        var lng = 0.0
        points.forEach { 
            lat += it.latitude
            lng += it.longitude
        }
        return LatLng(lat / points.size, lng / points.size)
    }

    fun calculateRadius(center: LatLng, points: List<LatLng>): Float {
        val results = FloatArray(1)
        var maxDistance = 0f
        for (point in points) {
            android.location.Location.distanceBetween(
                center.latitude, center.longitude,
                point.latitude, point.longitude,
                results
            )
            if (results[0] > maxDistance) {
                maxDistance = results[0]
            }
        }
        return maxDistance
    }
}
