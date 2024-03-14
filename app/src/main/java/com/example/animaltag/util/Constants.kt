package com.example.animaltag.util

import android.Manifest
import android.os.Build
import java.util.*

//For a RFComm connection to exist we use a hardcoded UUID
val myUuid: UUID = UUID.fromString("f37d28b2-615b-4d69-bc41-bc690009905d")

//Since the permissions needed for this app are fixed we define them here
val requiredPermissionsInitialClient =
    arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )