package com.rfcontrol.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@SuppressLint("MissingPermission", "SetTextI18n")
class MainActivity : Activity() {
    private lateinit var history: HistoryStore
    private lateinit var screenScroll: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var finder: TextView
    private lateinit var strength: ProgressBar
    private lateinit var bluetoothButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private var bleSession: Long? = null
    private var visibleSession: Long? = null
    private var showingHistory = false
    private var historyLimit = 100
    private var selected: String? = null
    private var selectedName = ""
    private var smoothed: Double? = null
    private var lastSeen = 0L
    private var nextBeep = 0L
    private var tone: ToneGenerator? = null
    private var wifiSession: Long? = null
    private var wifiRequestedAt = 0L
    private var receiverRegistered = false
    private var lastRender = 0L
    private val refresh = Runnable { render() }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            runOnUiThread {
                if (!scanning) return@runOnUiThread
                try {
                    val id = result.device.address
                    val name = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                        ?: result.device.name.orEmpty()
                    val record = result.scanRecord
                    val manufacturers = record?.manufacturerSpecificData
                    val codes = (0 until (manufacturers?.size() ?: 0)).joinToString { "0x%04X".format(manufacturers!!.keyAt(it)) }
                    val detail = "Bluetooth LE; advertised manufacturer IDs: ${codes.ifBlank { "unavailable" }}; services: ${record?.serviceUuids?.joinToString() ?: "unavailable"}"
                    history.record(bleSession ?: return@runOnUiThread, "BLE", id, name, detail, System.currentTimeMillis(), result.rssi)
                    if (id == selected && Signal.valid(result.rssi)) {
                        smoothed = Signal.smooth(smoothed, result.rssi)
                        lastSeen = SystemClock.elapsedRealtime()
                    }
                    handler.removeCallbacks(refresh)
                    if (SystemClock.elapsedRealtime() - lastRender > 500) render()
                    else handler.postDelayed(refresh, 500)
                } catch (_: SecurityException) { stopBluetooth(); status.text = "Bluetooth permission was removed. Grant permission and scan again." }
            }
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { onScanResult(0, it) } }
        override fun onScanFailed(errorCode: Int) {
            runOnUiThread { stopBluetooth(); status.text = "Bluetooth scan failed (code $errorCode). Wait briefly before retrying." }
        }
    }
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION || wifiSession == null) return
            val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            receiveWifi(updated)
        }
    }
    private val wifiTimeout = Runnable { if (wifiSession != null) receiveWifi(false) }
    private val bleTimeout = Runnable { stopBluetooth(); status.text = "Bluetooth scan ended after 2 minutes. Saved in History. Start another scan to continue." }
    private val tick = object : Runnable {
        override fun run() {
            updateFinder()
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = HistoryStore(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        root.addView(TextView(this).apply { text = "Kay's RF Shield • Detection & Finder"; textSize = 21f })
        val controls = LinearLayout(this)
        fun button(parent: LinearLayout, title: String, action: () -> Unit): Button = Button(this).apply {
            text = title; setOnClickListener { action() }; parent.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
        }
        bluetoothButton = button(controls, "Scan Bluetooth LE") { if (scanning) { stopBluetooth(); status.text = "Bluetooth scan stopped. History saved." } else startBluetooth() }
        button(controls, "Scan Wi-Fi") { scanWifi() }
        root.addView(controls)
        val navigation = LinearLayout(this)
        button(navigation, "Live results") { showingHistory = false; render() }
        button(navigation, "History") { showingHistory = true; historyLimit = 100; render() }
        button(navigation, "Settings") {
            AlertDialog.Builder(this).setItems(arrayOf("Permissions", "Bluetooth", "Wi-Fi", "Location", "Clear saved history")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
                    1 -> startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    2 -> startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    3 -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    4 -> AlertDialog.Builder(this).setMessage("Delete all saved detection history?").setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ -> stopBluetooth(); wifiSession = null; handler.removeCallbacks(wifiTimeout); history.clear(); visibleSession = null; render() }.show()
                }
            }.show()
        }
        root.addView(navigation)
        root.addView(Button(this).apply {
            text = "Privacy Shield • Block tracker connections"
            setOnClickListener { showPrivacyShield() }
            contentDescription = "Privacy Shield. Review Bluetooth, tracker alerts, and location privacy controls."
        })
        root.addView(TextView(this).apply { text = Signal.WARNING; textSize = 13f })
        status = TextView(this).apply { text = "Ready. Scans run while this screen is open. Names and identifiers may be missing or randomized." }
        root.addView(status)
        finder = TextView(this).apply { textSize = 17f }
        root.addView(finder)
        strength = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; contentDescription = "Relative signal strength, not distance or direction" }
        root.addView(strength)
        root.addView(Button(this).apply { text = "Stop finder sound"; setOnClickListener { clearFinder() } })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
            // Keep all controls reachable on small screens and outside system bars.
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        screenScroll = scroll
        setContentView(scroll)
        scroll.requestApplyInsets()
        render()
        updateFinder()
    }

    private fun showPrivacyShield() {
        AlertDialog.Builder(this)
            .setTitle("Privacy Shield")
            .setMessage(
                "Protect your phone using Android's system controls. Turning off your phone's Bluetooth " +
                "stops its Bluetooth connections, but cannot disable an independent GPS tracker or a " +
                "Bluetooth tag communicating through other phones. This app does not jam, scramble or " +
                "control nearby devices.\n\n" +
                "For suspected tracking, use Android's Unknown tracker alerts and Scan now. " +
                "Keep alerts enabled so Android can notify you about compatible tags traveling with you."
            )
            .setItems(arrayOf(
                "Bluetooth settings — turn off or forget devices",
                "Unknown tracker alerts — open Settings for Scan now",
                "Location settings — review location access",
                "App permissions — review RF Control permissions"
            )) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    1 -> AlertDialog.Builder(this)
                        .setTitle("Find unknown trackers")
                        .setMessage(
                            "In Settings, open Safety & emergency > Unknown tracker alerts > Scan now. " +
                            "On some phones the path is Google > Personal & device safety > Unknown tracker alerts. " +
                            "Not all GPS trackers broadcast Bluetooth signals or appear in a scan."
                        )
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Open Settings") { _, _ ->
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }.show()
                    2 -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    3 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:$packageName")))
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun permissions(bluetooth: Boolean): Boolean {
        val needed = if (bluetooth && Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            else if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (needed.all { has(it) }) return true
        requestPermissions(needed, 100)
        status.text = "Allow ${if (bluetooth && Build.VERSION.SDK_INT >= 31) "Nearby devices and precise Location" else "precise Location"}, then tap Scan again. Location services may also need to be enabled."
        return false
    }
    private fun startBluetooth() {
        if (!permissions(true)) return
        try {
            val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
            val available = adapter?.bluetoothLeScanner
            if (available == null) { status.text = "Bluetooth LE unavailable or Bluetooth disabled. Enable Bluetooth in Settings."; return }
            scanner = available
            bleSession = history.begin("BLE")
            visibleSession = bleSession
            showingHistory = false
            scanning = true
            bluetoothButton.text = "Stop Bluetooth LE"
            available.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(), callback)
            handler.postDelayed(bleTimeout, 120_000)
            status.text = "Scanning Bluetooth LE advertisements for up to 2 minutes. Tap a device to find its signal."
            render()
        } catch (_: SecurityException) { stopBluetooth(); status.text = "Bluetooth permission required. Enable Nearby devices in Settings." }
          catch (_: IllegalStateException) { stopBluetooth(); status.text = "Bluetooth is unavailable. Enable it and try again." }
    }
    private fun stopBluetooth() {
        scanning = false
        handler.removeCallbacks(bleTimeout)
        try { scanner?.stopScan(callback) } catch (_: SecurityException) { } catch (_: IllegalStateException) { }
        scanner = null
        if (::bluetoothButton.isInitialized) bluetoothButton.text = "Scan Bluetooth LE"
        clearFinder()
    }
    private fun clearFinder() {
        selected = null; smoothed = null; lastSeen = 0; nextBeep = 0
        tone?.stopTone()
        if (::finder.isInitialized) updateFinder()
    }
    private fun select(row: Detection) {
        if (!scanning) startBluetooth()
        if (!scanning) return
        selected = row.identifier; selectedName = row.name
        smoothed = null; lastSeen = 0; nextBeep = 0
        // Never beep from a historical reading. Wait for a new advertisement.
        updateFinder()
        screenScroll.smoothScrollTo(0, finder.top)
    }
    private fun updateFinder() {
        val id = selected
        val now = SystemClock.elapsedRealtime()
        if (id == null) { finder.text = "Finder: select a Bluetooth LE device below."; strength.progress = 0; return }
        if (!scanning || !Signal.fresh(lastSeen, now)) {
            finder.text = "Finder: $selectedName\n$id\nWaiting for a fresh signal — sound paused."
            strength.progress = 0; tone?.stopTone(); nextBeep = 0; return
        }
        val rssi = smoothed!!.roundToInt()
        val interval = Signal.interval(rssi)
        strength.progress = ((rssi.coerceIn(-100, -40) + 100) * 100 / 60)
        finder.text = "Finder: $selectedName\n$id\n${Signal.label(rssi)} • $rssi dBm • beep every ${interval}ms\nFaster = stronger signal; slower = weaker. Phone media volume controls sound."
        if (now >= nextBeep) {
            try {
                if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
            } catch (_: RuntimeException) { status.text = "Audio unavailable; use the signal strength bar." }
            nextBeep = now + interval
        } else {
            // A newly stronger signal should accelerate immediately, not wait for the old slow interval.
            nextBeep = minOf(nextBeep, now + interval)
        }
    }

    @Suppress("DEPRECATION")
    private fun scanWifi() {
        if (!permissions(false)) return
        if (wifiSession != null) { status.text = "A Wi-Fi scan is already pending."; return }
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        try {
            if (!wifi.isWifiEnabled) { status.text = "Enable Wi-Fi and Location in Settings, then scan again."; return }
            wifiSession = history.begin("Wi-Fi")
            visibleSession = wifiSession; showingHistory = false
            wifiRequestedAt = SystemClock.elapsedRealtime()
            if (wifi.startScan()) {
                status.text = "Waiting for Wi-Fi scan results. Android limits scan frequency."
                handler.postDelayed(wifiTimeout, 15_000)
            } else receiveWifi(false)
            render()
        } catch (_: SecurityException) { wifiSession = null; status.text = "Wi-Fi scan needs precise Location permission and Location enabled." }
    }
    @Suppress("DEPRECATION")
    private fun receiveWifi(updated: Boolean) {
        val session = wifiSession ?: return
        wifiSession = null
        handler.removeCallbacks(wifiTimeout)
        try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val now = System.currentTimeMillis()
            val elapsed = SystemClock.elapsedRealtime()
            val results = wifi.scanResults
            var cached = false
            results.forEach { network ->
                val seenElapsed = network.timestamp / 1000
                val validTime = seenElapsed in 1..elapsed
                val fresh = updated && validTime && seenElapsed >= wifiRequestedAt
                if (!fresh) cached = true
                val seenAt = if (validTime) now - (elapsed - seenElapsed) else now
                history.record(session, "Wi-Fi", network.BSSID ?: "Identifier unavailable: ${network.SSID}/${network.frequency}", network.SSID.orEmpty(),
                    "${network.frequency} MHz; ${network.capabilities}; ${if (fresh) "fresh result" else "CACHED result"}${if (!validTime) "; observation time unavailable, shown time is retrieval" else ""}", seenAt, network.level)
            }
            status.text = if (results.isEmpty()) "No Wi-Fi results. Enable Location and Wi-Fi; Android may throttle scans."
                else if (cached || !updated) "Wi-Fi results saved. Cached readings are marked with their original observation time when available. Android may throttle scans."
                else "Fresh Wi-Fi results saved. These are access points, not a list of connected client devices."
            render()
        } catch (_: SecurityException) { status.text = "Wi-Fi permission was removed or Location is disabled. Check Settings." }
    }
    private fun readableDevice(row: Detection): String {
        if (row.kind != "BLE") return row.name.ifBlank { "Wi-Fi network" }
        val ids = Regex("0x[0-9A-Fa-f]{4}").findAll(row.details.substringBefore("; services:")).map { it.value.uppercase() }.toSet()
        val brand = when {
            "0X004C" in ids -> "Apple"
            "0X0075" in ids -> "Samsung"
            "0X0006" in ids -> "Microsoft"
            "0X00E0" in ids -> "Google"
            "0X000F" in ids -> "Broadcom"
            else -> "Unknown brand"
        }
        val services = row.details.substringAfter("; services:", "")
        val type = when {
            services.contains("00001812", true) -> "Keyboard / mouse (possible)"
            services.contains("0000180F", true) -> "Battery-powered accessory"
            services.contains("0000180D", true) -> "Heart-rate device"
            services.contains("00001809", true) -> "Thermometer"
            else -> "Unknown device"
        }
        val name = row.name.takeUnless { it.isBlank() || it.equals("Name unavailable", true) }
        return if (name != null) "$brand — $name" else "$brand — $type"
    }

    private fun date(time: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(time))
    private fun deviceIdentifier(row: Detection): String = when {
        row.identifier.startsWith("Identifier unavailable:") -> "Device ID unavailable (Wi-Fi BSSID not reported)"
        row.kind == "BLE" -> "Device ID (Bluetooth address): ${row.identifier}"
        row.kind == "Wi-Fi" -> "Device ID (Wi-Fi BSSID): ${row.identifier}"
        else -> "Device ID: ${row.identifier}"
    }
    private fun duplicateSummary(row: Detection): String {
        val repeated = (row.samples - 1).coerceAtLeast(0)
        return if (repeated == 0) "Readings: 1 • no repeat readings for this ID"
            else "Readings: ${row.samples} • ${repeated} repeat(s) with the same ID grouped into this result"
    }
    private fun render() {
        lastRender = SystemClock.elapsedRealtime()
        list.removeAllViews()
        val rows = if (showingHistory) history.rows(limit = historyLimit) else visibleSession?.let { history.rows(it, 10000) }.orEmpty()
        list.addView(TextView(this).apply {
            text = if (showingHistory) "Saved history • newest scans first • local time (${java.util.TimeZone.getDefault().id})\nTap a saved BLE entry to search again."
                else "Scan ${visibleSession ?: ""} • ${rows.size} distinct reported IDs/results • ${rows.firstOrNull()?.first?.let { date(it) } ?: "waiting for devices"}\nTap a Bluetooth device to find its signal."
        })
        list.addView(TextView(this).apply {
            text = "Matching IDs within one scan are grouped. Bluetooth addresses can change or be randomized; IDs are not permanent serial numbers and cannot prove two results are different physical devices."
            textSize = 12f
        })
        if (rows.isEmpty()) list.addView(TextView(this).apply { text = "No detections yet. Start a scan, or open History for earlier scans." })
        rows.forEach { row ->
            val text = "#${row.number} • ${readableDevice(row)}\n" +
                "Scan ID: ${row.session} • ${row.kind}\n" +
                "${deviceIdentifier(row)}\n" +
                "${Signal.label(row.rssi)} • ${row.rssi} dBm\n" +
                "First detected: ${date(row.first)}\n" +
                "Last detected: ${date(row.last)}\n" +
                duplicateSummary(row)
            val view: TextView = if (row.kind == "BLE") Button(this).apply { setOnClickListener { select(row) }; isAllCaps = false } else TextView(this)
            view.text = text; view.textSize = 14f; view.setPadding(8, 12, 8, 12)
            list.addView(view)
        }
        if (showingHistory && rows.size == historyLimit) list.addView(Button(this).apply {
            text = "Load older detections"; setOnClickListener { historyLimit += 100; render() }
        })
    }
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(wifiReceiver, filter)
        receiverRegistered = true
        handler.post(tick)
    }
    override fun onStop() {
        stopBluetooth()
        handler.removeCallbacks(tick); handler.removeCallbacks(wifiTimeout); handler.removeCallbacks(refresh)
        wifiSession = null
        if (receiverRegistered) { unregisterReceiver(wifiReceiver); receiverRegistered = false }
        tone?.release(); tone = null
        status.text = "Scanning paused while away from this screen. Saved history is available."
        super.onStop()
    }
    override fun onDestroy() { history.close(); super.onDestroy() }
}
