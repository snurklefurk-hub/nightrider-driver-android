package com.nightrider.driver

import android.Manifest
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/** Launch the real native map screen without a NightRider account or production API calls. */
class VelaNavigationSmokeTest {
    @Test fun testNavigationScreenLaunchesInBothAppearances() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        val original = Prefs(context).aeroEnabled
        try {
            for (light in listOf(false,true)) {
                // Match Prefs' actual appearance key. Only emulator app-local state is changed.
                Prefs(context).aeroEnabled = light
                val activity = instrumentation.startActivitySync(Intent(context,VelaNavigationActivity::class.java)
                    .putExtra("test",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    fun texts(view: View): List<String> = when (view) {
                        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                        is TextView -> listOf(view.text.toString())
                        else -> emptyList()
                    }
                    val visible = texts(activity.window.decorView).joinToString("\n")
                    assertTrue(visible.contains("VELA · TEST"))
                    assertTrue(visible.contains("Start test"))
                    assertTrue(visible.contains("Save test ZIP"))
                    assertTrue(visible.contains("Speed unavailable") || visible.contains("mph"))
                    activity.finish()
                }
                instrumentation.waitForIdleSync()
            }
        } finally { Prefs(context).aeroEnabled = original }
    }
}
