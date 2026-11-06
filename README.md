# RF Control 1.1

Native Android 8+ foreground Bluetooth LE and Wi-Fi access-point scanner.

- Tap **Scan Bluetooth LE** or **Scan Wi-Fi**. Grant Nearby devices for BLE on Android 12+, and precise Location for both scanners (BLE signal proximity uses location-related scan information). Enable system Bluetooth, Wi-Fi and Location as needed.
- Each scan gets a durable ID; devices are numbered in first-observed order within that scan. Repeated BLE advertisements update the same row with first/last timestamps and sample count. SQLite history survives closing the app and restarting the phone. History loads older rows on demand. Settings → Clear saved history deletes it after confirmation. Uninstalling/clearing app data also removes it. Android cloud backup is disabled.
- Available names, BLE addresses, advertised manufacturer numeric IDs/service UUIDs, Wi-Fi SSIDs/BSSIDs, frequency, capabilities and RSSI are shown. Unavailable names remain unknown. Randomized addresses may change; names and IDs do not establish a device's owner, exact model, identity or connection status. Wi-Fi scanning lists access points, not their client devices.
- Tap a BLE row in live results or history to select its finder. It waits for a fresh advertisement, smooths RSSI, and beeps every 250–3000 ms (stronger = faster). The bar shows relative signal strength. Use media volume. Stop finder sound, stop scanning, leave the app or lock the screen to silence it. Sound pauses after 5 seconds without a valid signal. BLE scans end after 2 minutes; restart to continue. Only advertising Bluetooth LE devices are supported, not every Bluetooth device.
- RSSI **cannot establish exact distance or direction**. Walls, bodies, interference and transmitter power affect it. There are no distance estimates, directional maps, or claims that a device is on your body or connected to you.
- Wi-Fi scan requests can be throttled by Android. Results arrive asynchronously; cached entries are labeled and preserve Android's original observation timestamp when available (otherwise explicitly labeled retrieval time). Dates display in the phone's local timezone.
- History remains on the phone. The app has no Internet permission and sends no detections to a server.

## Installing this debug APK

Open `RFControl-1.1-debug.apk` on Android and allow installation from your download app if prompted. A previous debug build may use a different signing key; Android can reject an in-place update. In that case, uninstall the old build before installing this APK. Uninstalling removes that installation's saved data.

## Build and automated checks

Java 17, Gradle 8.9, Android SDK 35:

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions runs the same checks and uploads the debug APK and reports. Robolectric tests cover API 28/35 launch, denied permissions, saved-history UI, SQLite persistence, numbering/deduplication, timestamps, deletion, invalid/stale RSSI, smoothing and monotonic beep timing. Radio behavior and audible volume still need testing on physical Android devices.

## Physical-device verification

1. Install the APK; allow requested permissions. Scan a known advertising BLE beacon. Confirm one numbered row per address and increasing sample count.
2. Stop/restart scans; numbers restart per scan. Force close/reopen and restart the phone; saved scans should remain in History.
3. Select the beacon, wait for a fresh reading, then move closer/away. Verify relative bar changes and faster/slower beeps. Turn the beacon off: beeps must stop within 5 seconds.
4. Stop finder, switch apps or lock the screen: sound and BLE scanning must stop. Reopen and rescan to resume.
5. Scan Wi-Fi twice quickly; confirm fresh/cached labels, BSSID, original timestamps and throttle feedback. Test Location denied/disabled and Bluetooth disabled.
6. Clear history, confirm deletion, then reopen to verify it stays empty.
