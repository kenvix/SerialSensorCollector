//--------------------------------------------------
// Class UsbSerialRecorderService
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kenvix.sensorcollector.R
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsbSerialRecorderService :
    Service(),
    CoroutineScope by CoroutineScope(CoroutineName("UsbSerialRecorderService")) {

    private lateinit var notificationManager: NotificationManager
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock

    // Binder given to clients
    private val binder = LocalBinder()
    var isRecording: Boolean = false
        private set

    // Class used for the client Binder.
    inner class LocalBinder : Binder() {
        // Return this instance of MyService so clients can call public methods
        fun getService(): UsbSerialRecorderService = this@UsbSerialRecorderService
    }

    companion object {
        const val CHANNEL_ID = "UsbSerialRecorderService"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        Log.d("UsbSerialRecorderService", "Service creating")
        super.onCreate()

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "UsbSerialRecorder:ServiceWakeLock"
        )

        // Android O及以上版本需要配置通知渠道
        val name = getString(R.string.record_service_channel_name) // 渠道名称
        val descriptionText = getString(R.string.record_service_channel_name) // 渠道描述
        val importance = NotificationManager.IMPORTANCE_HIGH // 重要性级别
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        // 注册通知渠道
        notificationManager.createNotificationChannel(channel)

        Log.d("UsbSerialRecorderService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("UsbSerialRecorderService", "Service starting")
        // 创建通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.record_service_channel_name))
            .setContentText(getString(R.string.record_service_channel_info))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification)
        wakeLock.acquire(365 * 24 * 60 * 1000L)

        startWorking()

        return START_NOT_STICKY
    }

    private fun startWorking() {
        isRecording = true

        val intent = Intent("com.kenvix.sensorcollector.ACTION_WORKER_SERVICE_STARTED")
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun tryStopService() {
        Log.d("UsbSerialRecorderService", "Service stopping (invoker request)")
        // 创建更新的通知内容为“正在保存”
        val updatedNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 设置一个小图标
            .setContentTitle(getString(R.string.record_service_channel_name))
            .setContentText(getString(R.string.record_service_channel_stopping))
            .build()

        // 使用相同的NOTIFICATION_ID更新通知
        notificationManager.notify(NOTIFICATION_ID, updatedNotification)

        launch {
            withContext(Dispatchers.IO) {
                // 执行阻塞操作

            }

            if (wakeLock.isHeld)
                wakeLock.release()

            val intent = Intent("com.kenvix.sensorcollector.ACTION_WORKER_SERVICE_STOPPED")
            sendBroadcast(intent)
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d("UsbSerialRecorderService", "Service destroying")
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
