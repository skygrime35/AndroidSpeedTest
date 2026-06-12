# AGENT/adapters.md — Driven Adapters (Infrastructure)

Driven adapters implement the ports defined in the domain layer. They reside under `com.androidspeedtest.widget.adapter`.

## Clock Adapter

### `clock/RealClock.kt`
- Implements `Clock`.
- Returns `System.currentTimeMillis()`.

## Repository Adapter

### `repository/JsonSpeedTestRepository.kt`
- Implements `SpeedTestRepository`.
- Persists data to SharedPreferences named `"SpeedTestPrefs"`.
- Uses standard Android `org.json` to serialize speed test results and lists.
- Saves a history list capped at the last 50 completed records (with support for clearing history).
- Persists iteration counts (defaulting to 3) in SharedPreferences for configure-once reuse.
- Logs the latest test results to public storage `/sdcard/Download/speedtest_results.json` so termux scripts can read them. If storage permissions are not granted, it fails silently.

## Speed Test Runner Adapter

### `runner/HttpSpeedTestRunner.kt`
- Implements `SpeedTestRunner`.
- Performs HTTP operations using standard Java network classes (`java.net.URL`, `java.net.HttpURLConnection`).
- **Ping/Latency Test**: Performs 4 HTTP HEAD requests. Discards the first request (due to DNS/connection warmup skew), then averages the remaining 3.
- **Download Speed Test**: Performs an HTTP GET request. Reads blocks of 16KB from the connection stream in a loop. Calculates the transfer rate in 500ms sliding windows, tracking the peak rate. Capped at a maximum duration of 8 seconds to prevent excessive data consumption.
- **Upload Speed Test**: Performs an HTTP POST request. Uses chunked streaming mode (`setChunkedStreamingMode(16384)`) to upload mock bytes without caching them in RAM (which would cause OutOfMemoryErrors). Calculates rates in 500ms windows and tracks peak speeds. Capped at 8 seconds.
