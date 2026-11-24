package dev.terminalplayer.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Result of a runtime permission request, plus a way to trigger it again. */
data class PermissionState(
    val granted: Boolean,
    val request: () -> Unit,
)

/** Permissions needed to index the local audio library (and to post the media notification). */
fun audioLibraryPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
    )
} else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** The permission that actually gates MediaStore access on the current API level. */
fun audioLibraryPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_AUDIO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

@Composable
fun rememberPermissionState(permission: String): PermissionState =
    rememberPermissionStateFor(
        requiredPermission = permission,
        requestedPermissions = arrayOf(permission),
    )

@Composable
fun rememberPermissionStateFor(
    requiredPermission: String,
    requestedPermissions: Array<String>,
): PermissionState {
    val context = LocalContext.current
    var granted by remember(requiredPermission) {
        mutableStateOf(context.hasPermission(requiredPermission))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = context.hasPermission(requiredPermission)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, requiredPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = context.hasPermission(requiredPermission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val request: () -> Unit = remember(launcher, requestedPermissions) {
        { launcher.launch(requestedPermissions) }
    }

    return remember(granted, request) { PermissionState(granted = granted, request = request) }
}
