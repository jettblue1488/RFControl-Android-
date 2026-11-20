package com.rfcontrol.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.text.DateFormat
import java.util.Date

/** Reports actual Android Bluetooth ACL link events, not nearby advertisements or attempted pairing. */
class BluetoothConnectionReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        val prefs = context.getSharedPreferences("connection_alerts", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", true)) return
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val device = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val name = try { device?.name?.takeIf { it.isNotBlank() } } catch (_: SecurityException) { null }
        val address = try { device?.address } catch (_: SecurityException) { null }
        val label = name ?: address ?: "Unknown Bluetooth device"
        val connected = action == BluetoothDevice.ACTION_ACL_CONNECTED
        val stamp = DateFormat.getDateTimeInstance().format(Date())
        val line = "$stamp • ${if (connected) "CONNECTED" else "DISCONNECTED"} • $label • ${address ?: "Address unavailable"}"
        val prior = prefs.getString("events", "").orEmpty()
        prefs.edit().putString("events", (line + "\n" + prior).take(20000)).apply()
        if (!connected) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "bluetooth_connection_alerts"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Bluetooth connection alerts",
                NotificationManager.IMPORTANCE_HIGH).apply { description = "Alerts for Bluetooth devices connected to this phone" })
        }
        val launch = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Bluetooth device connected")
            .setContentText(label)
            .setStyle(android.app.Notification.BigTextStyle().bigText("$label\n${address ?: "Address unavailable"}\n$stamp"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        }
        // Short double-beep; the notification remains the reliable alert when background audio is restricted.
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            Handler(Looper.getMainLooper()).postDelayed({
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 220)
            }, 230)
        } catch (_: RuntimeException) { }
    }
}
