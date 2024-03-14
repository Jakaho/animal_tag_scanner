package com.example.animaltag

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import com.example.animaltag.data.BluetoothService
import com.example.animaltag.ui.AppViewModel
import com.example.animaltag.ui.screen.MainScreen
import com.example.animaltag.ui.theme.AnimalTagTheme
import com.example.animaltag.util.askPermissions
import com.example.animaltag.util.requiredPermissionsInitialClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var viewModel: AppViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            enableBluetooth()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        viewModel = AppViewModel(BluetoothService(bluetoothAdapter, this), fusedLocationClient)

        viewModel.getLatestLocation()

        setContent {
            AnimalTagTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    askPermissions(
                        requiredPermissions = requiredPermissionsInitialClient,
                        viewModel = viewModel
                    )
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            enableBluetooth()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopScanningForDevices()
        viewModel.bleCloseConnect()

    }

    private val enableBluetoothResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth Enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth is required for this app to run", Toast.LENGTH_SHORT)
                .show()
            this.finish()
        }
    }

    /**
     * Pop-up activation for enabling Bluetooth
     *
     */
    private fun enableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothResultLauncher.launch(enableBtIntent)
    }

    private fun enableGPS() {
        val enableGpsIntent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(enableGpsIntent)
    }

    companion object {
        private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
    }
}

