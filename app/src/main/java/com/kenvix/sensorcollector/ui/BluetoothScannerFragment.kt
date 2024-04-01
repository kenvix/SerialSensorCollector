package com.kenvix.sensorcollector.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
import androidx.core.util.size
import com.google.android.material.snackbar.Snackbar
import com.kenvix.sensorcollector.R
import com.kenvix.sensorcollector.databinding.FragmentBluetoothScanBinding
import com.kenvix.sensorcollector.utils.getScanFailureMessage
import com.kenvix.sensorcollector.utils.parserServiceData
import com.kenvix.sensorcollector.utils.toHexString
import java.time.LocalTime
import java.util.regex.Pattern

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
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
    private val scannedItems = mutableListOf<BluetoothScannerListItem>()
    private var blePhy: Int = 0

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

                val item = BluetoothScannerListItem(result)
                scannedItems.indexOf(item).let { index ->
                    if (index != -1) {
                        scannedItems[index] = item
                    } else {
                        scannedItems.add(item)
                    }
                }

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
            withUIOperationDisabledN {
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
                        return@withUIOperationDisabledN
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

    private fun withUIOperationDisabledN(op: () -> Unit) {
        binding.buttonBluetoothScan.isEnabled = false
        op()
        binding.buttonBluetoothScan.isEnabled = true
    }

    private suspend fun withUIOperationDisabledA(op: suspend () -> Unit) {
        binding.buttonBluetoothScan.isEnabled = false
        op()
        binding.buttonBluetoothScan.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateBthListUI() {
        (binding.bthList.adapter as ArrayAdapter<*>).notifyDataSetChanged()
    }
}
