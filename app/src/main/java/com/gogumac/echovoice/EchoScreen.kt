package com.gogumac.echovoice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gogumac.echovoice.ui.theme.EchoVoiceTheme

// A simple way to track service state from Compose.
// For more robust state management, consider a ViewModel or observing service state via LiveData/Flow.
object EchoServiceManager {
    var isServiceRunning = mutableStateOf(false) // Observable state
}

@Composable
fun EchoScreen(modifier: Modifier=Modifier) {
    val context = LocalContext.current
    var isServiceRunning by remember { EchoServiceManager.isServiceRunning }
    var echoDelay by remember { mutableStateOf(500f) }

    val requiredPermissions = remember {
        mutableListOf<String>().apply {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        var allGranted = true
        permissionsResult.entries.forEach {
            if (!it.value) {
                allGranted = false
                if (!shouldShowRationale(context, it.key)) {
                    // User selected "Don't ask again" or permission is permanently denied
                    showSettingsDialog(context, it.key)
                }
            }
        }
        if (allGranted) {
            startEchoService(context)
        } else {
            Toast.makeText(context, "Permissions denied. Echo cannot start.", Toast.LENGTH_LONG).show()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {


            // Echo Delay Slider UI
            Text("Echo Delay: ${echoDelay.toInt()} ms", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = echoDelay,
                onValueChange = { echoDelay = it },
                valueRange = 100f..3000f,
                steps = 28, // Creates steps at every 100ms increment from 100ms to 3000ms
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    checkPermissionsAndStartService(context, requiredPermissions, permissionsLauncher)
                },
                enabled = !isServiceRunning
            ) {
                Text("Start Echo")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    stopEchoService(context)
                },
                enabled = isServiceRunning
            ) {
                Text("Stop Echo")
            }
        }
    }
}

private fun checkPermissionsAndStartService(
    context: Context,
    permissions: Array<String>,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val permissionsToRequest = permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    if (permissionsToRequest.isEmpty()) {
        startEchoService(context)
    } else {
        launcher.launch(permissionsToRequest.toTypedArray())
    }
}

private fun startEchoService(context: Context) {
    if (!EchoServiceManager.isServiceRunning.value) {
        Log.d("EchoScreen", "Attempting to start EchoService...")

        val serviceIntent = Intent(context, EchoService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("checkfor","start>>>")
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        Log.d("EchoScreen", "Called startForegroundService/startService for EchoService.")
        EchoServiceManager.isServiceRunning.value = true
        Toast.makeText(context, "Echo service started", Toast.LENGTH_SHORT).show()
    }
}

private fun stopEchoService(context: Context) {
    if (EchoServiceManager.isServiceRunning.value) {
        val serviceIntent = Intent(context, EchoService::class.java)
        context.stopService(serviceIntent)
        EchoServiceManager.isServiceRunning.value = false
        Toast.makeText(context, "Echo service stopped", Toast.LENGTH_SHORT).show()
    }
}

// Helper to check if we should show rationale (simplified)
private fun shouldShowRationale(context: Context, permission: String): Boolean {
    // In a real app, you'd use ActivityCompat.shouldShowRequestPermissionRationale
    // This requires an Activity, so for simplicity in a Composable, we're skipping
    // the more complex rationale dialog flow here.
    // For a production app, integrate this properly.
    return false // Default to false to trigger settings dialog if denied once
}

private fun showSettingsDialog(context: Context, permission: String) {
    // In Compose, you'd typically use an AlertDialog Composable
    // For simplicity, using a Toast here. A real app should guide the user to settings.
    Toast.makeText(
        context,
        "Permission $permission permanently denied. Please enable it in app settings.",
        Toast.LENGTH_LONG
    ).show()

    // Intent to open app settings
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    val uri = Uri.fromParts("package", context.packageName, null)
    intent.data = uri
    context.startActivity(intent)
}

@Preview
@Composable
fun EchoScreenPreview(){
    EchoVoiceTheme {
        EchoScreen(Modifier)
    }
}