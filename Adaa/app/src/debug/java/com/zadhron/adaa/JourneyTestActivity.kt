package com.zadhron.adaa

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

/** Dedicated, non-exported debug host. Tests never initialize the live Auth client. */
class JourneyTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Sample-only instrumentation host can render while the phone is locked.
        // MainActivity and release builds retain the user's normal lock-screen protection.
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }
}
