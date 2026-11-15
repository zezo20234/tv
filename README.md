# TVHome v15

A premium Android TV launcher optimized for Toshiba TV.

## Features

- **Android Launcher Integration**: Full HOME/LEANBACK intent support
- **App Detection**: Automatic discovery of installed applications
- **App Launching**: Real app launching via explicit intents
- **D-pad Navigation**: Complete physical remote support
- **HDMI Inputs**: TV input management and display
- **Settings**: Customizable themes and device information
- **Auto-update**: GitHub releases integration

## Build

### Prerequisites
- Java 17
- Android SDK (API 34)
- Gradle 8.9+ (wrapper included)

### Building APK

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

## Installation

1. Enable "Install from unknown sources" on your TV
2. Transfer the APK to your TV (USB, ADB, or file manager)
3. Install the APK
4. Set TVHome as your default launcher

## Target Device

Toshiba Android TV

## Version

Version: 15
Build: 15.{GITHUB_RUN_NUMBER}

## Repository

https://github.com/zezo20234/tv
