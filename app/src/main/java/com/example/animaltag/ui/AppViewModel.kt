package com.example.animaltag.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.example.animaltag.data.BluetoothService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.MarkerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*


const val TAG = "AppViewModel"

class AppViewModel(
    private val bluetoothService: BluetoothService,
    private val fusedLocationClient: FusedLocationProviderClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    var leScanResults = bluetoothService.uiScanResults
        private set

    var selectedDevice by mutableStateOf<BluetoothDevice?>(null)
        private set

    var connecting = bluetoothService.uiConnecting

    var connected = bluetoothService.uiConnected

    val scanning = bluetoothService.uiScanning

    var udpResponse = bluetoothService.udpResponse

    var permissionGranted by mutableStateOf(false)

    var currentLocation by mutableStateOf(LatLng(0.0, 0.0))

    var cameraPositionState by mutableStateOf(CameraPositionState())

    var markerPositionState by mutableStateOf(MarkerState())


    fun scanForDevices() {
        //SCANNING WITH BLE
        selectedDevice = null
        getLatestLocation { position ->
            bluetoothService.scanLeDevice(position)
        }
    }

    fun stopScanningForDevices() {
        //SCANNING WITH BLE
        bluetoothService.stopScanningLeDevice()
    }

    fun selectDevice(device: BluetoothDevice) {
        selectedDevice = device
    }

    fun startBleConnect(context: Context) {
        Log.d(TAG, "startBleConnect: $selectedDevice")
        bluetoothService.startConnect(context, selectedDevice)
    }

    @SuppressLint("MissingPermission")
    fun getLatestLocation(callback: (position: LatLng) -> Unit = {}) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                // Use the location if it's not null, otherwise use 0.0, 0.0
                val latLng = if (location != null) {
                    LatLng(location.latitude, location.longitude)
                } else {
                    LatLng(0.0, 0.0) // Default to 0.0, 0.0 if location is null
                }

                // Update states with the new or default location
                currentLocation = latLng
                cameraPositionState = CameraPositionState(
                    CameraPosition.Builder()
                        .target(latLng)
                        .zoom(15f)
                        .build()
                )
                markerPositionState = MarkerState(latLng)

                // Invoke the callback with the new or default location
                callback(latLng)
            }
    }


    fun bleCloseConnect() {
        bluetoothService.closeCurrentGatt()
    }
}


