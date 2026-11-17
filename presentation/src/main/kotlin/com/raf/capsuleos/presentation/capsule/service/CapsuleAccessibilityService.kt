package com.raf.capsuleos.presentation.capsule.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Accessibility overlay host declared with `android:isAccessibilityTool="true"` so
 * `TYPE_ACCESSIBILITY_OVERLAY` renders directly over the camera cutout and status bar
 * at the very top of the screen while keeping Advanced Protection active.
 */
@AndroidEntryPoint
class CapsuleAccessibilityService : AccessibilityService() {

    @Inject lateinit var overlayController: CapsuleOverlayController

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayController.attachAccessibilityHost(
            serviceContext = this,
            onLockScreen = { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) },
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayController.onConfigurationChanged(this, newConfig)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        overlayController.detachAccessibilityHost()
        super.onDestroy()
    }
}
