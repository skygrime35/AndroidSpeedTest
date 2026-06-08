# AGENT.md — AndroidSpeedTest

> Single source of truth for developer agents (Claude Code, Cursor, Gemini, etc.) working on this project. `CLAUDE.md` and `GEMINI.md` redirect here.

---

## How to code here (meta)

Adapted from [multica-ai/andrej-karpathy-skills/CLAUDE.md](https://github.com/multica-ai/andrej-karpathy-skills/blob/main/CLAUDE.md). These rules bias toward caution over speed. Use judgment for trivial work.

### 1. Think before coding
**Don't assume. Don't hide confusion. Surface tradeoffs.**
Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity first
**Minimum code that solves the problem. Nothing speculative.**
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.
- Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical changes
**Touch only what you must. Clean up only your own mess.**
When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.
When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

### 4. Goal-driven execution
**Define success criteria. Loop until verified.**
Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"
For multi-step tasks, state a brief plan:
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]

---

## What this is
**Android Speed Test**: A native Android widget and companion application that measures and displays network speed metrics:
- **Ping / Latency**: Average of successful connection round-trip times (RTT) to the test server.
- **Max Download Speed**: Peak sustained download rate (bps) when fetching remote streams.
- **Max Upload Speed**: Peak sustained upload rate (bps) when uploading mock payloads.

All speed test tasks run on a background thread inside a **Foreground Service** to prevent system kills and ANR (Application Not Responding) conditions.

---

## Architecture (Hexagonal / Ports & Adapters)

The codebase is structured under `com.androidspeedtest.widget` using strict Hexagonal boundaries:

```
com.androidspeedtest.widget/
├── domain/                    # Pure Kotlin. ZERO android.* dependencies.
│   ├── model/                 # SpeedTestResult, SpeedTestState
│   ├── port/                  # SpeedTestRepository, SpeedTestRunner, Clock
│   └── usecase/               # RunSpeedTestUseCase, FormatSpeedTestUseCase
├── adapter/                   # Infrastructure implementations (driven adapters)
│   ├── repository/            # JsonSpeedTestRepository (SharedPreferences + JSON file)
│   ├── runner/                # HttpSpeedTestRunner (Raw Java URL connections)
│   └── clock/                 # RealClock (System.currentTimeMillis)
├── ui/                        # Android entry points (driving adapters)
│   ├── SpeedTestWidget        # AppWidgetProvider (Widget updates and clicks)
│   ├── WidgetRenderer         # Model state -> RemoteViews mapper
│   ├── MainActivity           # Settings, History list, and Manual run interface
│   └── SpeedTestService       # Foreground Service executing UseCase in a thread
└── di/
    └── ServiceLocator         # Manual dependency wiring registry
```

---

## Repo Layout

```
AndroidSpeedTest/
├── CLAUDE.md, GEMINI.md            # AI redirect shortcuts
├── AGENT.md                        # ← You are here
├── AGENT/                          # Specialized agent documentation
│   ├── domain.md                   # Specifications for Domain entities
│   ├── adapters.md                 # Specifications for Ports implementations
│   ├── ui.md                       # Specifications for Widgets, Services, Activities
│   └── build.md                    # Specifications for Termux build tools
├── app/
│   ├── AndroidManifest.xml         # Android manifest declaring entries and permissions
│   ├── src/                        # Kotlin source directories (Hexagonal layout)
│   └── res/
│       ├── layout/                 # widget_speedtest.xml, activity_main.xml
│       ├── xml/widget_info.xml     # AppWidgetProviderInfo configuration
│       ├── drawable/               # widget_bg, progress_*, ic_launcher_*
│       └── values/                 # strings.xml, colors.xml, styles.xml
├── setup_sdk.sh                    # Downloads platform android.jar from Google's CDN
├── build.sh                        # Custom no-Gradle Termux packaging script
└── debug.keystore                  # Generated debug key for signing the APK
```

---

## JSON Log Contract

The repository outputs the latest test results to public storage at `/sdcard/Download/speedtest_results.json` so Termux scripts and CLI tools can read them. Format:

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

---

## Specialized Module Documentation

The `AGENT/` directory contains specific manuals for each codebase slice. You **MUST** keep these documents up-to-date. If you alter any logic that invalidates information written in these manuals, update the corresponding document in the same commit.

| File | Covers |
|---|---|
| [`AGENT/domain.md`](file:///data/data/com.termux/files/home/Projects/AndroidSpeedTest/AGENT/domain.md) | Specs for models, ports, and use cases. |
| [`AGENT/adapters.md`](file:///data/data/com.termux/files/home/Projects/AndroidSpeedTest/AGENT/adapters.md) | Specs for repositories, HTTP sockets runner, and clock. |
| [`AGENT/ui.md`](file:///data/data/com.termux/files/home/Projects/AndroidSpeedTest/AGENT/ui.md) | Specs for Activities, Widget receivers, and Foreground Service. |
| [`AGENT/build.md`](file:///data/data/com.termux/files/home/Projects/AndroidSpeedTest/AGENT/build.md) | Specs for no-Gradle Termux CLI packaging compilation. |

---

## Conventions

- **Pure Hexagonal Boundary**: Classes inside `domain/` must never import `android.*` classes.
- **Zero External Dependencies**: Use only the platform `android.jar` and `kotlin-stdlib.jar`. Do not include androidx libraries or third-party HTTP libraries.
- **JSON Serialization**: Use the standard, built-in `org.json` package.
- **Agentic Maintenance**: This project is developed exclusively by agents. Code comments should be clean, docs must align with code changes, and dead code must be removed.

---

## Build Procedure (Termux CLI, On-Device)

Before compiling, fetch the platform dependencies:
```bash
./setup_sdk.sh
```

To build, zipalign, sign, and export the APK to public storage:
```bash
./build.sh
```
This produces `AndroidSpeedTest-1.0.apk` inside `/sdcard/Download/`.
