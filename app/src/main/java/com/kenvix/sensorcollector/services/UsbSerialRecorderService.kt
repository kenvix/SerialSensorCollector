//--------------------------------------------------
// Class UsbSerialRecorderService
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.kenvix.sensorcollector.R
import com.kenvix.sensorcollector.hardware.vendor.SensorDataParser
import com.kenvix.sensorcollector.ui.MainActivity
import com.kenvix.sensorcollector.utils.CsvRecordWriter
import com.kenvix.sensorcollector.utils.ExcelRecordWriter
import com.kenvix.sensorcollector.utils.RecordWriter
import com.kenvix.sensorcollector.utils.getFileSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class UsbSerialRecorderService :
    Service(),
    CoroutineScope by CoroutineScope(CoroutineName("UsbSerialRecorderService")) {

    private lateinit var notificationManager: NotificationManager
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock

    var uri: Uri? = null
    private var outputFormat: String = "xlsx"
    var recordWriter: RecordWriter? = null
        private set
    private var dataParser: SensorDataParser? = null

    // Binder given to clients
    private val binder = LocalBinder()
    private var workerJob: Job? = null
    val rowsWrittenTotal: Long
        get() = recordWriter?.rowsWrittenTotal ?: 0
    val rowsWrittenPerDevice: Map<UsbDevice, Long>
        get() = recordWriter?.rowsWrittenPerDevice ?: emptyMap()

    @Volatile
    var isRecording: Boolean = false
        private set

    private val opMutex = Mutex()

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

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d("UsbSerialRecorderService", "Service starting")
        // for android <13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uri = intent.getParcelableExtra("uri", Uri::class.java)
            dataParser =
                intent.getSerializableExtra("parser", SensorDataParser::class.java)
        } else {
            uri = intent.getParcelableExtra("uri")
            dataParser = intent.getSerializableExtra("parser") as SensorDataParser
        }
        outputFormat = intent.getStringExtra("output_format") ?: "xlsx"

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.record_service_channel_name))
            .setContentText(getString(R.string.record_service_channel_info, uri.toString()))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification)
        wakeLock.acquire(365 * 24 * 60 * 1000L)

        startWorking()

        return START_STICKY
    }

    private fun startWorking() {
        if (isRecording) return

        isRecording = true
        val startIntent = Intent("com.kenvix.sensorcollector.ACTION_WORKER_SERVICE_STARTED")
        sendBroadcast(startIntent)

        workerJob = launch(Dispatchers.Main) {
            try {
                this@UsbSerialRecorderService.recordWriter = withContext(Dispatchers.IO) {
                    when (outputFormat) {
                        "xlsx" -> ExcelRecordWriter(context = this@UsbSerialRecorderService, uri!!)
                        "csv" -> CsvRecordWriter(context = this@UsbSerialRecorderService, uri!!)
                        else -> throw IllegalArgumentException("Invalid output format: $outputFormat")
                    }
                }

                withContext(Dispatchers.IO) {
                    recordWriter!!.also { writer ->
                        writer.setDeviceList(UsbSerial.selectedDevices)
                        UsbSerial.startReceivingAllAndWait(
                            dataParser!!,
                            writer,
                            dataParser!!.packetHeader
                        ) { device, serial, data ->
                            writer.onSensorDataReceived(device, serial, data)
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.i("UsbSerialRecorderService", "Worker job stopped (canceled)")
                throw e
            } catch (e: Throwable) {
                Log.e("UsbSerialRecorderService", "Error while recording", e)
                Toast.makeText(
                    this@UsbSerialRecorderService,
                    "<!> ERROR while recording: $e", Toast.LENGTH_LONG
                ).show()

                val errorIntent = Intent("com.kenvix.sensorcollector.ACTION_WORKER_SERVICE_FAILED")
                errorIntent.putExtra("msg", e.toString())
                sendBroadcast(errorIntent)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    suspend fun tryStopService() {
        if (!isRecording) return
        opMutex.withLock {
            if (!isRecording) return
            Log.i("UsbSerialRecorderService", "Stopping recording [1/6]: Service stopping (invoker request)")

            // 创建更新的通知内容为“正在保存”
            val updatedNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // 设置一个小图标
                .setContentTitle(getString(R.string.record_service_channel_name))
                .setContentText(getString(R.string.record_service_channel_stopping, uri.toString()))
                .build()

            // 使用相同的NOTIFICATION_ID更新通知
            notificationManager.notify(NOTIFICATION_ID, updatedNotification)

            try {
                workerJob?.cancel()
                withContext(Dispatchers.IO) {
                    // 执行阻塞操作
                    Log.d("UsbSerialRecorderService", "Stopping recording [2/6]: Closing Serial")
                    UsbSerial.stopAllSerial()
                    Log.d("UsbSerialRecorderService", "Stopping recording [3/6]: Waiting for USB Receiver to finish")
                    workerJob?.join()
                    Log.d("UsbSerialRecorderService", "Stopping recording [4/6]: Waiting for USB Receiver to finish")
                    recordWriter?.close()
                    Log.d("UsbSerialRecorderService", "Stopping recording [5/6]: Cleaning")

                    if (outputFormat == "xlsx") {
                        this@UsbSerialRecorderService.getFileSize(uri!!).also {
                            Log.d("UsbSerialRecorderService", "Recording saved to $uri, size: $it")
                            if (it < 1024) {
                                Log.i(
                                    "UsbSerialRecorderService",
                                    "Recording size is too small, deleting"
                                )
                                runCatching {
                                    contentResolver.delete(uri!!, null, null)
                                }.onFailure { e ->
                                    Log.e(
                                        "UsbSerialRecorderService",
                                        "Error while deleting recording",
                                        e
                                    )
                                }
                            }
                        }
                    }
                }

                Toast.makeText(
                    this@UsbSerialRecorderService,
                    "Recording successfully saved to $uri", Toast.LENGTH_LONG
                ).show()

                System.gc()
            } catch (e: Exception) {
                Log.e("UsbSerialRecorderService", "Error while saving recordings", e)
                Toast.makeText(
                    this@UsbSerialRecorderService,
                    "<!> ERROR while saving recordings: $e", Toast.LENGTH_LONG
                ).show()
            } finally {
                if (wakeLock.isHeld)
                    wakeLock.release()

                workerJob = null
                recordWriter = null
                isRecording = false
                val intent = Intent("com.kenvix.sensorcollector.ACTION_WORKER_SERVICE_STOPPED")
                sendBroadcast(intent)

                Log.i("UsbSerialRecorderService", "Stopping recording [5/6]: Service stopped. Invoking stopSelf")
                stopSelf()
            }
        }

        Log.d("UsbSerialRecorderService", "Stopping recording [6/6]: Lock released")
    }

    override fun onDestroy() {
        Log.d("UsbSerialRecorderService", "Service destroying")
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
