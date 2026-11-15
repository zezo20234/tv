# Build Instructions for REALM TV OS v21

## Prerequisites

Before building, ensure you have the following installed:

1. **Java Development Kit (JDK) 17** or later
   - Download from: https://adoptium.net/ or https://www.oracle.com/java/technologies/downloads/
   - Set JAVA_HOME environment variable

2. **Android SDK**
   - Install Android Studio or Android SDK Command-line Tools
   - Set ANDROID_HOME environment variable
   - Accept Android SDK licenses: `sdkmanager --licenses`

3. **Gradle 8.5** (or use the Gradle Wrapper)
   - Download from: https://gradle.org/releases/
   - Or let the project download it automatically

## Setting Up the Gradle Wrapper

The project includes Gradle wrapper configuration. To initialize it:

### Option 1: Using installed Gradle
```bash
cd REALM_TV_OS_v21
gradle wrapper --gradle-version 8.5
```

### Option 2: Download gradle-wrapper.jar manually
```bash
# Download the gradle-wrapper.jar
curl -L https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar
```

Or on Windows PowerShell:
```powershell
Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.5-bin.zip" -OutFile "gradle-8.5-bin.zip"
# Extract and use local gradle
```

## Building the APK

### Using Gradle Wrapper (Recommended)

**Linux/Mac:**
```bash
cd REALM_TV_OS_v21
chmod +x gradlew
./gradlew assembleDebug
```

**Windows:**
```cmd
cd REALM_TV_OS_v21
gradlew.bat assembleDebug
```

### Using Installed Gradle

```bash
cd REALM_TV_OS_v21
gradle assembleDebug
```

## Output Location

After successful build, the APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Installing on Android TV

### Method 1: ADB (Developer Mode)
1. Enable USB Debugging on your Android TV
2. Connect TV to your computer via ADB
3. Install:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
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
2. Find "REALM TV OS"
3. Set as default launcher
4. Or select "Always" when prompted to choose between launchers

## Troubleshooting

### Gradle sync issues
- Clear Gradle cache: `./gradlew clean`
- Delete `.gradle` folder in project directory

### SDK not found
- Check ANDROID_HOME environment variable
- Ensure SDK platform API 34 is installed

### Build errors
- Ensure JDK 17 is being used
- Check that all required SDK components are installed
- Run with `--stacktrace` for detailed error information

## Release Build

For a signed release APK:
```bash
./gradlew assembleRelease
```

The keystore is already configured in `app/build.gradle.kts` with:
- Keystore: `app/realm_tvos.jks`
- Password: `realm123`
- Key alias: `realm`
- Key password: `realm123`

Release APK location:
```
app/build/outputs/apk/release/app-release.apk
```

## Performance Notes

REALM TV OS v21 is optimized for low-end Android TV hardware like the X96Q. The implementation includes:
- Lightweight custom views (no heavy dependencies)
- Optimized bitmap operations
- Efficient animation system
- Minimal memory footprint
