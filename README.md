# Kay's RF Control 1.3

## Download and open on Android

**[Tap here to download RF Control APK](https://github.com/jettblue1488/RFControl-Android-/releases/download/android-apk/RFControl-Android.apk)**

If the GitHub release link opens an error on `release-assets.githubusercontent.com`, use this **[alternate direct APK download](https://raw.githubusercontent.com/jettblue1488/RFControl-Android-/apk-download/RFControl-Android.apk)** instead. This alternative is hosted in the repository branch and does not use expiring release asset redirects. Both URLs lead to the APK that GitHub Actions publishes after a successful build on `main`. If Android blocks the installation, open the APK from Downloads and approve the normal browser/file-manager install prompt.\n\nThis permanent link is updated by the GitHub Actions workflow whenever checks and the APK build succeed on `main`. If the direct link is not yet available, [check the build status](https://github.com/jettblue1488/RFControl-Android-/actions/workflows/build-apk.yml) or [open Releases](https://github.com/jettblue1488/RFControl-Android-/releases).

On your Android phone: tap the APK link, open the downloaded `RFControl-Android.apk` from Downloads, approve installing from your browser/files app if Android asks, then tap **Install** and **Open**. After that, tap **Kay's RF Control** in your app drawer whenever you want to run it. This is a native Android app, not a website; a GitHub page cannot directly run it before installation.

**Package conflict fix (v1.2+):** This APK now installs as **Kay's RF Control** using a new Android app ID (`com.kays.rfcontrol`), separate from the original `com.rfcontrol.app`. Your previous app and its history remain installed; the new app begins with its own empty history. No need to uninstall the old one. Future APK updates still require the same signing key; GitHub Actions debug builds may not share a signing key across builds. Avoid uninstalling a version with history you need.

Version 1.3 · Native Android 8+ foreground Bluetooth LE and Wi-Fi access-point scanner.

- Tap **Scan Bluetooth LE** or **Scan Wi-Fi**. Grant Nearby devices for BLE on Android 12+, and precise Location for both scanners (BLE signal proximity uses location-related scan information). Enable system Bluetooth, Wi-Fi and Location as needed.
- Each scan gets a durable ID; devices are numbered in first-observed order within that scan. Repeated BLE advertisements update the same row with first/last timestamps and sample count. SQLite history survives closing the app and restarting the phone. History loads older rows on demand. Settings → Clear saved history deletes it after confirmation. Uninstalling/clearing app data also removes it. Android cloud backup is disabled.
- Available names, BLE addresses, advertised manufacturer numeric IDs/service UUIDs, Wi-Fi SSIDs/BSSIDs, frequency, capabilities and RSSI are shown. Unavailable names remain unknown. Randomized addresses may change; names and IDs do not establish a device's owner, exact model, identity or connection status. Wi-Fi scanning lists access points, not their client devices.
- Tap a BLE row in live results or history to select its finder. It waits for a fresh advertisement, smooths RSSI, and beeps every 250–3000 ms (stronger = faster). The bar shows relative signal strength. Use media volume. Stop finder sound, stop scanning, leave the app or lock the screen to silence it. Sound pauses after 5 seconds without a valid signal. BLE scans end after 2 minutes; restart to continue. Only advertising Bluetooth LE devices are supported, not every Bluetooth device.
- RSSI **cannot establish exact distance or direction**. Walls, bodies, interference and transmitter power affect it. There are no distance estimates, directional maps, or claims that a device is on your body or connected to you.
- Wi-Fi scan requests can be throttled by Android. Results arrive asynchronously; cached entries are labeled and preserve Android's original observation timestamp when available (otherwise explicitly labeled retrieval time). Dates display in the phone's local timezone.
- History remains on the phone. The app has no Internet permission and sends no detections to a server.

## Privacy Shield — protective controls, not interference

Tap **Privacy Shield • Block tracker connections** for shortcuts to Android Bluetooth settings, Location settings, app permissions, and guidance for **Unknown tracker alerts > Scan now** in Safety & emergency. Turning off Bluetooth on **your phone** stops its Bluetooth connections; Android 13+ does not permit ordinary apps to silently switch Bluetooth off. In Bluetooth settings, you can forget previously paired devices. Android does not provide a public app API to force unrelated nearby devices to power off or unpair.

A Bluetooth tracker or dedicated GPS tracker may work without connecting directly to your phone. A Bluetooth tag may use a network of *other* phones and continue reporting when your Bluetooth is off. Turning your phone's Location setting off does not disable a physical tracker; it may also reduce the usefulness of automated unknown-tracker alerts. To check compatible unknown tags, keep alerts on and run Android's Scan now. An empty scan does **not** prove the absence of every tracker.

This app does not scramble radio signals, spoof tracker reports, take control of other people's devices, or prevent independently powered GPS trackers from operating. It only offers controls and detection for the user's own device.

## Build and automated checks

Java 17, Gradle 8.9, Android SDK 35:

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions runs these checks, uploads the debug APK and reports as workflow artifacts, and publishes `RFControl-Android.apk` to the `android-apk` GitHub Release on successful main-branch builds. Robolectric tests cover API 28/35 launch, denied permissions, saved-history UI, SQLite persistence, numbering/deduplication, timestamps, deletion, invalid/stale RSSI, smoothing and monotonic beep timing. Radio behavior and audible volume still need testing on physical Android devices.

## Physical-device verification

1. Install the APK; allow requested permissions. Scan a known advertising BLE beacon. Confirm one numbered row per address and increasing sample count.
2. Stop/restart scans; numbers restart per scan. Force close/reopen and restart the phone; saved scans should remain in History.
3. Select the beacon, wait for a fresh reading, then move closer/away. Verify relative bar changes and faster/slower beeps. Turn the beacon off: beeps must stop within 5 seconds.
4. Stop finder, switch apps or lock the screen: sound and BLE scanning must stop. Reopen and rescan to resume.
5. Scan Wi-Fi twice quickly; confirm fresh/cached labels, BSSID, original timestamps and throttle feedback. Test Location denied/disabled and Bluetooth disabled.
6. Clear history, confirm deletion, then reopen to verify it stays empty.
