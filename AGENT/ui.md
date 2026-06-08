# AGENT/ui.md — User Interface & Entry Points

The UI and entry point layers reside under `com.androidspeedtest.widget.ui` and represent the driving adapters of our Hexagonal Architecture.

## Widgets

### `SpeedTestWidget.kt`
- Extends `AppWidgetProvider`.
- Responds to widget lifecycle updates (`onUpdate`) and click broadcasts (`ACTION_TRIGGER_TEST`).
- When clicked:
  1. Starts the `SpeedTestService` in the background (using `startForegroundService` on Android 8.0+).
  2. Instantly updates the widget UI to a "Connecting..." state to give immediate visual feedback.
- Listens to `ACTION_UPDATE_WIDGET` broadcasts from the Service. When received, it reads the latest result from the repository and re-renders the widget.

### `WidgetRenderer.kt`
- Maps the latest domain `SpeedTestResult` to `RemoteViews`.
- Calculates percentage scales for progress bars (0 to 150 Mbps scale) and updates `ProgressBar` views.
- Sets text fields (latency, download speed, upload speed) and statuses.
- Binds a click handler targeting the entire widget card to trigger the `ACTION_TRIGGER_TEST` broadcast.

## Companion App

### `MainActivity.kt`
- The launcher activity. Displays the test statistics, configuration URLs, and run history.
- Requests runtime permissions: `POST_NOTIFICATIONS` (on Android 13+) and storage permissions (`WRITE_EXTERNAL_STORAGE`, `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` for Android 11+).
- Starts `SpeedTestService` on manual run clicks.
- Registers a `BroadcastReceiver` listening to `ACTION_APP_UPDATE` broadcasts sent by the service, updating the UI metrics in real-time.
- Programmatically constructs and appends stylized horizontal cards to display the list of historical results.

## Background Services

### `SpeedTestService.kt`
- Extends `Service`. Hosts the test worker thread.
- Runs as a **Foreground Service** to prevent system termination during background speed testing.
- Uses `Notification.Builder` to create progress notifications, updating them on every phase transition or speed calculation.
- Sends broadcast notifications (`ACTION_UPDATE_WIDGET` and `ACTION_APP_UPDATE`) to let the widget and activities refresh their displays.
- Shuts down using `stopSelf()` when the test completes or fails.
