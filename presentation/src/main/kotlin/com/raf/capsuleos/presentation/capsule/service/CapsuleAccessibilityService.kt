package com.raf.capsuleos.presentation.capsule.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.res.Configuration
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Accessibility overlay host declared with `android:isAccessibilityTool="true"` so
 * `TYPE_ACCESSIBILITY_OVERLAY` renders directly over the camera cutout and status bar
 * at the very top of the screen while keeping Advanced Protection active.
 */
@AndroidEntryPoint
class CapsuleAccessibilityService : AccessibilityService() {

    @Inject lateinit var overlayController: CapsuleOverlayController
    private var lastEventShadeOpen = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayController.attachAccessibilityHost(
            serviceContext = this,
            onLockScreen = { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) },
            onOpenNotificationShade = { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) },
        )
        val km = getSystemService(KeyguardManager::class.java)
        overlayController.setKeyguardLocked(km.isKeyguardLocked)
        overlayController.setQuickSettingsOpen(detectQuickSettingsOpen(event = null))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayController.onConfigurationChanged(this, newConfig)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString().orEmpty()
            val cls = event.className?.toString()?.lowercase(Locale.US).orEmpty()
            val textBlob = buildString {
                event.text.forEach { append(it).append(' ') }
                event.contentDescription?.let { append(it) }
            }.lowercase(Locale.US)

            if (pkg == SYSTEM_UI_PACKAGE) {
                val isShadeEvent = textBlob.contains("notification") ||
                    textBlob.contains("quick settings") ||
                    textBlob.contains("shade") ||
                    textBlob.contains("setelan cepat") ||
                    textBlob.contains("notifikasi") ||
                    textBlob.contains("クイック設定") ||
                    textBlob.contains("通知") ||
                    cls.contains("shade") ||
                    cls.contains("quicksettings") ||
                    cls.contains("qspanel") ||
                    cls.contains("statusbarwindow")
                if (isShadeEvent) {
                    lastEventShadeOpen = true
                }
            } else if (pkg.isNotEmpty() && pkg != packageName) {
                lastEventShadeOpen = false
            }
        }
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event == null
        ) {
            val km = getSystemService(KeyguardManager::class.java)
            overlayController.setKeyguardLocked(km.isKeyguardLocked)
            overlayController.setQuickSettingsOpen(detectQuickSettingsOpen(event))
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        lastEventShadeOpen = false
        overlayController.setQuickSettingsOpen(false)
        overlayController.detachAccessibilityHost()
        super.onDestroy()
    }

    private fun detectQuickSettingsOpen(event: AccessibilityEvent?): Boolean {
        val km = getSystemService(KeyguardManager::class.java)
        val dm = resources.displayMetrics
        val minWidePx = (dm.widthPixels * 0.85f).roundToInt()
        val minTallPx = (dm.heightPixels * 0.40f).roundToInt()
        val bounds = Rect()
        val winList = windows.orEmpty()
        if (winList.isNotEmpty()) {
            val shadeVisibleInWindows = winList.any { win ->
                if (win.type != AccessibilityWindowInfo.TYPE_SYSTEM) return@any false
                val title = win.title?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
                val excludedTitle = title == "statusbar" ||
                    title == "status bar" ||
                    title.contains("navigation") ||
                    title.contains("screendecor") ||
                    title.contains("volume")
                if (excludedTitle) return@any false
                win.getBoundsInScreen(bounds)
                val isNamedShade = title.contains("notificationshade") ||
                    title.contains("notification shade") ||
                    title.contains("quick settings") ||
                    title.contains("notification panel") ||
                    title.contains("setelan cepat") ||
                    title.contains("クイック設定") ||
                    title.contains("通知シェード")
                val isLargeSystemShade = bounds.width() >= minWidePx &&
                    bounds.height() >= minTallPx &&
                    bounds.top <= 32
                (isNamedShade && bounds.height() >= dm.heightPixels * 0.22f) ||
                    (!km.isKeyguardLocked && isLargeSystemShade)
            }
            if (!shadeVisibleInWindows) {
                lastEventShadeOpen = false
            }
            if (!km.isKeyguardLocked) {
                return shadeVisibleInWindows || lastEventShadeOpen
            }
        }
        if (!km.isKeyguardLocked && lastEventShadeOpen) {
            return true
        }
        val cls = event?.className?.toString()?.lowercase(Locale.US).orEmpty()
        return event?.packageName == SYSTEM_UI_PACKAGE &&
            (cls.contains("quicksettings") || cls.contains("qspanel"))
    }

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
