package com.raf.capsuleos.presentation.capsule.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.res.Configuration
import android.graphics.Rect
import android.view.WindowManager
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
        runCatching {
            overlayController.attachAccessibilityHost(
                serviceContext = this,
                onLockScreen = { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) },
                onOpenNotificationShade = { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) },
            )
            val km = getSystemService(KeyguardManager::class.java)
            val qsOpen = detectQuickSettingsOpen(event = null)
            overlayController.setKeyguardLocked(km?.isKeyguardLocked == true)
            overlayController.setQuickSettingsOpen(qsOpen)
            overlayController.setFullscreenMode(detectImmersiveFullscreen(qsOpen))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        runCatching {
            overlayController.onConfigurationChanged(this, newConfig)
            val qsOpen = detectQuickSettingsOpen(event = null)
            overlayController.setQuickSettingsOpen(qsOpen)
            overlayController.setFullscreenMode(detectImmersiveFullscreen(qsOpen))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching {
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
                val qsOpen = detectQuickSettingsOpen(event)
                overlayController.setKeyguardLocked(km?.isKeyguardLocked == true)
                overlayController.setQuickSettingsOpen(qsOpen)
                overlayController.setFullscreenMode(detectImmersiveFullscreen(qsOpen))
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        lastEventShadeOpen = false
        overlayController.setQuickSettingsOpen(false)
        overlayController.setFullscreenMode(false)
        overlayController.detachAccessibilityHost()
        super.onDestroy()
    }

    private fun detectQuickSettingsOpen(event: AccessibilityEvent?): Boolean {
        val km = getSystemService(KeyguardManager::class.java) ?: return false
        val dm = resources.displayMetrics
        val minWidePx = (dm.widthPixels * 0.85f).roundToInt()
        val minTallPx = (dm.heightPixels * 0.40f).roundToInt()
        val bounds = Rect()
        val winList = runCatching { windows.orEmpty() }.getOrDefault(emptyList())
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

    /**
     * Detects immersive fullscreen apps (gaming, video playback, fullscreen readers) across both
     * Portrait and Landscape orientations by verifying a full-display application window is active
     * while the system StatusBar window is hidden.
     */
    private fun detectImmersiveFullscreen(isQsOpen: Boolean): Boolean {
        if (isQsOpen) return false
        val km = getSystemService(KeyguardManager::class.java) ?: return false
        if (km.isKeyguardLocked) return false

        val winList = runCatching { windows.orEmpty() }.getOrDefault(emptyList())
        if (winList.isEmpty()) return false

        val wmBounds = getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds
        val dm = resources.displayMetrics
        val screenW = wmBounds?.width()?.takeIf { it > 0 } ?: dm.widthPixels
        val screenH = wmBounds?.height()?.takeIf { it > 0 } ?: dm.heightPixels
        val maxStatusBarHeightPx = (72f * dm.density.coerceAtLeast(1f)).roundToInt()
        val bounds = Rect()

        var hasStatusBarWindow = false
        var hasFullscreenAppWindow = false

        winList.forEach { win ->
            win.getBoundsInScreen(bounds)
            when (win.type) {
                AccessibilityWindowInfo.TYPE_SYSTEM -> {
                    val title = win.title?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
                    val isExcluded = title.contains("navigation") ||
                        title.contains("volume") ||
                        title.contains("screendecor") ||
                        title.contains("shade") ||
                        title.contains("notification") ||
                        title.contains("quick")
                    if (!isExcluded) {
                        val isNamedStatusBar = title == "statusbar" ||
                            title == "status bar" ||
                            title.contains("status bar") ||
                            title.contains("status_bar")
                        val isTopBarGeometry = bounds.top <= 4 &&
                            bounds.height() in 1..maxStatusBarHeightPx &&
                            bounds.width() >= (screenW * 0.75f).roundToInt()
                        if (isNamedStatusBar || isTopBarGeometry) {
                            hasStatusBarWindow = true
                        }
                    }
                }
                AccessibilityWindowInfo.TYPE_APPLICATION -> {
                    val coversDisplay = bounds.top <= 8 &&
                        bounds.left <= 8 &&
                        bounds.right >= screenW - 8 &&
                        bounds.bottom >= screenH - 8
                    if (coversDisplay) {
                        val pkg = runCatching { win.root?.packageName?.toString().orEmpty() }.getOrDefault("")
                        val isExcludedPkg = pkg == packageName ||
                            pkg == SYSTEM_UI_PACKAGE ||
                            pkg.contains("launcher", ignoreCase = true) ||
                            pkg == "com.miui.home"
                        if (!isExcludedPkg) {
                            hasFullscreenAppWindow = true
                        }
                    }
                }
            }
        }

        return hasFullscreenAppWindow && !hasStatusBarWindow
    }

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
