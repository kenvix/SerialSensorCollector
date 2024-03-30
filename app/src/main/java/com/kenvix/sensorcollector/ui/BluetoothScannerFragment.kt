package com.kenvix.sensorcollector.ui

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.core.app.ActivityCompat
import com.google.android.material.snackbar.Snackbar
import com.kenvix.sensorcollector.R
import com.kenvix.sensorcollector.databinding.FragmentBluetoothScanBinding
import com.kenvix.sensorcollector.utils.getScanFailureMessage
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
    private var bthNamePattern: Pattern? = null

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
            .setScanMode(
                if (binding.quickScan.isChecked)
                    ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED
            ) // 高频率扫描
            .setReportDelay(if (binding.quickScan.isChecked) 0 else 500)
            .build()

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

                    bleScanner.startScan(null, bleSettings, bleScanCallback)
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

}
