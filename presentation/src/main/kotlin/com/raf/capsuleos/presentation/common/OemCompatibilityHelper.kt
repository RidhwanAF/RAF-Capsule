package com.raf.capsuleos.presentation.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.core.net.toUri

/**
 * OEM-specific compatibility utilities strictly scoped so stock Android / Pixel devices experience
 * zero behavioral changes while Xiaomi / Redmi / POCO (MIUI & HyperOS) devices receive safe
 * crash guards and one-tap background-persistence shortcuts.
 */
object OemCompatibilityHelper {

    /**
     * True only on Xiaomi, Redmi, or POCO hardware running MIUI / HyperOS.
     * Always false on Google Pixel and other AOSP/OEM devices.
     */
    val isXiaomiDevice: Boolean by lazy {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        manufacturer == "xiaomi" || brand == "xiaomi" || brand == "redmi" || brand == "poco"
    }

    /**
     * Safely resolves Material You [dynamicDarkColorScheme], falling back to [darkColorScheme]
     * only if an OEM theme engine (such as customized MIUI/HyperOS themes) throws on resource
     * lookup. On Pixel devices this always returns the exact dynamic dark color scheme.
     */
    fun safeDynamicDarkColorScheme(context: Context): ColorScheme =
        runCatching { dynamicDarkColorScheme(context) }.getOrElse { darkColorScheme() }

    /**
     * Returns true when the app is exempted from Doze / aggressive OEM battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(true)
    }

    /**
     * Opens Xiaomi MIUI / HyperOS AutoStart management so the system does not kill the
     * Accessibility overlay service after screen-off or RAM cleanup. Falls back to App Info.
     */
    fun openXiaomiAutoStart(context: Context) {
        val candidates = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            ),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
        )
        launchFirstResolvableOrAppDetails(context, candidates)
    }

    /**
     * Opens Xiaomi MIUI / HyperOS "Other Permissions" screen where users enable
     * "Display pop-up windows while running in the background" for overlay stability.
     */
    fun openXiaomiOtherPermissions(context: Context) {
        val candidates = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                )
                putExtra("extra_pkgname", context.packageName)
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", context.packageName)
            },
        )
        launchFirstResolvableOrAppDetails(context, candidates)
    }

    /**
     * Opens battery optimization settings (MIUI Battery Saver per-app page on Xiaomi, or standard
     * Android battery optimization list), falling back to App Info.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val candidates = buildList {
            if (isXiaomiDevice) {
                add(
                    Intent().apply {
                        setClassName(
                            "com.miui.powerkeeper",
                            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                        )
                        putExtra("package_name", context.packageName)
                        putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
                    },
                )
            }
            add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        launchFirstResolvableOrAppDetails(context, candidates)
    }

    /** Opens standard Android App Details settings page for this package. */
    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun launchFirstResolvableOrAppDetails(context: Context, candidates: List<Intent>) {
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
        openAppDetails(context)
    }
}
