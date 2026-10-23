package jp.bludex.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.bludex.app.bluetooth.BleScanner
import jp.bludex.app.bluetooth.BondController
import jp.bludex.app.data.BleDevice
import jp.bludex.app.data.DeviceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = BleScanner(application)
    private val store = DeviceStore(application)
    private val bonding = BondController(application)
    val devices = scanner.devices
    val scanState = scanner.state
    val history = store.history
    val bonds = bonding.states
    val hasBluetooth = scanner.available
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            scanner.devices.collect { store.observe(it) }
        }
    }

    fun bluetoothEnabled() = scanner.bluetoothEnabled()
    fun startScan() = scanner.start()
    fun stopScan() = scanner.stop()
    fun clearNearby() = scanner.clearNearby()
    fun toggleFavorite(address: String) {
        val current: BleDevice? = devices.value.firstOrNull { it.address == address }
        store.toggleFavorite(address, current)
    }
    fun rename(address: String, name: String) = store.rename(address, name)
    fun clearNonFavorites() = store.clearNonFavorites()
    fun pair(address: String) {
        _notice.value = bonding.pair(address)
    }
    fun dismissNotice() { _notice.value = null }

    override fun onCleared() {
        scanner.stop()
        store.flush()
        bonding.close()
        super.onCleared()
    }
}
