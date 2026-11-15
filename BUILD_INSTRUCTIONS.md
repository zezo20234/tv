# Build Instructions for REALM TV OS v22

## Prerequisites

Before building, ensure you have the following installed:

1. **Java Development Kit (JDK) 17** or later
   - Download from: https://adoptium.net/ or https://www.oracle.com/java/technologies/downloads/
   - Set JAVA_HOME environment variable

2. **Android SDK**
   - Install Android Studio or Android SDK Command-line Tools
   - Set ANDROID_HOME environment variable
   - Accept Android SDK licenses: `sdkmanager --licenses`
   - Install API 34 platform and build-tools

3. **Gradle 8.5.2** (or use the Gradle Wrapper)
   - Download from: https://gradle.org/releases/
   - Or let the project download it automatically

## Setting Up the Gradle Wrapper

The project includes Gradle wrapper configuration. To initialize it:

### Option 1: Using installed Gradle
```bash
cd REALM_TV_OS_v22
gradle wrapper --gradle-version 8.5.2
```

### Option 2: Download gradle-wrapper.jar manually
```bash
curl -L https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar
```

Or on Windows PowerShell:
```powershell
Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.5.2-bin.zip" -OutFile "gradle-8.5.2-bin.zip"
```

## Building the APKs

### Using Gradle Wrapper (Recommended)

**Linux/Mac:**
```bash
cd REALM_TV_OS_v22
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

**Windows:**
```cmd
cd REALM_TV_OS_v22

# Build debug APK
gradlew.bat assembleDebug

# Build release APK
gradlew.bat assembleRelease
```

### Using Installed Gradle

```bash
cd REALM_TV_OS_v22

# Build debug APK
gradle assembleDebug

# Build release APK
gradle assembleRelease
```

## Output Locations

After successful build, the APKs will be located at:

**Debug APK:**
```
app/build/outputs/apk/debug/app-debug.apk
```

**Release APK:**
```
app/build/outputs/apk/release/app-release.apk
```

## APK Details

### Debug APK
- **Purpose:** Development and testing
- **Signing:** Debug key
- **Optimization:** None
- **Use Case:** Testing new features, debugging

### Release APK
- **Purpose:** Production deployment
- **Signing:** Release key
- **Optimization:** ProGuard enabled
- **Use Case:** Final deployment to Android TV devices

## Installing on Android TV

### Method 1: ADB (Developer Mode)
1. Enable USB Debugging on your Android TV
2. Connect TV to your computer via ADB
3. Install debug APK:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
4. Install release APK:
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

### Method 2: USB Drive
1. Copy the APK to a USB drive
2. Plug USB drive into Android TV
3. Use a file manager app on the TV to install
4. Enable "Install from unknown sources" if prompted

### Method 3: Network Transfer
1. Host the APK on a local web server
2. Use the TV's browser to download and install
3. Enable "Install from unknown sources" if prompted

## Setting as Default Launcher

After installation:
1. Go to Android TV Settings → Apps → See all apps
2. Find "REALM TV OS v22"
3. Set as default launcher
4. Or select "Always" when prompted to choose between launchers

## Troubleshooting

### Gradle sync issues
- Clear Gradle cache: `./gradlew clean`
- Delete `.gradle` folder in project directory
- Rebuild: `./gradlew clean build`

### SDK not found
- Check ANDROID_HOME environment variable
- Ensure SDK platform API 34 is installed
- Run: `sdkmanager "platforms;android-34" "build-tools;34.0.0"`

### Build errors
- Ensure JDK 17 is being used: `java -version`
- Check that all required SDK components are installed
- Run with `--stacktrace` for detailed error information
- Run with `--info` for more verbose output

### Release build fails
- Ensure keystore file exists: `app/realm_tvos.jks`
- Verify keystore passwords in `app/build.gradle.kts`
- Check ProGuard rules in `app/proguard-rules.pro`

## Release Build Configuration

The release build is configured with:

**Keystore:**
- File: `app/realm_tvos.jks`
- Alias: `realm`
- Store password: `realm123`
- Key password: `realm123`

**ProGuard:**
- Enabled for code shrinking
- Custom rules in `app/proguard-rules.pro`
- Keeps TV-specific classes and custom views

**Build Types:**
- Debug: No optimization, debug signing
- Release: ProGuard optimization, release signing

## GitHub Actions

The project includes a GitHub Actions workflow that:

1. Builds both debug and release APKs
2. Publishes both APKs to GitHub releases
3. Names releases as "REALM TV OS v22 build [run_number]"
4. Includes both APKs in each release

Workflow file: `.github/workflows/build.yml`

## Performance Notes

REALM TV OS v22 is optimized for Toshiba Android TV and similar Android TV devices. The implementation includes:

- Lightweight custom views (no heavy dependencies)
- Optimized bitmap operations
- Efficient animation system
- Minimal memory footprint
- 12 theme options with instant switching
- Smart hero section with recent app integration
- ProGuard optimization for release builds

## Migration from v21

If upgrading from v21:

1. **Fresh Install Recommended**: New SharedPreferences key means settings start fresh
2. **Theme Selection**: All 12 themes available immediately
3. **Recent Apps**: Recent app tracking starts fresh
4. **HDMI Names**: HDMI custom names start fresh (presets still available)
5. **App Preferences**: Favorites and recent apps start fresh
6. **Hero Section**: Smart recent app integration works immediately

## Version Information

- **Version**: 22.0
- **Build Number**: Dynamic (based on GitHub run number or local build)
- **Package**: com.realm.tvos.app
- **Label**: REALM TV OS v22
- **SharedPreferences**: realm_tvos_v22
- **Target SDK**: API 34
- **Min SDK**: API 21
- **Language**: Kotlin
- **Build System**: Gradle 8.5.2
