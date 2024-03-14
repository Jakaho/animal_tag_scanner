package com.example.animaltag.data

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothDevice.PHY_LE_1M
import android.bluetooth.BluetoothDevice.PHY_LE_2M
import android.bluetooth.BluetoothDevice.PHY_LE_CODED
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*
import com.example.animaltag.*
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


private const val TAG = "BluetoothService"

// Stops scanning after 10 seconds.
const val SCAN_PERIOD: Long = 100000

const val COMPANY_ID = 26214; // 0x6666 Seer Electronics

const val SERVICE_UUID = "00000001-8334-4397-af7e-bfca78d70c67"

const val SENSOR_READ = "00000002-8334-4397-af7e-bfca78d70c67"
const val STEP_COUNTER_UUID = "00000003-8334-4397-af7e-bfca78d70c67"

const val SERVER_ADDRESS = "185.189.48.110"
const val PORT = 8772


data class UiScanResult(val scanResult: ScanResult, val steps: Int, val udpMessage: JSONObject)
class BluetoothService(bluetoothAdapter: BluetoothAdapter, private val context: Context) {
//    For foal alarm
    var file: File? = null
    var tagId = ""
    var steps = 0

//    Old code
    private val leScanner = bluetoothAdapter.bluetoothLeScanner

    private var currentPosition: LatLng = LatLng(0.0, 0.0)

    var uiScanResults = mutableStateListOf<UiScanResult>()
        private set

    var uiScanning = mutableStateOf(false)
        private set

    var uiConnecting = mutableStateOf(false)
        private set

    var uiConnected = mutableStateOf(false)
        private set

    var currentBluetoothGatt: BluetoothGatt? = null
        private set

    var udpResponse = mutableStateOf("")
        private set

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            //println time in seconds
            if (result.device.name != null && result.device.name.contains(
                    "pn_",
                    true
                )
            ) {
                Log.w(
                    TAG,
                    "onScanResult: ${result.device.name} ${
                        result.scanRecord?.manufacturerSpecificData?.get(
                            COMPANY_ID
                        )?.get(0)
                    }"
                )

