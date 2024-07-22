import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ScanFailure
import java.util.UUID

class BleManager(private val context: Context) {

    private val central: BluetoothCentralManager
    private val discoveredDevices = mutableListOf<BluetoothDeviceWrapper>()
    private val handler = Handler(Looper.getMainLooper())
    private var onDeviceFoundCallback: ((BluetoothDeviceWrapper) -> Unit)? = null
    private val cachedDevices = mutableSetOf<String>() // Set to store cached device addresses

    private val centralManagerCallback = object : BluetoothCentralManagerCallback() {
        override fun onDiscovered(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
            Log.d("BLE", "Discovered peripheral: ${peripheral.name ?: "Unknown"}")
            handleDiscoveredPeripheral(peripheral, scanResult)
        }

        override fun onScanFailed(scanFailure: ScanFailure) {
            Log.e("BLE", "Scan failed: $scanFailure")
        }
    }

    init {
        central = BluetoothCentralManager(context, centralManagerCallback, handler)
    }

    fun startScanning(serviceUUID: UUID? = null, deviceName: String? = null, onDeviceFound: (BluetoothDeviceWrapper) -> Unit) {
        Log.d("BLE", "Starting BLE scan")
        discoveredDevices.clear()
        onDeviceFoundCallback = onDeviceFound

        try {
            central.scanForPeripherals()
            Log.d("BLE", "Started scanning for peripherals")
        } catch (e: Exception) {
            Log.e("BLE", "Error starting scan: ${e.message}")
        }
    }

    fun isDeviceCached(address: String): Boolean {
        return cachedDevices.contains(address)
    }

    fun stopScanning() {
        central.stopScan()
        Log.d("BLE", "Stopped scanning")
    }

    private fun handleDiscoveredPeripheral(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
        val deviceType = try {
            if (hasBluetoothPermission()) {
                getDeviceType(scanResult.device.bluetoothClass)
            } else {
                "Unknown (No Permission)"
            }
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException when getting device type", e)
            "Unknown (Security Exception)"
        }

        val device = BluetoothDeviceWrapper(
            name = peripheral.name ?: "Unknown",
            address = peripheral.address,
            rssi = scanResult.rssi,
            deviceType = deviceType
        )

        if (!discoveredDevices.contains(device)) {
            discoveredDevices.add(device)
            cachedDevices.add(device.address) // Add the device address to the cached set
            onDeviceFoundCallback?.let { callback ->
                handler.post { callback(device) }
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getDeviceType(bluetoothClass: BluetoothClass?): String {
        return when {
            bluetoothClass == null -> "Unknown"
            bluetoothClass.hasService(BluetoothClass.Service.AUDIO) -> "Audio Device"
            bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.PHONE -> "Phone"
            bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER -> "Computer"
            bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE -> "Wearable"
            bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
            bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.IMAGING -> "Imaging Device"
            bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.TOY -> "Toy"
            bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.HEALTH -> "Health Device"
            bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.UNCATEGORIZED -> "Uncategorized"
            else -> "Other"
        }
    }
}

data class BluetoothDeviceWrapper(
    val name: String,
    val address: String,
    val rssi: Int,
    val deviceType: String
)