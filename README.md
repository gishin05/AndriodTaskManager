<div align="center">

<img src="docs/icon.png" width="120" alt="Task Manager Icon"/>

# Android Task Manager
### A premium system performance monitor with native Dynamic Island integration

[![Android](https://img.shields.io/badge/Android-16%20(API%2036)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Latest-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

</div>

---

## ✨ Features

### 📊 Real-Time System Monitoring
- **CPU Usage** — Per-core and total load
- **RAM / Memory** — Live used/available split
- **FPS Counter** — Frame pacing via `Choreographer`
  
### 🏝️ Native Dynamic Island Integration
Works on **every modern Android phone** — not just Xiaomi:
- **Android 16+** — Promotes to a native Live Update chip in the status bar on all OEM devices
- **HyperOS / MIUI** — Native island integration via the HyperIsland Toolkit
- **Android 8–15** — Falls back to a standard ongoing notification with a live progress bar



---


## ⚙️ Requirements

| Item | Minimum | Recommended |
|------|---------|-------------|
| Android OS | 8.0 (API 26) | 16 (API 36) |
| Dynamic Island | Any Android 16 device | Xiaomi HyperOS 3.0+ |

---

## 🚀 Build & Install

### Build

```bash
git clone https://github.com/gishin05/AndriodTaskManager.git
cd AndriodTaskManager
./gradlew assembleDebug
```

### Install via ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Or download the pre-built APK from the [Releases](../../releases) page.

---

## 📄 License

```
MIT License — Copyright (c) 2026
Permission is granted to use, copy, modify, and distribute this software.
```

---

<div align="center">

**Built with ❤️ using Kotlin · Jetpack Compose · Android 16**

</div>
