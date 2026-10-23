package jp.bludex.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import jp.bludex.app.data.BleDevice
import jp.bludex.app.data.DeviceClassifier
import jp.bludex.app.data.asHex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ScanState {
    data object Stopped : ScanState
    data object Scanning : ScanState
    data class Failed(val explanation: String) : ScanState
}

class BleScanner(context: Context) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val handler = Handler(Looper.getMainLooper())
    private val cache = LinkedHashMap<String, BleDevice>()
    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _state = MutableStateFlow<ScanState>(ScanState.Stopped)
    val state = _state.asStateFlow()
    private var active = false
    private var pendingRefresh = false

    private val refresh = Runnable {
        pendingRefresh = false
        _devices.value = cache.values.sortedByDescending { it.rssi }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handle)
        }
        override fun onScanFailed(errorCode: Int) {
            active = false
            _state.value = ScanState.Failed("BLEスキャンに失敗しました (code $errorCode)")
        }
    }

    val available: Boolean get() = adapter != null

    @SuppressLint("MissingPermission")
    fun bluetoothEnabled(): Boolean = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun start() {
        if (active) return
        if (adapter == null) {
            _state.value = ScanState.Failed("この端末はBluetoothに対応していません")
            return
        }
        if (!bluetoothEnabled()) {
            _state.value = ScanState.Failed("Bluetoothを有効にしてください")
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _state.value = ScanState.Failed("BLEスキャンを開始できません")
            return
        }
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0L)
                .build()
            scanner.startScan(null, settings, callback)
            active = true
            _state.value = ScanState.Scanning
        } catch (_: SecurityException) {
            _state.value = ScanState.Failed("Bluetooth・位置情報の権限を確認してください")
        } catch (e: IllegalStateException) {
            _state.value = ScanState.Failed(e.message ?: "スキャン開始に失敗しました")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (active) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
            active = false
        }
        handler.removeCallbacks(refresh)
        pendingRefresh = false
        _state.value = ScanState.Stopped
    }

    fun clearNearby() {
        cache.clear()
        _devices.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun handle(result: ScanResult) {
        val record = result.scanRecord
        val name = record?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: ""
        val address = runCatching { result.device.address }.getOrNull() ?: return
        val manufacturerBytes = record?.manufacturerSpecificData
        val manufacturerData = linkedMapOf<String, String>()
        var companyId: Int? = null
        if (manufacturerBytes != null) {
            for (i in 0 until manufacturerBytes.size()) {
                val id = manufacturerBytes.keyAt(i)
                if (companyId == null) companyId = id
                manufacturerData["0x%04X".format(id)] = manufacturerBytes.valueAt(i).asHex()
            }
        }
        val services = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty()
        val serviceData = linkedMapOf<String, String>()
        record?.serviceData?.forEach { (uuid, bytes) ->
            serviceData[uuid.uuid.toString()] = bytes.asHex()
        }
        val identification = DeviceClassifier.identify(name, companyId, services)
        val now = System.currentTimeMillis()
        val previous = cache[address]
        val item = BleDevice(
            address = address,
            name = name.ifBlank { previous?.name ?: "" },
            manufacturerId = companyId ?: previous?.manufacturerId,
            manufacturer = if (identification.manufacturer == "メーカー不明")
                previous?.manufacturer ?: identification.manufacturer
                else identification.manufacturer,
            model = identification.model ?: previous?.model,
            category = if (identification.category == jp.bludex.app.data.DeviceCategory.OTHER)
                previous?.category ?: identification.category
                else identification.category,
            rssi = result.rssi,
            txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE && it != 127 },
            connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else null,
            serviceUuids = services,
            serviceData = serviceData,
            manufacturerData = manufacturerData,
            rawHex = record?.bytes?.asHex().orEmpty(),
            firstSeen = previous?.firstSeen ?: now,
            lastSeen = now,
            packetCount = (previous?.packetCount ?: 0) + 1,
            bondState = runCatching { result.device.bondState }.getOrDefault(
                android.bluetooth.BluetoothDevice.BOND_NONE
            )
        )
        cache[address] = item
        if (cache.size > 300) {
            val oldest = cache.values.minByOrNull { it.lastSeen }
            if (oldest != null) cache.remove(oldest.address)
        }
        if (!pendingRefresh) {
            pendingRefresh = true
            handler.postDelayed(refresh, 500L)
        }
    }
}
