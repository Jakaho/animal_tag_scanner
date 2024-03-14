package com.example.animaltag.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.example.animaltag.ui.AppViewModel

const val TAG = "Permissions"

/**
 * General function for requesting permissions from an array
 *
 * @param multiplePermissionLauncher
 * An ActivityResultLauncher passed to be invoked using 'multiplePermissionLauncher.launch(requiredPermissions)'
 *
 * @param requiredPermissions
 * An array of Strings defining permissions
 *
 * @param context
 * In order to invoke ActivityCompat.checkSelfPermission we require a context to be passed down
 *
 * @param actionIfAlreadyGranted
 * A function that will be invoked if permissions are already granted
 *
 */
@Composable
fun askPermissions(
    requiredPermissions: Array<String>,
    viewModel: AppViewModel,
) {
    val context = LocalContext.current

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                viewModel.permissionGranted = true;
            } else {
                // Permission Denied: Do something
                Log.i(TAG, "Permission Denied")
                Toast.makeText(
                    context,
                    "You must manually select the option 'Allow all the time' for location in order for this app to work!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    val multiplePermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            Log.i(TAG, "Launcher result: $permissions")
            if (permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
                //permission for location was granted.
                //we direct the user to select "Allow all the time option"
                Toast.makeText(
                    context,
                    "You must select the option 'Allow all the time' for the app to work",
                    Toast.LENGTH_SHORT
                ).show()
                askSinglePermission(
                    locationPermissionLauncher,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    context
                ) {
                    viewModel.permissionGranted = true;
                }
            } else {
                Toast.makeText(
                    context,
                    "Location permission was not granted. Please do so manually",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    if (!hasPermissions(requiredPermissions, context)) {
        //Launching multiple contract permission launcher for ALL the required permissions
        SideEffect {
            multiplePermissionLauncher.launch(requiredPermissions)
        }
    } else {
        viewModel.permissionGranted = true
        //All permissions are already granted
    }
}

/**
 * Use to ask a single Permission
 *
 * @param singlePermissionLauncher
 * @param permission
 * @param context
 * @param actionIfAlreadyGranted
 */
fun askSinglePermission(
    singlePermissionLauncher: ActivityResultLauncher<String>,
    permission: String,
    context: Context,
    actionIfAlreadyGranted: () -> Unit
) {
    if (!hasSinglePermission(permission, context)) {
        //Launching contract permission launcher for the required permissions
        singlePermissionLauncher.launch(permission)
    } else {
        //Permission is already granted so we execute the actionIfAlreadyGranted
        actionIfAlreadyGranted()
    }
}

/**
 * Checks if all permissions are granted. Returns true is all are granted or false if at least one permission is not granted
 */
fun hasPermissions(permissions: Array<String>, context: Context): Boolean {
    for (permission in permissions) {
        if (ActivityCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
    }
    return true
}


/**
 * Checks if a specific permission is granted
 */
fun hasSinglePermission(permission: String, context: Context): Boolean {
    return ActivityCompat.checkSelfPermission(
        context,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Check if location services are enabled
 *
 * @param context
 * @return
 */
fun isLocationEnabled(context: Context): Boolean {
    val locationManager =
        context.getSystemService(ComponentActivity.LOCATION_SERVICE) as LocationManager
    return locationManager.isLocationEnabled
}

/**
 * Take the user to location settings to enable location services
 *
 * @param activity
 */
fun enableLocation(activity: Activity) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    activity.startActivity(intent)
}

