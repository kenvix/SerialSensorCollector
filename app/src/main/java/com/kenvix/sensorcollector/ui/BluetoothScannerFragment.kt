package com.kenvix.sensorcollector.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.material.snackbar.Snackbar
import com.kenvix.sensorcollector.R
import com.kenvix.sensorcollector.databinding.FragmentBluetoothScanBinding
import com.kenvix.sensorcollector.utils.ThermometerData
import com.kenvix.sensorcollector.utils.getScanFailureMessage
import com.kenvix.sensorcollector.utils.parserServiceData
import java.util.regex.Pattern

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
@Suppress("SameParameterValue")
class BluetoothScannerFragment : Fragment() {

    private var _binding: FragmentBluetoothScanBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var activity: MainActivity
    private val loggingVeryVerbose = true
    private var isIncludeNullNameDevices = false
    private var isIncludeUnknownDevices = false
    private var bthNamePattern: Pattern? = null
    private val scannedItems = mutableListOf<BluetoothScannerScanResultListItem>()
    private var blePhy: Int = 0
    private lateinit var notificationManager: NotificationManager
    @Volatile private var isEnableAlert: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentBluetoothScanBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity = requireActivity() as MainActivity

        val bleScanner = (requireActivity() as MainActivity)
            .bluetoothManager.adapter.bluetoothLeScanner
        val bleSettings = ScanSettings.Builder()
            .setLegacy(false)
            .setScanMode(
                if (binding.quickScan.isChecked)
                    ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED
            ) // 高频率扫描
            .setReportDelay(if (binding.quickScan.isChecked) 0 else 500)

        binding.bthList.apply {
            adapter = BluetoothScannerListAdapter(requireContext(), scannedItems)
        }
        binding.enableAlert.setOnCheckedChangeListener { buttonView, isChecked ->
            isEnableAlert = isChecked
        }

        /////////////////////////// RH SERVICE TEMPORARY CODE /////////////////////////////
        notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        prepare()
        /////////////////////////// RH SERVICE TEMPORARY CODE END /////////////////////////////

        // Create an ArrayAdapter using the string array and a default spinner layout.
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.ble_phy,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears.
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner.
            binding.blePhy.adapter = adapter
            binding.blePhy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    blePhy = when (position) {
                        0 -> ScanSettings.PHY_LE_ALL_SUPPORTED
                        1 -> BluetoothDevice.PHY_LE_1M
                        2 -> BluetoothDevice.PHY_LE_CODED
                        else -> throw IllegalArgumentException("Invalid position")
                    }

