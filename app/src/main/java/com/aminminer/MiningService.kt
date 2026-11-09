package com.aminminer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import java.io.File
import kotlin.concurrent.thread
import java.util.concurrent.TimeUnit

class MiningService : Service() {
    companion object {
        const val ACTION_START = "com.aminminer.action.START"
        const val ACTION_STOP = "com.aminminer.action.STOP"
        const val ACTION_STATUS = "com.aminminer.action.STATUS"
        const val EXTRA_POOL = "pool"
        const val EXTRA_WALLET = "wallet"
        private const val CHANNEL_ID = "amin_mining"
        private const val NOTIFICATION_ID = 4102
    }

    @Volatile private var minerProcess: Process? = null
    @Volatile private var launchInProgress = false
    @Volatile private var monitorThread: Thread? = null
    @Volatile private var stopping = false
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMiner("تم إيقاف التعدين بواسطة المستخدم")
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (minerProcess != null || launchInProgress) return START_NOT_STICKY
                launchInProgress = true
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, notification("جارٍ تجهيز محرك التعدين"))
                val pool = intent.getStringExtra(EXTRA_POOL).orEmpty()
                val wallet = intent.getStringExtra(EXTRA_WALLET).orEmpty()
                startMiner(pool, wallet)
            }
        }
        return START_NOT_STICKY
    }

    private fun startMiner(pool: String, wallet: String) {
        if (!pool.startsWith("stratum+ssl://") || wallet.isBlank()) {
            launchInProgress = false
            stopMiner("بيانات المجمع أو المحفظة غير صحيحة")
            return
        }
        stopping = false
        thread(name = "amin-miner-launch") {
            try {
                val binary = File(applicationInfo.nativeLibraryDir, "libprimo.so")
                val caBundle = File(File(filesDir, "verus-engine").apply { mkdirs() }, "cacert.pem")
                copyAssetIfNeeded("verus-engine/cacert.pem", caBundle)
                if (stopping) return@thread
                if (!binary.isFile || !binary.canExecute()) {
                    stopMiner("تعذر تفعيل صلاحية تشغيل محرك التعدين")
                    return@thread
                }
                if (!caBundle.isFile || caBundle.length() < 100_000L) {
                    stopMiner("ملف شهادات TLS غير متاح؛ تم منع الاتصال غير الآمن")
                    return@thread
                }

                val threads = 1 // Conservative first-run limit for phone thermals.
                val command = listOf(
                    binary.absolutePath,
                    "--algo=verus",
                    "--url=$pool",
                    "--user=$wallet",
                    "--pass=x",
                    "--threads=$threads",
                    "--statsavg=10",
                    "--timeout=20",
                    "--retry-pause=15"
                )
                val builder = ProcessBuilder(command).redirectErrorStream(true)
                builder.environment()["PRIMO_CA_BUNDLE"] = caBundle.absolutePath
                builder.environment()["LD_LIBRARY_PATH"] = applicationInfo.nativeLibraryDir
                if (stopping) return@thread
                val process = builder.start()
                minerProcess = process
                updateStatus("اتصل المحرك وبدأ التشغيل؛ جارٍ انتظار بيانات المعدل")
                monitorSafetyLimits()

                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (stopping) return@forEach
                        val safeLine = line.take(220)
                        if (safeLine.contains("Hashrate", ignoreCase = true) ||
                            safeLine.contains("Accepted", ignoreCase = true) ||
                            safeLine.contains("Rejected", ignoreCase = true) ||
                            safeLine.contains("error", ignoreCase = true) ||
                            safeLine.contains("failed", ignoreCase = true)) {
                            updateStatus(safeLine)
                        }
                    }
                }
                val exitCode = process.waitFor()
                minerProcess = null
                if (!stopping) stopMiner("انتهى المحرك برمز خروج $exitCode")
            } catch (e: Exception) {
                stopMiner("تعذر تشغيل المحرك: ${e.message ?: "خطأ غير معروف"}")
            }
        }
    }

    private fun copyAssetIfNeeded(assetName: String, target: File) {
        if (target.exists() && target.length() > 0L) return
        assets.open(assetName).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun monitorSafetyLimits() {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= 29) {
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
                    stopMiner("إيقاف وقائي: حرارة الجهاز مرتفعة")
                }
            }
            thermalListener = listener
            power.addThermalStatusListener(mainExecutor, listener)
            if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
                stopMiner("إيقاف وقائي: حرارة الجهاز مرتفعة")
                return
            }
        }

        monitorThread = thread(name = "amin-safety-monitor") {
            while (!stopping && minerProcess?.isAlive == true) {
                try {
                    val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                    val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
                    if (percent in 0..20 && !charging) {
                        stopMiner("إيقاف وقائي: البطارية وصلت إلى 20% أو أقل")
                        break
                    }
                    Thread.sleep(15_000)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    Thread.sleep(15_000)
                }
            }
        }
    }

    private fun stopMiner(reason: String) {
        stopping = true
        val processToStop = minerProcess
        try {
            processToStop?.destroy()
            if (processToStop?.isAlive == true) {
                thread(name = "amin-miner-stop") {
                    try {
                        if (processToStop.waitFor(3, TimeUnit.SECONDS).not()) processToStop.destroyForcibly()
                    } catch (_: Exception) {
                        processToStop.destroyForcibly()
                    }
                }
            }
        } catch (_: Exception) { }
        minerProcess = null
        if (Build.VERSION.SDK_INT >= 29) {
            thermalListener?.let {
                try { (getSystemService(POWER_SERVICE) as PowerManager).removeThermalStatusListener(it) } catch (_: Exception) { }
            }
        }
        thermalListener = null
        monitorThread?.interrupt()
        monitorThread = null
        updateStatus(reason)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateStatus(message: String) {
        getSharedPreferences("amin_miner", MODE_PRIVATE).edit().putString("status", message).apply()
        val broadcast = Intent(ACTION_STATUS).setPackage(packageName).putExtra("status", message)
        sendBroadcast(broadcast)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "حالة التعدين", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "إشعار دائم أثناء تشغيل محرك التعدين" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun notification(message: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, MiningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Amin Miner • Verus")
            .setContentText(message.take(120))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف", stopIntent)
            .build()
    }

    override fun onDestroy() {
        stopping = true
        minerProcess?.destroy()
        monitorThread?.interrupt()
        if (Build.VERSION.SDK_INT >= 29) {
            thermalListener?.let {
                try { (getSystemService(POWER_SERVICE) as PowerManager).removeThermalStatusListener(it) } catch (_: Exception) { }
            }
        }
        super.onDestroy()
    }
}
