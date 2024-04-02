//--------------------------------------------------
// Class ExcelRecordWriter
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.utils

import android.content.Context
import android.hardware.usb.UsbDevice
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.felhr.usbserial.UsbSerialDevice
import com.kenvix.sensorcollector.hardware.vendor.SensorData
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.PrintStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger

interface RecordWriter : Closeable {
    fun setDeviceList(usbDevice: Collection<UsbDevice>)
    fun onSensorDataReceived(
        usbDevice: UsbDevice,
        usbSerialDevice: UsbSerialDevice,
        sensorData: SensorData
    )

    fun save()
    val rowsWritten: Long
}

class CsvRecordWriter(val context: Context, val filePath: Uri) :
    RecordWriter,
    CoroutineScope by CoroutineScope(CoroutineName("CsvRecordWriter")) {

    private val deviceToStream = mutableMapOf<UsbDevice, SheetPos>()
    private lateinit var documentRootTree: DocumentFile
    private lateinit var documentActualTree: DocumentFile
    private val formatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSS")

    private val dirNameFormatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss")

    private data class SheetPos(val uri: Uri, val stream: PrintStream, var pos: Int)
    override var rowsWritten: Long = 0
        private set

    override fun setDeviceList(usbDevice: Collection<UsbDevice>) {
        // Make directory
        documentRootTree = DocumentFile.fromTreeUri(context, filePath)!!
        documentActualTree = documentRootTree.createDirectory(ZonedDateTime.now().format(dirNameFormatter))!!

        usbDevice.forEach {
            val name = it.deviceName
                .removePrefix("/dev/bus/usb/")
                .removePrefix("/dev/")
                .removePrefix("/")
                .replace("/", "_")
            val file = documentActualTree.createFile("text/csv", "$name.csv")
            val stream = context.contentResolver.openOutputStream(file!!.uri, "w")
            val s = PrintStream(BufferedOutputStream(stream, 32 * 1024))
            deviceToStream[it] = SheetPos(file.uri, s, 0)
            s.println("No,AccX,AccY,AccZ,GyroX,GyroY,GyroZ,AngleX,AngleY,AngleZ,TimeStamp,LocalTime")
        }
    }

    override fun onSensorDataReceived(
        usbDevice: UsbDevice,
        usbSerialDevice: UsbSerialDevice,
        sensorData: SensorData
    ) {
        // Log.v("CsvRecordWriter", "SensorDataReceived: ${usbDevice.deviceName} : $sensorData")
        val pos = deviceToStream[usbDevice]
            ?: throw IllegalArgumentException("Device not found in PrintStreams")
        pos.pos++
        val s = "${pos.pos},${sensorData.accX},${sensorData.accY},${sensorData.accZ}," +
                "${sensorData.gyroX},${sensorData.gyroY},${sensorData.gyroZ}," +
                "${sensorData.angleX},${sensorData.angleY},${sensorData.angleZ}," +
                "${Instant.now().toEpochMilli()},\"${ZonedDateTime.now().format(formatter)}\""
        pos.stream.println(s)
        rowsWritten++
    }

    override fun save() {
        deviceToStream.forEach { (device, pair) ->
            pair.stream.flush()
        }
    }

    override fun close() {
        save()
        deviceToStream.forEach { (device, pair) ->
            pair.stream.close()
        }
    }

}

class ExcelRecordWriter(val context: Context, val filePath: Uri) :
    RecordWriter,
    CoroutineScope by CoroutineScope(CoroutineName("ExcelRecordWriter")) {

    private val workbook = XSSFWorkbook() // XSSFWorkbook or SXSSFWorkbook
    private val deviceToSheet = mutableMapOf<UsbDevice, SheetPos>()
    private val cellStyleHeader = workbook.createCellStyle().apply {
        fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        fillPattern = FillPatternType.SOLID_FOREGROUND
        setFont(workbook.createFont().apply {
            bold = true
        })
    }

    override var rowsWritten: Long = 0
        private set

    private val formatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSS")
    private val zone = ZoneId.systemDefault()

    private data class SheetPos(val sheet: Sheet, var pos: AtomicInteger)

    init {
    }

    override fun setDeviceList(usbDevice: Collection<UsbDevice>) {
        usbDevice.forEach {
            val name = it.deviceName
                .removePrefix("/dev/bus/usb/")
                .removePrefix("/dev/")
                .removePrefix("/")
                .replace("/", "_")
            val sheet = workbook.createSheet(name)
            sheet.createRow(0).apply {
                createCell(0).apply { setCellValue("No"); cellStyle = cellStyleHeader }
                createCell(1).apply { setCellValue("AccX"); cellStyle = cellStyleHeader }
                createCell(2).apply { setCellValue("AccY"); cellStyle = cellStyleHeader }
                createCell(3).apply { setCellValue("AccZ"); cellStyle = cellStyleHeader }
                createCell(4).apply { setCellValue("GyroX"); cellStyle = cellStyleHeader }
                createCell(5).apply { setCellValue("GyroY"); cellStyle = cellStyleHeader }
                createCell(6).apply { setCellValue("GyroZ"); cellStyle = cellStyleHeader }
                createCell(7).apply { setCellValue("AngleX"); cellStyle = cellStyleHeader }
                createCell(8).apply { setCellValue("AngleY"); cellStyle = cellStyleHeader }
                createCell(9).apply { setCellValue("AngleZ"); cellStyle = cellStyleHeader }
                createCell(10).apply { setCellValue("TimeStamp"); cellStyle = cellStyleHeader }
                createCell(11).apply { setCellValue("LocalTime"); cellStyle = cellStyleHeader }
            }

            deviceToSheet[it] = SheetPos(sheet, AtomicInteger(1))
        }
    }

    override fun onSensorDataReceived(
        usbDevice: UsbDevice,
        usbSerialDevice: UsbSerialDevice,
        sensorData: SensorData
    ) {
        // Log.v("ExcelRecordWriter", "SensorDataReceived: ${usbDevice.deviceName} : $sensorData")
        val (sheet, indexA) = deviceToSheet[usbDevice]
            ?: throw IllegalArgumentException("Device not found in XSSFSheets")
        val index = indexA.getAndIncrement()
        sheet.createRow(index).apply {
            createCell(0).setCellValue(index.toDouble())
            createCell(1).setCellValue(sensorData.accX)
            createCell(2).setCellValue(sensorData.accY)
            createCell(3).setCellValue(sensorData.accZ)
            createCell(4).setCellValue(sensorData.gyroX)
            createCell(5).setCellValue(sensorData.gyroY)
            createCell(6).setCellValue(sensorData.gyroZ)
            createCell(7).setCellValue(sensorData.angleX)
            createCell(8).setCellValue(sensorData.angleY)
            createCell(9).setCellValue(sensorData.angleZ)
            val currentTime = Instant.now()
            createCell(10).setCellValue(currentTime.toEpochMilli().toDouble())
            createCell(11).setCellValue(
                ZonedDateTime.ofInstant(currentTime, zone).format(formatter)
            )
        }
        rowsWritten++
    }

    override fun save() {
        context.contentResolver.openOutputStream(filePath, "w").use { stream ->
            if (stream == null)
                throw IllegalStateException("Cannot open target file stream $filePath")

            stream.buffered().also {  s  ->
                workbook.write(s)
                s.flush()
            }

            stream.flush()
        }
    }

    override fun close() {
        save()
        workbook.close()
    }
}
