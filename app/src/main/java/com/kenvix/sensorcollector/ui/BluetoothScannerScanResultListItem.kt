package com.kenvix.sensorcollector.ui

import android.bluetooth.le.ScanResult
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.util.size
import com.kenvix.sensorcollector.databinding.RecordingBthListEntryBinding // 更新此行以匹配你的包名和布局文件名
import com.kenvix.sensorcollector.utils.parserServiceData
import com.kenvix.sensorcollector.utils.toHexString
import kotlinx.serialization.Serializable
import java.time.LocalTime

@Serializable
open class BluetoothScannerListItem(
    var title: String = "",
    var body: String = "",
    var isChecked: Boolean = false
)

data class BluetoothScannerScanResultListItem(private val result: ScanResult) :
    BluetoothScannerListItem(),
    Comparable<BluetoothScannerScanResultListItem> {

    val time = LocalTime.now()

    init {
        title = String.format(
            "[%02d:%02d:%02d.%03d %02d] %s",
            time.hour, time.minute, time.second, time.nano / 1000000,
            result.rssi,
            if (result.scanRecord?.deviceName.isNullOrEmpty()) result.device.address else result.scanRecord?.deviceName
        )

        body = StringBuilder().let {
            result.scanRecord?.serviceData?.forEach { (uuid, bytes) ->
                parserServiceData(uuid.toString(), bytes).let { data ->
                    if (data != null) {
                        it.append(data.toString()).append("\n")
                    } else {
                        it.append(String.format("%s: %s\n", uuid, bytes.toHexString()))
                    }
                }
            }

            it.append("Packet ${result.scanRecord?.bytes?.size ?: 0}B, ")
            it.append("Services ${result.scanRecord?.serviceData?.size ?: 0}, ")
            it.append("ManufactSpec ${result.scanRecord?.manufacturerSpecificData?.size ?: 0} ")

            if (result.txPower > -127 && result.txPower < 126) {
                it.append("Tx ${result.txPower}")
            }

            it.toString()
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is BluetoothScannerScanResultListItem && other.result.device.address == result.device.address
    }

    override fun hashCode(): Int {
        return result.device.address.hashCode()
    }

    override fun compareTo(other: BluetoothScannerScanResultListItem): Int =
        time.compareTo(other.time)
}

class BluetoothScannerListAdapter(
    context: Context,
    private val items: List<BluetoothScannerListItem>
) :
    ArrayAdapter<BluetoothScannerListItem>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            RecordingBthListEntryBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            RecordingBthListEntryBinding.bind(convertView)
        }
        val item = items[position]
        binding.itemTitle.text = item.title
        binding.itemBody.text = item.body
        binding.itemCheckbox.isChecked = item.isChecked

        return binding.root
    }
}
