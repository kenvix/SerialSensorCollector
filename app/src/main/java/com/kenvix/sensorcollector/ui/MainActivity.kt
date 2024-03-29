package com.kenvix.sensorcollector.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android_serialport_api.SerialPortFinder
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.felhr.usbserial.UsbSerialDevice
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.kenvix.sensorcollector.R
import com.kenvix.sensorcollector.databinding.ActivityMainBinding
import com.kenvix.sensorcollector.hardware.vendor.SensorDataParser
import com.kenvix.sensorcollector.hardware.vendor.WitHardwareDataParser
import com.kenvix.sensorcollector.services.UsbSerial
import com.kenvix.sensorcollector.services.UsbSerialRecorderService
import com.kenvix.sensorcollector.utils.toArray
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity :
    AppCompatActivity(),
    CoroutineScope by CoroutineScope(CoroutineName("MainActivity") + Dispatchers.Main) {
    private var workingDialog: AlertDialog? = null
    private lateinit var powerManager: PowerManager
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    internal val serialFinder: SerialPortFinder by lazy { SerialPortFinder() }

    private var service: UsbSerialRecorderService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as UsbSerialRecorderService.LocalBinder
            this@MainActivity.service = binder.getService()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.i("MainActivity", "UsbSerialRecorderService Service disconnected")
            this@MainActivity.service = null
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.kenvix.sensorcollector.ACTION_WORKER_SERVICE_STARTED" ->
                    showProgressDialogIfRecordingNow()

                "com.kenvix.sensorcollector.ACTION_WORKER_SERVICE_STOPPED" ->
                    dismissProgressDialogIfRecordingNow()

                "com.kenvix.sensorcollector.ACTION_WORKER_SERVICE_FAILED" -> {
                    val msg = intent.getStringExtra("msg")
                    showAlertDialogIfRecordingFailed(msg)
                }
            }
        }
    }

    internal val dataParser: SensorDataParser = WitHardwareDataParser()

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        UsbSerial.init(sysContext = applicationContext)
        UsbSerial.registerUsbReceiver(uiContext = this)
        powerManager = applicationContext.getSystemService(POWER_SERVICE) as PowerManager

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Written by Kenvix <i@kenvix.com>.\nLicensed under GPLv3 license.", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }

        acquirePermissions()
        bindUsbSerialWorkerService()

        showProgressDialogIfRecordingNow()
        dismissProgressDialogIfRecordingNow()

        val filter = IntentFilter().apply {
            addAction("com.kenvix.sensorcollector.ACTION_WORKER_SERVICE_STARTED")
            addAction("com.kenvix.sensorcollector.ACTION_WORKER_SERVICE_FAILED")
            addAction("com.kenvix.sensorcollector.ACTION_WORKER_SERVICE_STOPPED")
        }


        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter)
        } else {
            registerReceiver(broadcastReceiver, filter, RECEIVER_EXPORTED)
        }
    }

    override fun onStart() {
        super.onStart()
    }

    fun showProgressDialogIfRecordingNow() {
        if (service?.isRecording == true && (workingDialog == null || !workingDialog!!.isShowing)) {
            workingDialog = AlertDialog.Builder(this)
                .setTitle("Recording")
                .setCancelable(false)
                .setMessage(getString(R.string.record_activity_info_detailed,
                    service?.uri.toString(),
                    UsbSerial.selectedDevices.joinToString("\n") { it.deviceName }))
                .setNegativeButton("Stop") { dialog, _ ->
                    launch(Dispatchers.Main) {
                        service?.tryStopService()
                        workingDialog?.dismiss()
                        workingDialog = AlertDialog.Builder(this@MainActivity)
                            .setTitle("Stopping")
                            .setMessage(getString(R.string.record_service_channel_stopping, service?.uri.toString()))
                            .setCancelable(false)
                            .create()

                        if (service?.isRecording != true) {
                            workingDialog?.dismiss()
                            workingDialog = null
                        } else {
                            workingDialog?.show()
                        }
                    }
                }.show()
        }
    }

    fun showAlertDialogIfRecordingFailed(msg: String?) {
        launch(Dispatchers.Main) {
            service?.tryStopService()
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Recording Failed")
                .setMessage(msg)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    fun dismissProgressDialogIfRecordingNow() {
        if (workingDialog != null && service?.isRecording != true) {
            workingDialog?.dismiss()
            workingDialog = null
        }
    }

    private fun bindUsbSerialWorkerService() {
        if (service == null) {
            // Create a reference to the foreground service instance
            val serviceIntent = Intent(this, UsbSerialRecorderService::class.java)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            R.id.show_available_drivers -> {
                true
            }

            R.id.action_test_device -> {
                val grantedNum = UsbSerial.selectedDevices
                    .asSequence()
                    .map { if (UsbSerialDevice.isSupported(it)) 1 else 0 }
                    .sum()
                Snackbar.make(
                    binding.root,
                    "Found ${UsbSerial.selectedDevices.size} devices, $grantedNum supported",
                    Snackbar.LENGTH_LONG
                )
                    .setAction("Action", null)
                    .setAnchorView(R.id.fab).show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun isPermissionsGranted(): Boolean {
        val permsA = sequenceOf(
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION),
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION),
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN),
            ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE),
            ContextCompat.checkSelfPermission(this, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),
            ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS),
        ).all { it == PackageManager.PERMISSION_GRANTED }

        var permsB = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permsB = sequenceOf(
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT),
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN),
                ContextCompat.checkSelfPermission(this, Manifest.permission.HIGH_SAMPLING_RATE_SENSORS),
            ).all { it == PackageManager.PERMISSION_GRANTED }
        }

        val powerPerm = powerManager.isIgnoringBatteryOptimizations(packageName)

        return permsB && permsA && powerPerm
    }

    @SuppressLint("BatteryLife")
    private fun acquirePermissions() {
        if (!isPermissionsGranted()) {
            var perms = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Manifest.permission.BODY_SENSORS,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms = perms.plus(sequenceOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
                ))
            }

            requestPermissions(perms.toTypedArray(), 0)

            /////////// Power Optimization Exemption ///////////
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val i = Intent()

                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    i.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    i.setData(Uri.parse("package:$packageName"))
                    startActivity(i)
                }
            }
            ///////////////////////////////////////////////////
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!isPermissionsGranted()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.permissions_required))
                .setMessage(getString(R.string.permissions_not_granted))
                .setCancelable(false)
                .setNegativeButton("Ignore") { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton("OK. Try again") { dialog, _ ->
                    dialog.dismiss()
                    acquirePermissions()
                }.show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }
}
