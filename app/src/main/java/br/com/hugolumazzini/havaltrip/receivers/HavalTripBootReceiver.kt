package br.com.hugolumazzini.havaltrip.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import br.com.hugolumazzini.havaltrip.ServicoDeBordo

class HavalTripBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HavalTripBootReceiver"
        private const val ACTION_RETRY_START = "br.com.hugolumazzini.havaltrip.action.RETRY_START"
        private const val EXTRA_ATTEMPT = "attempt"

        // Backoff progressivo: 5s, 15s, 45s
        // Não tão agressivo quanto antes (3s/10s/30s) para dar tempo da central
        // estabilizar displays, Shizuku e outros serviços do sistema no boot.
        private val RETRY_BACKOFF_MS = longArrayOf(5000, 15000, 45000)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val bootAction = intent.action
        val startTime = SystemClock.elapsedRealtime()

        Log.d(TAG, "Boot event received: $bootAction")

        // Verifica se é evento de boot válido
        val isBootEvent = bootAction in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        val isRetry = bootAction == ACTION_RETRY_START

        if (!isBootEvent && !isRetry) {
            Log.w(TAG, "Ignoring unexpected action: $bootAction")
            return
        }

        // Pega o número da tentativa
        val attempt = if (isRetry) {
            intent.getIntExtra(EXTRA_ATTEMPT, 0)
        } else {
            0
        }

        Log.i(TAG, "Starting Haval Trip (attempt=$attempt, action=$bootAction)")

        // Tenta iniciar o serviço
        tryStartService(context, bootAction ?: "", attempt, startTime)
    }

    private fun tryStartService(
        context: Context,
        action: String,
        attempt: Int,
        startTime: Long
    ) {
        try {
            // Inicia o ServicoDeBordo (ForegroundService)
            ServicoDeBordo.garantir(context)

            val duration = SystemClock.elapsedRealtime() - startTime
            Log.i(TAG, "Service started successfully (attempt=$attempt, duration=${duration}ms)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service (attempt=$attempt)", e)
            scheduleRetry(context, attempt)
        }
    }

    private fun scheduleRetry(context: Context, currentAttempt: Int) {
        if (currentAttempt >= RETRY_BACKOFF_MS.size) {
            Log.e(TAG, "Giving up after ${RETRY_BACKOFF_MS.size} retries")
            return
        }

        val delayMs = RETRY_BACKOFF_MS[currentAttempt]
        val nextAttempt = currentAttempt + 1

        val retryIntent = Intent(context, HavalTripBootReceiver::class.java).apply {
            action = ACTION_RETRY_START
            putExtra(EXTRA_ATTEMPT, nextAttempt)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            nextAttempt,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager not available, cannot schedule retry")
            return
        }

        // A partir do Android 12, precisa verificar se pode agendar alarmes exatos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.e(TAG, "Cannot schedule exact alarms, permission not granted")
            return
        }

        val triggerAtMs = System.currentTimeMillis() + delayMs
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent
            )
            Log.d(TAG, "Retry #$nextAttempt scheduled in ${delayMs/1000}s")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when scheduling exact alarm", e)
        }
    }
}