                if (result.primaryPhy != null && result.primaryPhy == PHY_LE_1M) {
                    Log.w(
                        TAG,
                        "onScanResult: ${result.device.name} ${
                            "PrimaryPhy = PHY_LE_1M"
                        }"
                    )
                }

                if (result.primaryPhy != null && result.primaryPhy == PHY_LE_2M) {
                    Log.w(
                        TAG,
                        "onScanResult: ${result.device.name} ${
                            "PrimaryPhy = PHY_LE_2M"
                        }"
                    )
                }

                if (result.primaryPhy != null && result.primaryPhy == PHY_LE_CODED) {
                    Log.w(
                        TAG,
                        "onScanResult: ${result.device.name} ${
                            "PrimaryPhy = PHY_LE_CODED"
                        }"
                    )
                }
                //    Foal alarm: changed to global variable
                steps = getStepsFromManufacturingData(
                    result.scanRecord?.manufacturerSpecificData?.get(
                        COMPANY_ID
                    ) ?: ByteArray(2)
                )

                addScanResults(result, steps)
                tagId = result.device.name
                //send udp message
                val udpMessage = JSONObject().put("id", result.device.name)
                    .put("s", steps)
                    .put("v", 1)
                    .put("la", currentPosition.latitude)
                    .put("lo", currentPosition.longitude)
                sendUdpMessage(udpMessage)
            }

        }


        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> Log.d(
                    TAG,
                    "onScanFailed: SCAN_FAILED_ALREADY_STARTED"
                )

                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Log.d(
                    TAG,
                    "onScanFailed: SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
                )

                SCAN_FAILED_FEATURE_UNSUPPORTED -> Log.d(
                    TAG,
                    "onScanFailed: SCAN_FAILED_FEATURE_UNSUPPORTED"
                )

                SCAN_FAILED_INTERNAL_ERROR -> Log.d(
                    TAG,
                    "onScanFailed: SCAN_FAILED_INTERNAL_ERROR"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun addScanResults(scanResult: ScanResult, steps: Int) {
        //Update scan result if its already in the list, else add it
        uiScanResults.find { it.scanResult.device.name == scanResult.device.name }?.let {
            uiScanResults.remove(it)
            uiScanResults.add(
                addScanResult(scanResult, steps)
            )
        } ?: uiScanResults.add(
            addScanResult(scanResult, steps)
        )
    }

    @SuppressLint("MissingPermission")
    private fun addScanResult(
        scanResult: ScanResult,
        steps: Int
    ) = UiScanResult(
        scanResult,
        steps,
        JSONObject().put("id", scanResult.device.name)
            .put("s", steps)
            .put("v", 1)
            .put("la", currentPosition.latitude)
            .put("lo", currentPosition.longitude)
    )

    @SuppressLint("MissingPermission")
    fun scanLeDevice(position: LatLng) {
        currentPosition = position
        uiScanResults.clear()
        udpResponse.value = ""
        if (!uiScanning.value) {
            uiScanning.value = true
            //add low power scan settings here
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()
            leScanner.startScan(null, settings, leScanCallback)
        } else {
            stopScanningLeDevice()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanningLeDevice() {
        uiScanning.value = false
        udpResponse.value = ""
        leScanner.stopScan(leScanCallback)
    }

    @SuppressLint("MissingPermission")
    fun startConnect(context: Context, selectedDevice: BluetoothDevice?) {
        stopScanningLeDevice()
        selectedDevice?.let {
            uiConnecting.value = true
            selectedDevice.connectGatt(context, true, gattCallback, TRANSPORT_LE)
        }
    }

    @SuppressLint("MissingPermission")
    fun closeCurrentGatt(currentGatt: BluetoothGatt? = currentBluetoothGatt) {
        Log.w("closeCurrentGatt ", "Try to close connection ${currentGatt?.device?.name}")
        uiConnecting.value = true
        currentGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceName = gatt.device.name
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("onConnectionStateChange", "Successfully connected to $deviceName")
                    uiConnected.value = true
                    currentBluetoothGatt = gatt;
                    Handler(Looper.getMainLooper()).post {
                        currentBluetoothGatt?.discoverServices()
                    }

//                    Foal alarm create new file for data collection
                    var dateNow = LocalDateTime.now()
                    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
                    val date = dateNow.format(formatter)
                    val filename = "animal-tag-data-" + date.toString() + ".csv"

                    //file = File(Environment.getExternalStorageDirectory(), filename
                    file = File(context.getExternalFilesDir(null), filename)


                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("onConnectionStateChange", "Successfully disconnected from $deviceName")
                    uiConnected.value = false
                    uiConnecting.value = false
                    udpResponse.value = ""
                    gatt.close()
                }
            } else {
                Log.w(
                    "onConnectionStateChange",
                    "Error $status encountered for $deviceName! Disconnecting..."
                )
                uiConnected.value = false
                uiConnecting.value = false;
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(
                            "onCharacteristicRead",
                            "Read characteristic $uuid:\n${value.toHexString()}"
                        )

                    }

                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("onCharacteristicRead", "Read not permitted for $uuid!")
                    }

                    else -> {
                        Log.e(
                            "onCharacteristicRead",
                            "Characteristic read failed for $uuid, error: $status"
                        )
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            with(descriptor) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(
                            "onDescriptorWrite",
                            "Read descriptor ${this?.uuid}:\n${this?.value?.toHexString()}"
                        )
                    }

                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e(
                            "onDescriptorWrite",
                            " Descriptor read not permitted for ${this?.uuid}!"
                        )
                    }

                    else -> {
                        Log.e(
                            "BluetoothGattCallback",
                            "Descriptorread failed for ${this?.uuid}, error: $status"
                        )
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            with(characteristic) {
                Log.i(
                    "onCharacteristicChanged",
                    "Characteristic $uuid changed | value: ${value.size}"
                )

                var shortArray = ShortArray(0)
                for (i in 0 until value.size step 4) {
                    val a = value[i].toUByte()
                    val b = value[i + 1].toUByte()
                    val c = value[i + 2].toUByte()
                    val d = value[i + 3].toUByte()
                    shortArray = shortArray.plus(decomp(a, b, c, d))
                }

//                Foal alarm: create data string
                val csvData = "$tagId,$steps,${Date().time},\"${shortArray.joinToString(",")}\""

//                Foal alarm: append data to file if not null
                try {
                    Log.d(TAG, "Attempting to write to file: $csvData")
                    file?.appendText("$csvData\n")
                    Log.d(TAG, "Successfully wrote to file.")
                } catch (e: IOException) {
                    Log.e(TAG, "Error writing to file", e)
                }

                uiScanResults.find { it.scanResult.device.name == gatt.device.name }
                    ?.let {
                        it.udpMessage.put("d", JSONArray(shortArray))
                            .put("g", 2)
                            .put("a", 1)
                            .put("f", 25)
                            .put("t", Date().time)
                        sendUdpMessage(it.udpMessage)
                    }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w(
                    "onServicesDiscovered",
                    "Discovered ${services.size} services for ${device.address}"
                )
                if (services.isNotEmpty()) {
                    services.find {
                        it.uuid.toString() == SERVICE_UUID
                    }?.characteristics?.find {
                        it.uuid.toString() == SENSOR_READ
                    }?.let { bluetoothGattCharacteristic ->
                        Log.w(
                            "onServicesDiscovered",
                            "Found characteristic ${bluetoothGattCharacteristic.uuid} and its readable: ${bluetoothGattCharacteristic.isReadable()}  writable: ${bluetoothGattCharacteristic.isWritable()} writableWithoutResponse: ${bluetoothGattCharacteristic.isWritableWithoutResponse()}  notify: ${bluetoothGattCharacteristic.isNotifiable()}"
                        )
                        bluetoothGattCharacteristic.descriptors.forEach {
                            Log.w(
                                "onServicesDiscovered",
                                "Found descriptor ${it.uuid} and its ${it.permissions}"
                            )
                            enableNotifications(bluetoothGattCharacteristic, it.uuid)
                        }
                    }
                }
            }
        }


        private fun BluetoothGatt.printGattTable(): Boolean {
            if (services.isEmpty()) {
                Log.i(
                    "printGattTable",
                    "No service and characteristic available, call discoverServices() first?"
                )
                return false
            }
            services.forEach { service ->
                val characteristicsTable = service.characteristics.joinToString(
                    separator = "\n|--",
                    prefix = "|--"
                ) { it.uuid.toString() }
                Log.i(
                    "printGattTable",
                    "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
                )
            }
            return true
        }

        fun enableNotifications(characteristic: BluetoothGattCharacteristic, descriptor: UUID) {
            val payload = when {
                characteristic.isIndictable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else -> {
                    Log.e(
                        "enableNotifications",
                        "${characteristic.uuid} doesn't support notifications/indications"
                    )
                    return
                }
            }

            characteristic.getDescriptor(descriptor)?.let { cccDescriptor ->
                if (currentBluetoothGatt?.setCharacteristicNotification(
                        characteristic,
                        true
                    ) == false
                ) {
                    Log.e(
                        "enableNotifications",
                        "setCharacteristicNotification failed for ${characteristic.uuid}"
                    )
                    return
                }
                writeDescriptor(cccDescriptor, payload)
            } ?: Log.e(
                "enableNotifications",
                "${characteristic.uuid} doesn't contain the CCC descriptor!"
            )
        }

        fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
            currentBluetoothGatt?.let { gatt ->
                descriptor.value = payload
                gatt.writeDescriptor(descriptor)
            } ?: error("Not connected to a BLE device!")
        }

        fun BluetoothGattCharacteristic.isReadable(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

        fun BluetoothGattCharacteristic.isWritable(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

        fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

        fun BluetoothGattCharacteristic.isIndictable(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

        fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

        fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
            return properties and property != 0
        }
    }

    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

    fun sendUdpMessage(json: JSONObject) {
        GlobalScope.launch {
            try {
                udpResponse.value = "Sending..."
                //add timestamp here
                json.put("t", Date().time)
                val socket = DatagramSocket()
                val data = json.toString().toByteArray()
                val packet =
                    DatagramPacket(data, data.size, InetAddress.getByName(SERVER_ADDRESS), PORT)

                Log.i(TAG, json.toString())

                socket.send(packet)

                val responseBytes = ByteArray(1024)
                val responsePacket = DatagramPacket(responseBytes, responseBytes.size)
                socket.receive(responsePacket)

                udpResponse.value = String(responsePacket.data, 0, responsePacket.length)
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "sendUdpMessage: ", e)
            }
        }
    }
}

