# AGENT/domain.md — Domain Layer

The Domain layer is located under `com.androidspeedtest.widget.domain`. It represents the pure business logic and does not contain any Android platform imports (`android.*`).

## Models

### `SpeedTestResult.kt`
- Encapsulates test statistics.
- `downloadSpeedMaxBps`: Long (peak download rate in bits per second).
- `uploadSpeedMaxBps`: Long (peak upload rate in bits per second).
- `latencyMs`: Double (average round-trip time in milliseconds).
- `timestamp`: Long (epoch milliseconds of the test run).
- `state`: SpeedTestState (active phase of the test).
- `errorMessage`: String? (populated when state is `ERROR`).

### `SpeedTestState.kt`
- Enum of states: `IDLE`, `CONNECTING` (warmup/resolving), `PINGING`, `DOWNLOADING`, `UPLOADING`, `COMPLETED`, `ERROR`.

## Ports (Interfaces)

### `SpeedTestRepository.kt`
Defines data persistence behaviors:
- `readLatest()`: Fetches last saved SpeedTestResult.
- `writeLatest(result)`: Saves the current result (and appends to history list if state is `COMPLETED`).
- `readHistory()`: Returns the historical list of completed tests.
- `saveUrls(...)` & `readUrls()`: Stores/retrieves custom test endpoints.

### `SpeedTestRunner.kt`
Defines the speed tester engine. It accepts a `SpeedTestListener` to stream live updates (such as current speeds, peak speeds, or latencies) to the caller during the test.

### `Clock.kt`
Decouples time fetching, facilitating deterministic unit testing.

## Use Cases

### `RunSpeedTestUseCase.kt`
Orchestrates test sequence:
1. Fetches test endpoint URLs from `SpeedTestRepository`.
2. Triggers `SpeedTestRunner.runTest(...)` with the URLs.
3. Observes the intermediate listener callbacks and updates the repository state.
4. Returns the final complete or error record.

### `FormatSpeedTestUseCase.kt`
Handles unit-formatting of speed metrics (e.g. `124.5 Mbps`, `512 Kbps`, `0 bps`) and latency (`35 ms`).
