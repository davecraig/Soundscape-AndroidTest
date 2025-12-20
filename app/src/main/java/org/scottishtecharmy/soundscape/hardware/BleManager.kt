package org.scottishtecharmy.soundscape.hardware

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import org.scottishtecharmy.soundscape.hardware.bose.SampleData
import org.scottishtecharmy.soundscape.hardware.bose.SensorConfiguration
import org.scottishtecharmy.soundscape.hardware.bose.SensorInformation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class BleManager(
    private val context: Context,
    private val targetServiceUuid: UUID,
    private val onRotation: (Double) -> Unit // Callback to send data to the UI/ViewModel
) {

    private val boseSensorInformation = UUID.fromString("855cb3e7-98ff-42a6-80fc-40b32a2221c1")
    private val boseSensorConfiguration = UUID.fromString("5af38af6-000e-404b-9b46-07f77580890b")
    private val boseSensorData = UUID.fromString("56a72ab8-4988-4cc8-a752-fbd1d54a953d")

    private val commandQueue: Queue<Runnable> = ConcurrentLinkedQueue()
    private var isCommandQueueExecuting = false

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private var bleGatt: BluetoothGatt? = null
    private var targetDevice: BluetoothDevice? = null

    //region ====== State Callbacks ======

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT Connected. Discovering services...")
                bleGatt = gatt
                commandQueue.clear()
                isCommandQueueExecuting = false
                bleGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "GATT Disconnected.")
                stop() // Clean up resources on disconnection
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered successfully.")
                val service = gatt?.getService(targetServiceUuid)
                // Read all of our characteristics
                service?.getCharacteristic(boseSensorInformation)?.let { char ->
                    enqueueCommand { gatt.readCharacteristic(char) }
                }
                service?.getCharacteristic(boseSensorConfiguration)?.let { char ->
                    enqueueCommand { gatt.readCharacteristic(char) }
                }
                service?.getCharacteristic(boseSensorData)?.let { char ->
                    // For the 'data' characteristic, we likely want to enable notifications
                    enqueueCommand { enableNotifications(gatt, char) }
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
            }
        }

        // Add this to the "Private Helper Functions" region
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bleGatt?.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                // For older Android versions
                characteristic.value = data
                bleGatt?.writeCharacteristic(characteristic)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicRead(gatt: BluetoothGatt,
                                          characteristic: BluetoothGattCharacteristic,
                                          data: ByteArray,
                                          status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val buffer = ByteBuffer.wrap(data)
                buffer.order(ByteOrder.BIG_ENDIAN)
                when (characteristic.uuid) {
                    boseSensorConfiguration -> {
                        val configs = SensorConfiguration.parse(buffer)
                        for (config in configs.entries) {
                            Log.e(TAG, "${config.type} ${config.samplePeriod} ${config.isEnabled}")
                        }

                        configs.enableAll()
                        writeCharacteristic(characteristic, configs.data())
                        return
                    }
                    boseSensorInformation -> {
                        val informations = SensorInformation.parse(buffer)
                        for (information in informations.entries) {
                            information.dump()
                        }
                    }
                    boseSensorData -> {
                        Log.e(TAG, "Sensor data!")
                        val sample = SampleData.parse(buffer)
                    }
                }
            } else {
                Log.w(TAG, "Characteristic read failed with status: $status")
            }
            commandFinished()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Successfully wrote to characteristic ${characteristic?.uuid}")
            } else {
                Log.w(TAG, "Failed to write to characteristic ${characteristic?.uuid}, status: $status")
            }
            // This is crucial: signal that the command has finished so the next one can run.
            commandFinished()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt,
                                             characteristic: BluetoothGattCharacteristic,
                                             data: ByteArray) {
            if (characteristic.uuid == boseSensorData) {
                val buffer = ByteBuffer.wrap(data)
                buffer.order(ByteOrder.BIG_ENDIAN)
                val data = SampleData.parse(buffer)
                if(data != null)
                    onRotation(data)
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // We found a device advertising our service
            Log.i(TAG, "Found BLE device: ${result.device.name ?: "Unnamed"} (${result.device.address})")
            targetDevice = result.device
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            stopScan()
            connectToDevice()
        }



        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan Failed with error code: $errorCode")
        }
    }

    //endregion

    private fun enqueueCommand(command: Runnable) {
        commandQueue.add(command)
        if (!isCommandQueueExecuting) {
            processNextCommand()
        }
    }

    private fun processNextCommand() {
        if (isCommandQueueExecuting) return

        val command = commandQueue.poll()
        if (command != null) {
            isCommandQueueExecuting = true
            command.run()
        }
    }

    // Call this in your GATT callbacks (onCharacteristicRead, onDescriptorWrite, etc.)
    // after a GATT operation completes.
    private fun commandFinished() {
        isCommandQueueExecuting = false
        processNextCommand()
    }

    //region ====== Public API ======

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or not available.")
            // You should prompt the user to enable Bluetooth here.
            return
        }
        Log.i(TAG, "Starting BLE scan for service: $targetServiceUuid")
        startScan()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stop() {
        Log.i(TAG, "Stopping BLE Manager.")
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        stopScan()
        bleGatt?.disconnect()
        bleGatt?.close()
        bleGatt = null
        targetDevice = null
    }

// Add this to the "Public API" region of your BleManager class

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readValue() {
        if (bleGatt == null) {
            Log.e(TAG, "Cannot read value, GATT connection not established.")
            return
        }
        val service = bleGatt?.getService(targetServiceUuid)
//        val characteristic = service?.getCharacteristic(targetCharacteristicUuid)
//
//        if (characteristic != null) {
//            Log.i(TAG, "Requesting read from characteristic: $targetCharacteristicUuid")
//            if (!bleGatt!!.readCharacteristic(characteristic)) {
//                Log.e(TAG, "Failed to initiate characteristic read.")
//            }
//        } else {
//            Log.e(TAG, "Cannot read, target characteristic not found.")
//        }
    }

    //endregion

    //region ====== Private Helper Functions ======

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan() {
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(targetServiceUuid))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopScan() {
        bleScanner?.stopScan(scanCallback)
        Log.i(TAG, "Scan stopped.")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice() {
        if (targetDevice == null) {
            Log.e(TAG, "Cannot connect, no target device found.")
            return
        }
        Log.i(TAG, "Connecting to device: ${targetDevice?.address}")
        targetDevice?.connectGatt(context, false, gattCallback)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        // The CCCD is a standard descriptor that enables/disables notifications/indications.
        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val descriptor = characteristic.getDescriptor(cccdUuid)

        if (descriptor == null) {
            Log.e(TAG, "CCCD descriptor not found for characteristic!")
            return
        }

        gatt.setCharacteristicNotification(characteristic, true)
        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
    }

    //endregion

    companion object {
        private const val TAG = "BleManager"
    }
}

// Extension function for logging byte arrays
fun ByteArray.toHexString(): String = joinToString(separator = " ") { eachByte -> "%02x".format(eachByte) }
