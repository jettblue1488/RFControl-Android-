package com.rfcontrol.app

import android.Manifest
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class MainActivityTest {
    private fun texts(v: View): List<TextView> = (if (v is TextView) listOf(v) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
    @Test fun launchHistoryAndPermissionDenialWorkWithoutRadioHardware() {
        val app: android.app.Application = RuntimeEnvironment.getApplication()
        app.deleteDatabase("detections.db")
        HistoryStore(app).withClosed { store ->
            val s = store.begin("BLE")
            store.record(s, "BLE", "AA:BB:CC:DD:EE:FF", "Test beacon", "advertisement", 1000, -60)
        }
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.findViewById<View>(android.R.id.content)
        fun click(title: String) { texts(root).filterIsInstance<Button>().first { it.text.toString() == title }.performClick() }
        click("History")
        assertTrue(texts(root).any { it.text.contains("Test beacon") && it.text.contains("#1") })
        click("Scan Bluetooth LE")
        assertTrue(texts(root).any { it.text.contains("Allow") })
        assertTrue(texts(root).any { it.text.contains("cannot establish exact distance or direction") })
        click("Stop finder sound")
        controller.pause().stop().destroy()
        HistoryStore(app).withClosed { assertEquals(1, it.rows().size) }
    }
}
