# Android Speed Test Widget & Companion App

A lightweight, premium-styled Android Home Screen widget and companion application that measures network performance (latency, download speed, and upload speed) with a strict Hexagonal Architecture. 

Built entirely **on-device inside Termux** without Gradle.

---

## Features

- **Home Screen Widget**: A dark-themed 4x1 card showing your latest network stats, with smooth progress bars and a tap-to-test refresh trigger.
- **Speed Test Metrics**:
  - **Latency (Ping)**: Average round-trip time (ms) to the test server.
  - **Download Speed**: Peak sustained download rate (Mbps).
  - **Upload Speed**: Peak sustained upload rate (Mbps).
- **Foreground Execution**: Tests run in a background thread inside an Android Foreground Service, providing real-time notification updates and preventing ANRs or OS kills.
- **Customizable Endpoints**: Change test server URLs directly in the companion app settings.
- **Test History Log**: View a reverse-chronological list of past runs in the companion app.
- **External Integration**: Logs the latest speed test result in a public JSON file (`/sdcard/Download/speedtest_results.json`) for Termux terminal scripts or cron jobs to consume.

---

## On-Device Compilation (Termux)

This project compiles directly on your Android phone using command-line binaries. No Gradle or Android Studio required.

### 1. Install Prerequisites
Open Termux and install the necessary package tools:
```bash
pkg install aapt aapt2 apksigner d8 kotlin openjdk-21 zipalign curl unzip
```

### 2. Run Setup
Download the standard Android SDK platform classes:
```bash
./setup_sdk.sh
```
This fetches the required `android.jar` (API 34) dependency and stores it in your local cache (`~/.cache/android-sdk/`).

### 3. Build the APK
Compile, align, and sign the application package:
```bash
./build.sh
```
The script compiles the resources and Kotlin files, dexes them, zipaligns the binary, signs it using a generated `debug.keystore`, and exports the APK.

---

## Installation & Setup

1. Locate the compiled APK at `/sdcard/Download/AndroidSpeedTest-1.0.apk` using your device's file manager.
2. Tap the APK file and select **Install** (allow installation from unknown sources if prompted).
3. Open **Speed Test** from your launcher.
4. Grant the requested permissions:
   - **Notifications** (required on Android 13+ to show foreground progress notifications).
   - **All Files Access** (required to write logs to `/sdcard/Download/speedtest_results.json`).
5. Long-press your launcher's Home Screen → select **Widgets** → find **Speed Test Widget** → drag it to your home screen.
6. Tap the widget anywhere to initiate a network speed test!

---

## How It Works

- **Trigger**: Clicks on the widget broadcast a `com.androidspeedtest.widget.ACTION_TRIGGER_TEST` intent.
- **Service**: The broadcast starts `SpeedTestService`. The service creates a notification channel, runs as a Foreground Service, and executes the speed test in a background thread.
- **Engine**: 
  - **Latency**: Fires 4 HEAD requests to the ping URL, discards the first request, and averages the remaining 3.
  - **Download/Upload**: Connects via HTTP sockets. Reads/writes data in blocks. Measures rates in 500ms sliding windows to determine peak speed. Automatically caps tests at 8 seconds to save data.
- **Broadcasting & Rendering**: The service writes results to SharedPreferences/JSON log and sends update broadcasts. `SpeedTestWidget` and `MainActivity` intercept these to render the progress values and bars live.

---

## JSON Log Schema

The log written to `/sdcard/Download/speedtest_results.json` contains:

```json
{
  "download_speed_max_bps": 94520100,
  "upload_speed_max_bps": 24102900,
  "latency_ms": 32.5,
  "timestamp": 1780876200000,
  "state": "COMPLETED",
  "error_message": null
}
```

You can query this file from any Termux script or widget parser:
```bash
cat /sdcard/Download/speedtest_results.json | jq '.download_speed_max_bps'
```

---

## Developer Manuals

For internal developer guidelines and module details, see:
- [AGENT.md](file:///data/data/com.termux/files/home/Projects/AndroidSpeedTest/AGENT.md): Master manual.
- [AGENT/domain.md](file:///data/data/com.termux/files/home/Projects/AndroidSpeedTest/AGENT/domain.md): Hexagonal core details.
- [AGENT/adapters.md](file:///data/data/com.termux/files/home/Projects/AndroidSpeedTest/AGENT/adapters.md): Port implementations.
- [AGENT/ui.md](file:///data/data/com.termux/files/home/Projects/AndroidSpeedTest/AGENT/ui.md): Android entry points.
- [AGENT/build.md](file:///data/data/com.termux/files/home/Projects/AndroidSpeedTest/AGENT/build.md): Packaging details.
