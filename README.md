# Vexor - Calculator Vault Edition

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="Vexor Logo"/>
</p>

<p align="center">
  <strong>🔐 A fully functional Calculator that secretly hides your photos, videos, and files.</strong>
</p>

<p align="center">
  <a href="https://github.com/Tushal12e/Vexor/releases/tag/v4.1.0">
    <img src="https://img.shields.io/badge/Version-4.1.0-blue.svg" alt="Version"/>
  </a>
  <a href="https://github.com/Tushal12e/Vexor/releases">
    <img src="https://img.shields.io/github/downloads/Tushal12e/Vexor/total.svg" alt="Downloads"/>
  </a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-green.svg" alt="Android"/>
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License"/>
</p>

---

Disguised as a Calculator on your home screen. Type your secret PIN and press `=` to unlock the hidden vault.  
Part of the **Tushal12e App Series**.

---

## 📥 Download

**[⬇️ Download Vexor v4.1.0 APK](https://github.com/Tushal12e/Vexor/releases/download/v4.1.0/Vexor-v4.1.0.apk)**

| Info | Details |
|------|---------|
| **Version** | 4.1.0 |
| **Size** | ~18 MB |
| **Min Android** | 8.0 (API 26) |
| **Target SDK** | Android 14 (API 34) |

---

## 🚀 Key Features

### 🕵️ Calculator Disguise
- **Fully Functional Math**: Appears and works exactly like a standard calculator
- **Stealth Entry**: Enter PIN + `=` to access your hidden data
- **Stealth Icon**: App shows as "Calculator" on your phone

### 🎥 Secure Media & Files
- **Private Gallery**: Hide photos and videos with AES-256 Encryption
- **Video Support**: Smoothly encrypt and playback large HD videos
- **Document Locker**: Secure PDF, Word, and Excel files
- **Secure Camera**: Take photos directly into the vault

### 🚨 Intruder Detection
- **Intruder Selfie**: Automatically captures a photo of anyone trying to break in
- **Break-in Alerts**: Shows you the photo and time of the attempt upon successful login
- **Intruder Log**: View history of all break-in attempts

### 🛡️ Advanced Security
- **Multi-Vault System**:
  - **Main Vault**: Your real private data
  - **Fake Vault**: A decoy vault with a separate PIN to show snoops (stores dummy files)
- **Biometric Unlock**: Fingerprint support for quick access (optional)
- **AES-256 Encryption**: Military-grade encryption for all your files
- **No Cloud Upload**: All data stays on your device

### 📝 Additional Features
- **Secure Notes**: Encrypted note-taking within the vault
- **Share to Vault**: Receive files from other apps directly into the vault
- **Multiple Vaults**: Create and manage multiple separate vaults
- **Dark Theme**: Eye-friendly dark mode interface

---

## 🛠️ How to Use

1. **Launch "Calculator"**: It looks just like a normal calculator
2. **First Setup**: Create a secure PIN (e.g., `1234`)
3. **Daily Usage**:
   - **Calculate**: Type `2 + 2 =` → Shows `4`
   - **Unlock Vault**: Type `1234` then `=` → Opens Vault
4. **Fake Vault** (optional): Set up a different PIN that opens a decoy vault

---

## 🏗️ Project Structure

```
Vexor/
├── app/
│   ├── src/main/
│   │   ├── java/com/vexor/vault/
│   │   │   ├── data/           # Data layer (Repository, Models)
│   │   │   ├── security/       # Encryption & Security managers
│   │   │   └── ui/             # Activities & UI components
│   │   ├── res/                # Resources (layouts, drawables, etc.)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
└── build.gradle.kts
```

### Key Components

| Component | Description |
|-----------|-------------|
| `CalculatorActivity` | Main launcher - functional calculator with vault access |
| `MainActivity` | Vault file browser and management |
| `FileEncryptionManager` | AES-256 file encryption/decryption |
| `IntruderManager` | Captures intruder photos on failed attempts |
| `VaultRepository` | Data management for vault files |
| `CameraActivity` | Secure in-app camera |

---

## 🔧 Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Build Steps

```bash
# Clone the repository
git clone https://github.com/Tushal12e/Vexor.git
cd Vexor

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

The APK will be generated at:
- Debug: `app/build/outputs/apk/debug/`
- Release: `app/build/outputs/apk/release/`

---

## 📱 Screenshots

| Calculator | Vault | Settings |
|:----------:|:-----:|:--------:|
| Fully functional calculator disguise | Secure file browser | Customize security options |

---

## 🔒 Security & Privacy

- **Local Only**: No data is ever uploaded to any server
- **AES-256 Encryption**: Industry-standard encryption for all files
- **No Permissions Abuse**: Only requests necessary permissions
- **Open Source**: Full transparency - review the code yourself

---

## 📜 Complete App Series

Check out other apps in the Tushal12e series:

| App | Description |
|-----|-------------|
| **NetGuardPro** | Advanced Network Analysis & Security |
| **AppXray** | Deep App Analysis & Permission Manager |
| **Flownet** | Secure Offline Mesh Messenger |
| **RatnaLedger** | Professional Diamond Inventory Management |

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📞 Contact

**Developer**: Tushal12e

- GitHub: [@Tushal12e](https://github.com/Tushal12e)
- Repository: [Vexor](https://github.com/Tushal12e/Vexor)

---

<p align="center">
  <strong>Made with ❤️ by Tushal12e</strong>
</p>

<p align="center">
  ⭐ Star this repository if you find it useful! ⭐
</p>
