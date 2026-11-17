package com.raf.capsuleos

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point initializing Dagger Hilt dependency graph.
 */
@HiltAndroidApp
class CapsuleApplication : Application()
