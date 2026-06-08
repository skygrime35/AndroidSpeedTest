# AGENT/build.md — Build Pipeline

This project bypasses the standard Gradle build system to run compilation entirely on-device inside Termux.

## Prerequisites

The following packages are required (installable via Termux `pkg`):
```bash
pkg install aapt aapt2 apksigner d8 kotlin openjdk-21 zipalign
```

Run `./setup_sdk.sh` to download the standard platform class library jar:
```bash
./setup_sdk.sh
```
This fetches `android.jar` from Google's Android 34 repository into `~/.cache/android-sdk/android-34/`.

## Compilation Pipeline (`build.sh`)

The `./build.sh` script executes the following steps sequentially:

1. **Clean**: Deletes the `build/` output directory and rebuilds directory structures (`gen/`, `classes/`, `dex/`).
2. **Resources Compilation (`aapt2 compile`)**: Compiles XML resources in `app/res/` into a binary archive `build/res.zip`.
3. **Resource Linking (`aapt2 link`)**: Links the compiled resources with the `android.jar` platform dependency and `AndroidManifest.xml`. This creates the unsigned base APK `app-unsigned.apk` and generates the java file `R.java` inside `build/gen`.
4. **Kotlin Compilation (`kotlinc`)**: Compiles all Kotlin source files in `app/src/` alongside the generated resource references (`R.java`) into JVM class files in `build/classes/`.
5. **Dexing (`d8`)**: Takes all compiled JVM class files in `build/classes/` and packages them alongside Termux's local `kotlin-stdlib.jar` (API 26 min limit) into `classes.dex` inside `build/dex/`.
6. **Dex Injection (`zip`)**: Injects the compiled `classes.dex` file into the unsigned base APK.
7. **Alignment (`zipalign`)**: Aligns the zip entries on 4-byte boundaries, improving runtime memory mapping efficiency.
8. **Keystore Check**: Generates a self-signed `debug.keystore` file if one is not already present at the project root.
9. **Signing (`apksigner`)**: Signs the aligned APK using the debug keystore.
10. **Export**: Copies the final signed package `AndroidSpeedTest-1.0.apk` to public storage `/sdcard/Download/` (or fallback `$HOME/storage/downloads`).
