
package com.rfcontrol.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var output: TextView
    private var scanning = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            runOnUiThread {
                val name = result.scanRecord?.deviceName
                    ?: "Unknown device"
                output.append(
                    "\n$name | ${result.device.address}" +
                    " | ${result.rssi} dBm"
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        fun button(title: String, action: () -> Unit) {
            layout.addView(Button(this).apply {
                text = title
                setOnClickListener { action() }
            })
        }

        button("Scan Bluetooth") { scanBluetooth() }
        button("Scan Wi-Fi") { scanWifi() }
        button("Bluetooth Settings") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        button("Wi-Fi Settings") {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        button("Airplane Mode Settings") {
            startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        }
        button("Mobile Network Settings") {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }

        output = TextView(this).apply {
            text = "RF Control ready\n"
            textSize = 15f
        }

        val scroll = ScrollView(this)
        scroll.addView(output)
        layout.addView(scroll)

        setContentView(layout)

        requestPermissions(
            if (Build.VERSION.SDK_INT >= 31)
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            else
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            100
        )
    }

    private fun scanBluetooth() {
        val permission = if (Build.VERSION.SDK_INT >= 31)
            Manifest.permission.BLUETOOTH_SCAN
        else Manifest.permission.ACCESS_FINE_LOCATION

        if (checkSelfPermission(permission) !=
            PackageManager.PERMISSION_GRANTED) {
            output.text = "Bluetooth scan permission required"
            return
        }

        val manager = getSystemService(BLUETOOTH_SERVICE)
            as BluetoothManager
        val scanner = manager.adapter?.bluetoothLeScanner

        if (scanner == null) {
            output.text = "Bluetooth unavailable or disabled"
            return
        }

        if (scanning) {
            scanner.stopScan(callback)
            scanning = false
            output.append("\nScan stopped")
        } else {
            output.text = "Scanning Bluetooth LE devices...\n"
            scanner.startScan(callback)
            scanning = true
        }
    }

    private fun scanWifi() {
        if (checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            output.text = "Location permission required"
            return
        }

        val wifi = applicationContext.getSystemService(
            WIFI_SERVICE
        ) as WifiManager

        val networks = wifi.scanResults
        output.text = "Available Wi-Fi scan results:\n"

        for (network in networks) {
            output.append(
                "\n${network.SSID} | " +
                "${network.frequency} MHz | " +
                "${network.level} dBm"
            )
        }

        if (networks.isEmpty()) {
            output.append(
                "\nNo scan results available. " +
                "Enable Wi-Fi and Location."
            )
        }
    }

    override fun onDestroy() {
        if (scanning) {
            val manager = getSystemService(BLUETOOTH_SERVICE)
                as BluetoothManager
            manager.adapter?.bluetoothLeScanner?.stopScan(callback)
        }
        super.onDestroy()
    }
}
