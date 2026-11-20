package com.rfcontrol.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Manual, user-confirmed camera review for Spectrum routers.
 * Android Wi-Fi scan results are access points, NOT connected clients.
 * Spectrum does not provide a general public local API for pausing arbitrary clients.
 */
class CameraGuardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "Camera Guard • Spectrum Wi-Fi"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "Review devices actually connected to YOUR Spectrum router. " +
                "Nearby Wi-Fi names and Bluetooth detections are not proof of a connected camera. " +
                "A device's name or manufacturer alone cannot confirm that it is a camera or recording.\n\n" +
                "1. Open My Spectrum and find your Wi-Fi network's connected devices.\n" +
                "2. Review any unfamiliar device, especially one identified as Ring, Wyze, Blink, or a camera.\n" +
                "3. Confirm it is not a device you own or recognize.\n" +
                "4. Use Spectrum's Pause or Block control for THAT device only, if your router supports it.\n\n" +
                "RF Control will never automatically disconnect devices. It cannot directly block " +
                "clients without an authorized router interface."
            textSize = 16f
        })
        root.addView(Button(this).apply {
            text = "OPEN MY SPECTRUM"
            setOnClickListener {
                val launch = packageManager.getLaunchIntentForPackage("com.brighthouse.mybhn")
                    ?: packageManager.getLaunchIntentForPackage("com.charter.spectrum")
                if (launch != null) startActivity(launch)
                else {
                    AlertDialog.Builder(this@CameraGuardActivity)
                        .setTitle("Open My Spectrum")
                        .setMessage("Open the My Spectrum app to review and pause connected devices. If it isn't installed, find it in your app store. The router may not offer per-device blocking.")
                        .setPositiveButton("OK", null).show()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "OPEN WI-FI SETTINGS"
            setOnClickListener { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "WHY CAN'T THIS APP BLOCK DIRECTLY?"
            setOnClickListener {
                AlertDialog.Builder(this@CameraGuardActivity)
                    .setTitle("Router authorization required")
                    .setMessage("Your phone cannot remove another device from Spectrum Wi-Fi just by scanning nearby networks. Spectrum's authorized router controls must perform the block. This screen does not claim to identify hidden cameras or intercept video.")
                    .setPositiveButton("Understood", null).show()
            }
        })
        setContentView(root)
    }
}
