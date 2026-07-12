package com.example.foamdartbattle.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) return
        
        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e("GeofenceReceiver", errorMessage)
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences
            Log.d("GeofenceReceiver", "Transition \$geofenceTransition triggered for \$triggeringGeofences")
            
            val transitionString = if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) "entered" else "exited"
            Toast.makeText(context, "You \$transitionString the battle zone!", Toast.LENGTH_LONG).show()
        } else {
            Log.e("GeofenceReceiver", "Invalid transition type: \$geofenceTransition")
        }
    }
}
