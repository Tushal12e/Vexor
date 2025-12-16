# Vexor - Secure File Locker 🔐

A secure file vault Android app with AES-256 encryption to hide and protect your private files.

![Platform](https://img.shields.io/badge/Platform-Android-green)
![API](https://img.shields.io/badge/API-26%2B-brightgreen)
![Language](https://img.shields.io/badge/Language-Kotlin-purple)

## Features

### 🔒 Security
- **AES-256-GCM Encryption** - Military-grade encryption for all files
- **Android Keystore** - Secure key storage using hardware-backed keystore
- **4-Digit PIN Lock** - Quick and secure access
- **Fingerprint Unlock** - Biometric authentication support
- **Auto-Lock** - Locks after 5 failed attempts (30-second timeout)

### 🗂️ Vault Features
- **Photo & Video Storage** - View encrypted media in-app
- **Document Storage** - Store PDFs, Word docs, and more
- **File Categories** - Organize by type (Photos, Videos, Documents, Audio)
- **Encrypted Thumbnails** - Even thumbnails are encrypted
- **Secure Delete** - Permanently delete files

### 🕵️ Privacy Features
- **Fake Vault (Decoy)** - Enter a different PIN to show a fake vault
- **Intruder Detection** - Captures selfie on wrong PIN attempts
- **Break-in Logs** - View all failed unlock attempts with timestamps
- **File Hiding** - Files removed from gallery after import

## Screenshots

| Auth Screen | Main Vault | Settings |
|-------------|------------|----------|
| PIN pad with fingerprint | File grid with tabs | Security options |

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM
- **Encryption**: javax.crypto (AES-256-GCM)
- **Biometric**: AndroidX Biometric
- **Camera**: CameraX (for intruder detection)
- **Media**: ExoPlayer (for video playback)

## Installation

### From Release
1. Download the latest APK from [Releases](../../releases)
2. Install on your Android device
3. Grant required permissions

### Build from Source
```bash
# Clone the repository
git clone https://github.com/yourusername/Vexor.git
cd Vexor

# Build debug APK
./gradlew assembleDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

| Permission | Usage |
|------------|-------|
| `READ_MEDIA_IMAGES` | Import photos |
| `READ_MEDIA_VIDEO` | Import videos |
| `CAMERA` | Intruder selfie capture |
| `USE_BIOMETRIC` | Fingerprint unlock |
| `VIBRATE` | Haptic feedback |

## How It Works

1. **First Launch**: Create a 4-digit PIN and optionally set up a fake vault PIN
2. **Import Files**: Tap + to import files from gallery or file manager
3. **Encryption**: Files are encrypted with AES-256 and stored securely
4. **Original Deleted**: Original files are removed from gallery
5. **Access**: Enter your PIN or use fingerprint to unlock
6. **Fake Vault**: Enter fake PIN to show decoy vault (if enabled)

## Security Architecture

```
┌─────────────────────────────────────────────────┐
│                  Android Keystore               │
│  ┌─────────────────────────────────────────┐   │
│  │     AES-256 Master Key (Hardware)       │   │
│  └─────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────┐
│              File Encryption Layer              │
│  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ IV (12 bytes)│─▶│ AES-GCM Encrypted Data │  │
│  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────┐
│              Encrypted Storage                  │
│  /data/data/com.vexor.vault/files/vault/       │
└─────────────────────────────────────────────────┘
```

## Project Structure

```
app/src/main/java/com/vexor/vault/
├── data/
│   ├── Models.kt           # Data models (VaultFile, IntruderLog)
│   └── VaultRepository.kt  # Encrypted database operations
├── security/
│   ├── CryptoManager.kt    # AES-256 encryption/decryption
│   ├── VaultPreferences.kt # Encrypted SharedPreferences
│   ├── BiometricHelper.kt  # Fingerprint authentication
│   └── FileEncryptionManager.kt # File encryption with thumbnails
└── ui/
    ├── AuthActivity.kt     # PIN/Biometric authentication
    ├── SetupActivity.kt    # First-time setup
    ├── MainActivity.kt     # Vault file browser
    ├── PhotoViewerActivity.kt # Encrypted photo viewer
    ├── VideoPlayerActivity.kt # Encrypted video player
    ├── SettingsActivity.kt # App settings
    └── IntruderLogActivity.kt # Break-in attempts log
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Author

**Your Name**

---

⭐ Star this repo if you find it useful!
