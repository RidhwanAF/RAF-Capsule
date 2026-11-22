package com.raf.capsuleos.presentation.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import rikka.shizuku.Shizuku

/** Live connection and authorization status of the Shizuku privileged service. */
enum class ShizukuStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    READY,
}

/**
 * Helper encapsulating Shizuku availability, permission requests, and one-tap
 * `WRITE_SECURE_SETTINGS` granting without requiring a PC ADB connection.
 */
object ShizukuPermissionHelper {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_REQUEST_CODE = 0x5A12
    private const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"

    /** Returns true if the Shizuku manager app is installed on this device. */
    fun isShizukuInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Evaluates current [ShizukuStatus] cleanly without throwing on dead binders. */
    fun getStatus(context: Context): ShizukuStatus {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return if (isShizukuInstalled(context)) {
                ShizukuStatus.NOT_RUNNING
            } else {
                ShizukuStatus.NOT_INSTALLED
            }
        }
        val granted = runCatching {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return if (granted) ShizukuStatus.READY else ShizukuStatus.PERMISSION_REQUIRED
    }

    /** Triggers the native Shizuku authorization prompt. */
    fun requestPermission(requestCode: Int = SHIZUKU_REQUEST_CODE) {
        runCatching {
            if (!Shizuku.isPreV11()) {
                Shizuku.requestPermission(requestCode)
            }
        }
    }

    /**
     * Grants `android.permission.WRITE_SECURE_SETTINGS` to this package via Shizuku's privileged
     * shell process. Returns true when `pm grant` exits with code 0.
     */
    fun grantWriteSecureSettings(context: Context): Boolean = runCatching {
        if (getStatus(context) != ShizukuStatus.READY) return false
        val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
        val cmd = arrayOf(
            "pm",
            "grant",
            context.packageName,
            Manifest.permission.WRITE_SECURE_SETTINGS,
        )
        val process = newProcessMethod.invoke(null, cmd, null, null) as? Process ?: return false
        val exitCode = process.waitFor()
        runCatching { process.destroy() }
        exitCode == 0
    }.getOrDefault(false)

    /** Opens the Shizuku app if installed, or opens the official Shizuku download page. */
    fun openShizukuOrDownload(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launchIntent) }
            return
        }
        val webIntent = Intent(Intent.ACTION_VIEW, SHIZUKU_DOWNLOAD_URL.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(webIntent) }
    }
}