inline infix fun UByte.and(other: UByte): UByte = (this.toInt() and other.toInt()).toUByte()

public inline infix fun UShort.and(other: UShort): UShort =
    (this.toInt() and other.toInt()).toUShort()

// Shifts from littleEndian to bigEndian
fun ext(l: UByte, h: UByte): Short {
    var high: UByte = h;
    // Keep the signed value ???
    if ((h.toInt() and 2) > 0) {
        high = (high.toInt() or 0xfc).toUByte();
    }
    var return_value: Short = ((high.toInt() shl 8) or l.toInt()).toShort();
    return return_value;
}

// Unpack SEER compressed byte format into ShortArray
fun decomp(a: UByte, b: UByte, c: UByte, d: UByte): ShortArray {

    // Unpack x-low and x-high
    val xl: UByte = a;
    val xh: UByte = (b.toInt() shr 6).toUByte();
    // Unpack y-low and y-high
    val yl: UByte = (((b.toInt() and 0x3f) shl 2) or (c.toInt() shr 6)).toUByte();
    val yh: UByte = ((c.toInt() shr 4) and 0x3).toUByte();
    // Unpack z-low and z-high
    val zl: UByte = (((c.toInt() and 0xf) shl 4) or (d.toInt() shr 4)).toUByte();
    val zh: UByte = ((d.toInt() shr 2) and 0x3).toUByte();

    // Create final values
    val x: Short = ext(xl, xh);
    val y: Short = ext(yl, yh);
    val z: Short = ext(zl, zh);

    var unpacked_array: ShortArray = shortArrayOf(x, y, z);

    return unpacked_array;
}

fun getStepsFromManufacturingData(byteArray: ByteArray): Int {

    val low: UShort = byteArray[0].toUShort();
    val high: UShort = byteArray[1].toUShort();

    var steps: UShort = (high.toInt() shl 8).toUShort();

    steps = (steps + low).toUShort();

    return steps.toInt();


}