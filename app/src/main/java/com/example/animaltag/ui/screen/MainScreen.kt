package com.example.animaltag.ui.screen

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.animaltag.data.BluetoothService
import com.example.animaltag.ui.AppViewModel
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker

private const val MY_TAG = "MainScreen"

@SuppressLint("MissingPermission")
@Composable
fun MainScreen(
    viewModel: AppViewModel
) {
    val leScanResults = viewModel.leScanResults
    val selectedDevice = viewModel.selectedDevice
    val connecting = viewModel.connecting;
    val connected = viewModel.connected;
    val scanning = viewModel.scanning;
    val response = viewModel.udpResponse;

    if (viewModel.permissionGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Spacer(
                    modifier = Modifier
                        .padding(5.dp)
                )
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .alpha(if (scanning.value || connecting.value) 1f else 0f)
                )
                Spacer(
                    modifier = Modifier
                        .padding(5.dp)
                )
                Text(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .alpha(if (response.value != "") 1f else 0f),
                    text = "${response.value}"
                )
                Spacer(
                    modifier = Modifier
                        .padding(5.dp)
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(3.dp, MaterialTheme.colors.primaryVariant)
                        .weight(2f)
                ) {
                    Text(
                        text = "Discovered Tags",
                        style = TextStyle(textDecoration = TextDecoration.Underline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.padding(3.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(modifier = Modifier.width(110.dp), text = "Name")
                        Spacer(Modifier.weight(1f))
                        Text(text = "PHY")
                        Spacer(Modifier.weight(1f))
                        Text(text = "Steps")
                        Spacer(Modifier.weight(1f))
                        Text(text = "dBm")
                    }


                    LazyColumn(
                        modifier = Modifier
                            .heightIn(0.dp, 250.dp)
                            .padding(4.dp)
                            .weight(1f)
                            .alpha(if (leScanResults.isNotEmpty()) 1f else 0f)
                    ) {
                        items(leScanResults.sortedByDescending { it.scanResult.rssi })
                        { uiScanResult ->
                            ListedDeviceItem(
                                deviceName = uiScanResult.scanResult.device.name,
                                phy = uiScanResult.scanResult.primaryPhy,
                                steps = uiScanResult.steps,
                                rsi = uiScanResult.scanResult.rssi.toString(),
                                selected = viewModel.selectedDevice == uiScanResult.scanResult.device,
                            ) {
                                viewModel.selectDevice(uiScanResult.scanResult.device)
                            }
                        }
                    }

                    Text(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .alpha(if (connected.value) 1f else 0f),
                        text = "Connected"
                    )

                    val context = LocalContext.current
                    if (selectedDevice != null && !connected.value && !connecting.value)
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            onClick = {
                                viewModel.startBleConnect(context)
                            }) {
                            Text(text = "Connect to ${selectedDevice.name}")
                        }
                    if (connected.value) {
                        Button(
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxWidth(),
                            onClick = {
                                viewModel.bleCloseConnect()
                            }) {
                            Text(text = "Disconnect from ${selectedDevice?.name}")
                        }
                    }
                    Spacer(modifier = Modifier.padding(3.dp))
                }

                //spacer
                Spacer(modifier = Modifier.padding(10.dp))

                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    cameraPositionState = viewModel.cameraPositionState,
                ) {
                    Marker(
                        state = viewModel.markerPositionState,
                        title = "You are here",
                    )
                }


                Spacer(modifier = Modifier.padding(10.dp))

                Button(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .alpha(if (!connected.value && !connecting.value) 1f else 0f),
                    onClick = {
                        if (!scanning.value) viewModel.scanForDevices() else viewModel.stopScanningForDevices()
                    }) {
                    Text(text = "${if (!scanning.value) "Start" else "Stop"} scan")
                }

            }

        }
    }
}

@Composable
fun ListedDeviceItem(
    deviceName: String,
    phy: Int,
    steps: Int,
    rsi: String,
    selected: Boolean,
    onItemClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .clickable {
                onItemClick(deviceName)
            }
            .background(if (selected) MaterialTheme.colors.secondary else Color.Transparent)
            .fillMaxWidth()
            .padding(6.dp), elevation = 2.dp
    )
    {
        Column() {
            Row {
                Text(modifier = Modifier.width(110.dp), text = deviceName)
                Spacer(Modifier.weight(1f))
                if (phy == BluetoothDevice.PHY_LE_1M)
                {
                    Text(text = "LE_1M")
                } else if (phy == BluetoothDevice.PHY_LE_2M) {
                    Text(text = "LE_2M")
                } else if (phy == BluetoothDevice.PHY_LE_CODED) {
                    Text(text = "LE_CO")
                } else {
                    Text(text = "Unknown")
                }
                Spacer(Modifier.weight(1f))
                Text(text = steps.toString())
                Spacer(Modifier.weight(1f))
                Text(text = rsi)
            }


        }
    }
}