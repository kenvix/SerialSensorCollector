package com.kenvix.sensorcollector.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.kenvix.sensorcollector.R
import com.kenvix.sensorcollector.databinding.FragmentRecordingBinding
import com.kenvix.sensorcollector.exceptions.BusinessException
import com.kenvix.sensorcollector.services.UsbSerial
import com.kenvix.sensorcollector.services.UsbSerialRecorderService
import com.kenvix.sensorcollector.utils.ExcelRecordWriter
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDateTime
import kotlin.coroutines.resume


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class RecorderFragment : Fragment() {
    private var _binding: FragmentRecordingBinding? = null
    private val scannedSerialStringItems: MutableList<String> = ArrayList()
    private val scannedSerialDevices = ArrayList<UsbDevice>()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var activity: MainActivity
    private var safContinuation: CancellableContinuation<Uri?>? = null
    private lateinit var safCreateFileLauncherXlsx: ActivityResultLauncher<String>
    private lateinit var safCreateFileLauncherCsv: ActivityResultLauncher<Uri?>


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity = requireActivity() as MainActivity

        safCreateFileLauncherXlsx = registerForActivityResult(
            ActivityResultContracts
                .CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        ) { uri ->
            // Handle the returned Uri
            Log.d(this::class.simpleName, "SAF Created output xlsx file: $uri")
            safContinuation?.resume(uri)
        }

        safCreateFileLauncherCsv = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            // Handle the returned Uri
            Log.d(this::class.simpleName, "SAF Selected Output Directory: $uri")
            safContinuation?.resume(uri)
        }

        binding.serialList.apply {
            setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE)
            setAdapter(
                ArrayAdapter(
                    this.context,
                    R.layout.recording_serial_list_entry, scannedSerialStringItems
                )
            )

            setOnItemClickListener { parent, view, position, id ->
                val checked = binding.serialList.isItemChecked(position)
                val device = scannedSerialDevices[position]
                if (checked) {
                    var granted = UsbSerial.hasPermission(device)
                    if (!granted) {
                        // request permission of this usb device
                        activity.launch {
                            withUIOperationDisabledA {
                                granted = UsbSerial.requestPermission(requireContext(), device)
                                binding.serialList.setItemChecked(
                                    position,
                                    granted
                                )

                                if (granted)
                                    UsbSerial.selectedDevices.add(device)
                            }
                        }
                    } else {
                        UsbSerial.selectedDevices.add(device)
                    }
                } else {
                    UsbSerial.selectedDevices.remove(device)
                }
            }
        }

        //listView.getCheckedItemPositions();
        updateSerialListUI()

        binding.buttonScanSerial.setOnClickListener {
            try {
                withUIOperationDisabledN {
                    val devices = UsbSerial.getAvailableUsbSerialDevices()
                    Log.d(this::class.simpleName, "Available devices: $devices")

                    scannedSerialDevices.clear()
                    scannedSerialStringItems.clear()
                    UsbSerial.selectedDevices.clear()
                    devices.forEach {
                        scannedSerialDevices.add(it)
                        scannedSerialStringItems.add("${it.productName} ${it.version}\n${it.deviceName}")
                    }

                    updateSerialListUI()
                }
            } catch (e: Exception) {
                Log.w(this::class.simpleName, e)
                AlertDialog.Builder(requireContext())
                    .setTitle("USB Scan failed")
                    .setMessage(e.message)
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }

        binding.buttonStartRecoding.setOnClickListener {
            try {
                activity.launch(Dispatchers.Main) {
                    withUIOperationDisabledA {

                        try {
                            val outputFormat =
                                when (binding.outputFormatGroup.checkedRadioButtonId) {
                                    R.id.output_format_csv -> "csv"
                                    R.id.output_format_xlsx -> "xlsx"
                                    else -> throw BusinessException("Unknown output format")
                                }

                            val uri = safCreateFile(outputFormat)
                                ?: throw BusinessException("You must choose the save path")

                            val intent = Intent(context, UsbSerialRecorderService::class.java)
                            intent.putExtra("uri", uri)
                            intent.putExtra("parser", activity.dataParser)
                            intent.putExtra("output_format", outputFormat)
                            ContextCompat.startForegroundService(requireContext(), intent)
                        } catch (e: Exception) {
                            UsbSerial.stopAllSerial()
                            Log.w(this::class.simpleName, e)
                            AlertDialog.Builder(requireContext())
                                .setTitle("Start Recorder Service Failed")
                                .setMessage(e.toString())
                                .setPositiveButton("OK") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(this::class.simpleName, e)
                AlertDialog.Builder(requireContext())
                    .setTitle("Record failed")
                    .setMessage(e.toString())
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }

//        binding.buttonFirst.setOnClickListener {
//            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
//        }
    }

    private suspend fun safCreateFile(outputFormat: String): Uri? {
        val filename = LocalDateTime.now().toString().replace(':', '_') + ".xlsx"
        return suspendCancellableCoroutine<Uri?> { continuation ->
            // 启动文件创建流程
            safContinuation = continuation
            if (outputFormat == "xlsx")
                safCreateFileLauncherXlsx.launch(filename)
            else
                safCreateFileLauncherCsv.launch(Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments"))
        }
    }

    private fun updateSerialListUI() {
        (binding.serialList.adapter as ArrayAdapter<*>).notifyDataSetChanged()
    }

    private suspend fun withUIOperationDisabledA(op: suspend () -> Unit) {
        try {
            binding.buttonStartRecoding.isEnabled = false
            binding.buttonScanSerial.isEnabled = false
            binding.serialList.isEnabled = false
            op()
        } finally {
            binding.buttonStartRecoding.isEnabled = true
            binding.buttonScanSerial.isEnabled = true
            binding.serialList.isEnabled = true
        }
    }

    private fun withUIOperationDisabledN(op: () -> Unit) {
        try {
            binding.buttonStartRecoding.isEnabled = false
            binding.buttonScanSerial.isEnabled = false
            binding.serialList.isEnabled = false
            op()
        } finally {
            binding.buttonStartRecoding.isEnabled = true
            binding.buttonScanSerial.isEnabled = true
            binding.serialList.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