                    bleSettings.setPhy(blePhy)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    blePhy = 0
                }
            }
        }

        binding.clear.setOnClickListener {
            scannedItems.clear()
            updateBthListUI()
        }

        var isScanning = false
        val bleScanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            private fun handleResult(result: ScanResult) {
                if (loggingVeryVerbose) {
                    Log.v(
                        "BluetoothScanner",
                        "Found device: ${result.rssi} ${result.scanRecord?.deviceName} [${result.device.address}] " +
                                "${result.scanRecord?.txPowerLevel} ${result.scanRecord?.manufacturerSpecificData} ${result.scanRecord?.serviceData} ${result.scanRecord?.serviceUuids}"
                    )
                }

                val parsedResults = result.scanRecord?.serviceData?.map { (uuid, bytes) ->
                    parserServiceData(uuid.toString(), bytes)
                }

                val item = BluetoothScannerScanResultListItem(result, parsedResults)

                scannedItems.indexOf(item).let { index ->
                    if (index != -1) {
                        scannedItems[index] = item
                    } else {
                        scannedItems.add(item)
                    }
                }

                if (parsedResults != null)
                    onSensorDataReceived(parsedResults)

                if (loggingVeryVerbose)
                    Log.v("BluetoothScanner", "${item.title}\n${item.body}")
            }

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                // 处理扫描结果
                if (result.scanRecord != null &&
                    (isIncludeNullNameDevices || !result.scanRecord!!.deviceName.isNullOrEmpty())
                ) {
                    if (bthNamePattern == null || bthNamePattern!!.matcher(
                            result.scanRecord?.deviceName ?: ""
                        ).find()
                    ) {
                        handleResult(result)

                        scannedItems.sortDescending()
                        activity.runOnUiThread { updateBthListUI() }
                    }
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                super.onBatchScanResults(results)
                // 批量处理扫描结果
                results
                    .asSequence()
                    .filter {
                        it.scanRecord != null && (isIncludeNullNameDevices || !it.scanRecord!!.deviceName.isNullOrEmpty())
                    }
                    .filter {
                        bthNamePattern == null || bthNamePattern!!.matcher(
                            it.scanRecord?.deviceName ?: ""
                        ).find()
                    }
                    .forEach { handleResult(it) }

                scannedItems.sortDescending()
                activity.runOnUiThread { updateBthListUI() }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                // 处理扫描失败情况
                val errorMessage = getScanFailureMessage(errorCode)
                val errorMessageDetailed = "Scan failed with error code #$errorCode: $errorMessage"
                Log.e("BluetoothScanner", errorMessageDetailed)
                activity.runOnUiThread {
                    Snackbar.make(view, errorMessageDetailed, Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.fab).show()
                }
            }
        }

        binding.buttonBluetoothScan.setOnClickListener {
            withUIOperationDisabled {
                isIncludeNullNameDevices = binding.showUnnamedDevices.isChecked
                isIncludeUnknownDevices = binding.showUnknown.isChecked

                bthNamePattern = if (!binding.bthNameFilter.text.isNullOrBlank()) {
                    Pattern.compile(binding.bthNameFilter.text?.toString() ?: "(.*)")
                } else {
                    null
                }

                if (!isScanning) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        activity.acquirePermissions()
                        return@withUIOperationDisabled
                    }

                    val settings = bleSettings.build()
                    bleScanner.startScan(null, settings, bleScanCallback)
                    binding.buttonBluetoothScan.text = getString(R.string.scan_bluetooth_stop)
                    isScanning = true
                } else {
                    bleScanner.stopScan(bleScanCallback)
                    binding.buttonBluetoothScan.text = getString(R.string.scan_bluetooth)
                    isScanning = false
                }
            }
        }
    }

    private inline fun withUIOperationDisabled(op: () -> Unit) {
        try {
            binding.buttonBluetoothScan.isEnabled = false
            op()
        } finally {
            binding.buttonBluetoothScan.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateBthListUI() {
        (binding.bthList.adapter as ArrayAdapter<*>).notifyDataSetChanged()
    }



    /////////////////////////// RH SERVICE TEMPORARY CODE /////////////////////////////
    fun prepare() {
        createNotificationChannel(
            getString(R.string.temperature_warning_critical_level),
            getString(R.string.temperature_warning_critical_level),
            RH_CHANNEL_ID_0
        )
    }

    private fun createNotificationChannel(name: String, descriptionText: String, channelId: String) {
        // Android O及以上版本需要配置通知渠道
        val importance = NotificationManager.IMPORTANCE_HIGH // 重要性级别
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }

        // 注册通知渠道
        notificationManager.createNotificationChannel(channel)
    }

    fun onSensorDataReceived(results: List<Any?>) {
        results.asSequence().run {
            onRHSensorDataReceived(filter { it is ThermometerData }.map { it as ThermometerData })
        }
    }

    private var rhNotificationShownTime = 0L
    private fun onRHSensorDataReceived(result: Sequence<ThermometerData>) {
        if (isEnableAlert) {
            result.any { it.temperature <= 24.0 }.let { isWarn ->
                if (isWarn) {
                    if (rhNotificationShownTime != 0L && System.currentTimeMillis() - rhNotificationShownTime < 1000 * 7)
                        return

                    notificationManager.cancel(RH_NOTIFICATION_ID_0)

                    val builder =
                        NotificationCompat.Builder(requireContext(), RH_CHANNEL_ID_0).apply {
                            setSmallIcon(R.drawable.ic_launcher_foreground) // 设置通知小图标
                            setContentTitle("传感器：严重警报") // 设置通知标题
                            setContentText("有传感器温度已低于临界值，请立即检查") // 设置通知内容
                            priority = NotificationCompat.PRIORITY_HIGH // 设置为高优先级
                            setCategory(NotificationCompat.CATEGORY_MESSAGE) // 设置通知类别
                            setAutoCancel(true) // 设置触摸时自动取消
                            // 针对Android 8.0（API级别26）及以上版本，重要性已在通知渠道中定义
                        }

                    rhNotificationShownTime = System.currentTimeMillis()
                    notificationManager.notify(RH_NOTIFICATION_ID_0, builder.build())
                }
            }
        }
    }

    companion object {
        const val RH_CHANNEL_ID_0 = "RHWarn0"
        const val RH_NOTIFICATION_ID_0 = 0x10
    }
}
