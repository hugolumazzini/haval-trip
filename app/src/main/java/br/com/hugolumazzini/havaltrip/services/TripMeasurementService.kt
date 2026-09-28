package br.com.hugolumazzini.havaltrip.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import br.com.hugolumazzini.havaltrip.R
import br.com.hugolumazzini.havaltrip.MotorDeBordo

class TripMeasurementService : Service() {

    companion object {
        private const val TAG = "TripMeasurementService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "haval_trip_channel"
    }

    private var tripStartTime: Long = 0
    private var motor: MotorDeBordo? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")

        tripStartTime = SystemClock.elapsedRealtime()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Inicializa o motor de bordo
        try {
            motor = MotorDeBordo.de(application)
            motor?.pedirTudoAoCarro()
            Log.i(TAG, "MotorDeBordo initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MotorDeBordo", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")
        return START_STICKY // Reinicia automaticamente se morrer
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Haval Trip Medição",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Medição contínua de trip e tempo de viagem"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Haval Trip Ativo")
            .setContentText("Medindo trip e tempo de viagem")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
        motor?.gravarAgora()
    }
}
