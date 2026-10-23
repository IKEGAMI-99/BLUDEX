package jp.bludex.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import jp.bludex.app.ui.BludexApp
import jp.bludex.app.ui.BludexTheme

class MainActivity : ComponentActivity() {
    private val model: ExplorerViewModel by viewModels()
    private var permissionsReady by mutableStateOf(false)
    private var bluetoothReady by mutableStateOf(false)
    private var startedOnce = false

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshStatus()
        if (permissionsReady) enableBluetoothOrScan()
    }
    private val requestBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStatus()
        if (permissionsReady && bluetoothReady) model.startScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshStatus()
        setContent {
            BludexTheme {
                BludexApp(
                    viewModel = model,
                    permissionsReady = permissionsReady,
                    bluetoothReady = bluetoothReady,
                    onScanRequest = ::enableBluetoothOrScan
                )
            }
        }
        if (permissionsReady && bluetoothReady) {
            model.startScan()
            startedOnce = true
        }
    }

    private fun permissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasPermissions(): Boolean = permissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshStatus() {
        permissionsReady = hasPermissions()
        bluetoothReady = permissionsReady && model.bluetoothEnabled()
    }

    private fun enableBluetoothOrScan() {
        refreshStatus()
        when {
            !model.hasBluetooth -> Unit
            !permissionsReady -> requestPermissions.launch(permissions())
            !bluetoothReady -> requestBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> {
                model.startScan()
                startedOnce = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (startedOnce && permissionsReady && bluetoothReady) model.startScan()
    }

    override fun onStop() {
        model.stopScan()
        super.onStop()
    }
}
