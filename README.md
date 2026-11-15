# REALM TV OS v22

The Ultimate Smart TV Operating System - Enhanced Edition

## What's New in v22

- **12 Premium Themes**: Expanded from 6 to 12 beautiful color themes
- **Smart Hero Section**: Automatically displays your most recently used app
- **Release Build Support**: Both debug and release APKs with ProGuard optimization
- **Improved Theme Selector**: Better UI for selecting from 12 themes
- **Enhanced Performance**: Optimized theme rendering and memory management

## Features

- **Premium Visual Design**: Beautiful gradients, glassmorphism, and depth effects inspired by Apple tvOS, Samsung Tizen, LG webOS, Google TV, and PlayStation 5
- **Cinematic Home Screen**: Large hero section with dynamic content and beautiful artwork
- **Smart Organization**: Favorites, Recent, Entertainment, Gaming, and All Apps sections
- **HDMI Input Management**: Customizable HDMI inputs with presets for PS5, Xbox, PC, and more
- **12 Color Themes**: Realm Blue, Cosmic Purple, Deep Ocean, Sunset Gold, Neon Cyan, Midnight, Vibrant Coral, Electric Violet, Mystic Teal, Aurora Green, Royal Maroon, Ultra Violet
- **Smart Hero**: Recent app integration for personalized experience
- **Weather Integration**: Real-time weather display for Riyadh
- **Auto-Updates**: Built-in GitHub-based update system
- **Physical Remote Support**: Full D-pad navigation optimized for TV remotes
- **Performance Optimized**: Smooth animations even on low-end Android TV hardware
- **Release Builds**: Optimized APKs with ProGuard for production use

## Build Instructions

### Prerequisites
- Android SDK with API 34
- JDK 17
- Gradle 8.5.2

### Build APK

1. Navigate to the project directory:
```bash
cd REALM_TV_OS_v22
```

2. Build debug APK:
```bash
./gradlew assembleDebug
```

3. Build release APK:
```bash
./gradlew assembleRelease
```

Or on Windows:
```bash
gradlew.bat assembleDebug
gradlew.bat assembleRelease
```

### APK Output

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

### Install on Android TV

1. Enable "Install from unknown sources" in Android TV settings
2. Transfer the APK to your TV (via USB, network, or ADB)
3. Install the APK
4. Set REALM TV OS v22 as the default launcher

## Target Device

Optimized for Toshiba Android TV and similar Android TV devices.

## Design Philosophy

REALM TV OS v22 combines the best ideas from the world's leading TV operating systems:

- **Apple tvOS**: Cinematic layouts and premium simplicity
- **Samsung Tizen/One UI**: Modern interface and smart layouts
- **LG webOS**: Intuitive navigation and attractive cards
- **Google TV**: Personalized recommendations and organization
- **PlayStation 5**: Futuristic dashboard and elegant transitions

The result is a completely original, cohesive, premium TV experience.

## Theme Selection

REALM TV OS v22 offers 12 premium themes:

1. **Realm Blue** - Deep blue with purple and teal accents
2. **Cosmic Purple** - Purple and pink gradient
3. **Deep Ocean** - Blue and teal ocean colors
4. **Sunset Gold** - Orange and gold sunset colors
5. **Neon Cyan** - Bright cyan and teal
6. **Midnight** - Dark gray and charcoal
7. **Vibrant Coral** - Coral and pink
8. **Electric Violet** - Violet and pink
9. **Mystic Teal** - Teal and blue
10. **Aurora Green** - Green and teal
11. **Royal Maroon** - Dark red and maroon
12. **Ultra Violet** - Deep purple and violet

To change themes:
1. Navigate to the THEME card in the Inputs section
2. Press OK to open the theme selector
3. Choose from 12 themes in two rows
4. Theme changes apply instantly

## Smart Hero Section

The hero section now automatically displays your most recently used app when you launch the launcher:

- **Recent App Detection**: Finds your most recently used app
- **Delayed Display**: Shows after 200ms for smooth transition
- **Dynamic Updates**: Updates when you focus on different apps
- **Personalized**: Quick access to apps you use most

## Version

v22 - Enhanced edition with expanded themes and smart features
