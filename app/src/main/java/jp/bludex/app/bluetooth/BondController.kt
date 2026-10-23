package jp.bludex.app.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Initiates user-requested Android system bonding and observes asynchronous results. */
class BondController(private val context: Context) {
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE)
        as BluetoothManager).adapter
    private val _states = MutableStateFlow<Map<String, Int>>(emptyMap())
    val states = _states.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                ?: return
            val address = runCatching { device.address }.getOrNull() ?: return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            _states.value = _states.value + (address to state)
        }
    }
    init {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    @SuppressLint("MissingPermission")
    fun pair(address: String): String {
        if (adapter == null) return "Bluetoothに対応していません"
        return try {
            val device = adapter.getRemoteDevice(address)
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                _states.value = _states.value + (address to BluetoothDevice.BOND_BONDED)
                return "すでにペアリング済みです"
            }
            if (device.createBond()) {
                _states.value = _states.value + (address to BluetoothDevice.BOND_BONDING)
                "ペアリング要求を送信しました。Androidの確認画面に従ってください"
            } else {
                "ペアリング要求を開始できませんでした"
            }
        } catch (_: SecurityException) {
            "BLUETOOTH_CONNECT権限がありません"
        } catch (_: IllegalArgumentException) {
            "無効なBluetoothアドレスです"
        }
    }

    fun close() {
        runCatching { context.unregisterReceiver(receiver) }
    }
}
