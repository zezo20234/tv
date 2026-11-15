# REALM TV OS v22

The Ultimate Smart TV Operating System

## Features

- **Premium Visual Design**: Beautiful gradients, glassmorphism, and depth effects inspired by Apple tvOS, Samsung Tizen, LG webOS, Google TV, and PlayStation 5
- **Cinematic Home Screen**: Large hero section with dynamic content and beautiful artwork
- **Smart Organization**: Favorites, Recent, Entertainment, Gaming, and All Apps sections
- **HDMI Input Management**: Customizable HDMI inputs with presets for PS5, Xbox, PC, and more
- **Theme System**: 6 premium color themes (Realm Blue, Cosmic Purple, Deep Ocean, Sunset Gold, Neon Cyan, Midnight)
- **Weather Integration**: Real-time weather display for Riyadh
- **Auto-Updates**: Built-in GitHub-based update system
- **Physical Remote Support**: Full D-pad navigation optimized for TV remotes
- **Performance Optimized**: Smooth animations even on low-end Android TV hardware

## Build Instructions

### Prerequisites
- Android SDK with API 34
- JDK 17
- Gradle 8.5

### Build APK

1. Navigate to the project directory:
```bash
cd REALM_TV_OS_v22
```

2. Generate gradle wrapper (if needed):
```bash
gradle wrapper --gradle-version 8.5
```

3. Build the debug APK:
```bash
./gradlew assembleDebug
```

Or on Windows:
```bash
gradlew.bat assembleDebug
```

The APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`

### Install on Android TV

1. Enable "Install from unknown sources" in Android TV settings
2. Transfer the APK to your TV (via USB, network, or ADB)
3. Install the APK
4. Set REALM TV OS as the default launcher

## Target Device

Optimized for X96Q Android TV box and similar low-end Android TV devices.

## Design Philosophy

REALM TV OS combines the best ideas from the world's leading TV operating systems:

- **Apple tvOS**: Cinematic layouts and premium simplicity
- **Samsung Tizen/One UI**: Modern interface and smart layouts
- **LG webOS**: Intuitive navigation and attractive cards
- **Google TV**: Personalized recommendations and organization
- **PlayStation 5**: Futuristic dashboard and elegant transitions

The result is a completely original, cohesive, premium TV experience.

## Version

v22 - Initial release with complete feature set
