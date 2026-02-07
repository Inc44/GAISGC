# GAISGC

![Stars](https://img.shields.io/github/stars/Inc44/GAISGC?style=social)
![Forks](https://img.shields.io/github/forks/Inc44/GAISGC?style=social)
![Watchers](https://img.shields.io/github/watchers/Inc44/GAISGC?style=social)
![Repo Size](https://img.shields.io/github/repo-size/Inc44/GAISGC)
![Language Count](https://img.shields.io/github/languages/count/Inc44/GAISGC)
![Top Language](https://img.shields.io/github/languages/top/Inc44/GAISGC)
[![Issues](https://img.shields.io/github/issues/Inc44/GAISGC)](https://github.com/Inc44/GAISGC/issues?q=is%3Aopen+is%3Aissue)
![Last Commit](https://img.shields.io/github/last-commit/Inc44/GAISGC?color=red)
[![Release](https://img.shields.io/github/release/Inc44/GAISGC.svg)](https://github.com/Inc44/GAISGC/releases)
[![Sponsor](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86)](https://github.com/sponsors/Inc44)

Google AI Studio Garbage Collector

## 🚀 Installation

### From Source

```bash
git clone https://github.com/Inc44/GAISGC.git
```

#### Terminal

Ensure these binaries are in your system's PATH:

- Gradle - Version 9.3.0 tested
- JDK 21 - Version 21.0.9 tested

On Arch Linux:

```bash
sudo pacman -S gradle jdk21-openjdk
sudo archlinux-java set java-21-openjdk
```

On Windows:

- Download binary archive and extract from [Gradle](https://gradle.org/releases)
- Download and install from [JDK 21](https://www.oracle.com/java/technologies/downloads/#jdk21-windows)

```cmd
setx /M PATH "%PATH%;path\to\gradle\bin"
```

```bash
gradle -v
java --version
```

```bash
gradle wrapper
gradlew run
```

Or

```bash
gradle run
```

##### Important Links

- [FFmpeg](https://www.gyan.dev/ffmpeg/builds) - Media
- [js-beautify](https://beautifier.io) - Formatting

##### System Requirements

Ensure these binaries/scripts are in your system's PATH:

- `ffmpeg.exe` - Version 8.0.1 tested
- `js-beautify` - Version 1.15.4 tested

##### Adding Binaries to System Path

1. Download the necessary binaries.
2. Include them in your system's PATH, e.g., `C:\Windows\`.

Check their presence:

```bash
ffmpeg -version
js-beautify -v
```

## 🧾 Configuration

- Create `GAISGC` project in [Google Cloud console](https://console.cloud.google.com)
- Enable [Google Drive API](https://console.cloud.google.com/marketplace/product/google/drive.googleapis.com)
- Create [OAuth 2.0 Client ID](https://console.cloud.google.com/auth/clients) (Application type* `Desktop App`)
- Download `credentials.json`
- Add [Test user](https://console.cloud.google.com/auth/audience)

Move `credentials.json` to `path/to/GAISGC` or set environment variable:

```powershell
setx /M CREDENTIALS_JSON_PATH your_credentials_json_path
```

For Linux/macOS:

```bash
echo 'export CREDENTIALS_JSON_PATH="your_credentials_json_path"' >> ~/.bashrc # or ~/.zshrc
```

Or create a `.env` file or modify /etc/environment:

```
CREDENTIALS_JSON_PATH=your_credentials_json_path
```

Check by restarting the terminal and using:

```cmd
echo %CREDENTIALS_JSON_PATH%
```

For Linux/macOS:

```bash
echo $CREDENTIALS_JSON_PATH
```

## 🐛 Bugs

Not yet found.

## ⛔ Known Limitations

- May be slow for duplicate files due to quadratic time complexity.
- May consume too much memory due to not using streaming.
- May exceed Google Drive API quotas.
- May be insecure due to lack of encryption for credentials, cache, and temp files.
- "Direct" and "Regex" relink methods are destructive.
- "Pretty" relink method doesn't retain original formatting.
- "js-beautify" relink method is an external dependency.
- Requires Gradle, whose daemon may take too much time to start.

## 🚧 TODO

- [ ] Android version
	- [ ] Bottom navigation instead of a sidebar
	- [ ] Slide-up preview from the bottom
	- [ ] Video player

## 🙏 Thanks

Creators of:

- [FFmpeg](https://www.gyan.dev/ffmpeg/builds/) - Media processor

## 🤝 Contribution

Contributions, suggestions, and new ideas are heartily welcomed. If you're considering significant modifications, please initiate an issue for discussion before submitting a pull request.

## 📜 License

[![MIT](https://img.shields.io/badge/License-MIT-lightgrey.svg)](https://opensource.org/licenses/MIT)

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## 💖 Support

[![BuyMeACoffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/xamituchido)
[![Ko-Fi](https://img.shields.io/badge/Ko--fi-F16061?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/inc44)
[![Patreon](https://img.shields.io/badge/Patreon-F96854?style=for-the-badge&logo=patreon&logoColor=white)](https://www.patreon.com/Inc44)