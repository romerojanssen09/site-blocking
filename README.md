# DNS-Based VPN Site Blocker

A system-wide DNS-based site blocking application for Android built with **Kotlin** and **Jetpack Compose**. Block websites and domains across all apps on your device without routing all traffic through a VPN.

## 🌟 Features

- **DNS-Only Blocking** - Intercepts DNS queries without routing all traffic
- **System-Wide** - Works across all apps on the device
- **Real-Time Updates** - Automatically restarts VPN when rules change
- **Domain & IP Blocking** - Block by domain name or IP address
- **Live Statistics** - See blocked and allowed request counts
- **Activity Logs** - View detailed VPN activity logs
- **No Root Required** - Uses Android VPN API

## 📱 Screenshots

[Add screenshots here]

## 🏗️ Architecture

### How It Works

1. **VPN Setup**: Creates a VPN interface that only routes DNS servers (8.8.8.8, 8.8.4.4, etc.)
2. **DNS Interception**: Captures all DNS queries going through the VPN
3. **Domain Checking**: Checks if the queried domain is in the block list
4. **Blocking**: Returns 0.0.0.0 for blocked domains, causing connection failures
5. **Forwarding**: Forwards allowed DNS queries to real DNS servers
6. **Direct Routing**: TCP/UDP traffic goes directly to the internet (not through VPN)

### Why DNS-Only?

- **Efficient**: Only processes DNS packets, not all traffic
- **Fast**: No performance impact on regular internet usage
- **Battery Friendly**: Minimal battery consumption
- **Simple**: No need for complex TCP/UDP forwarding

## 🚀 Getting Started

### Prerequisites

- Android Studio Arctic Fox or later
- Android device or emulator (API 21+)
- JDK 11 or later
- For Android 14+ (API 35): Additional permissions required

### Installation

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and run it directly.

## 📖 Usage

1. **Start VPN**: Tap "Start VPN" button and grant VPN permission
2. **Add Rules**: Enter domain (e.g., `facebook.com`) or IP address
3. **Automatic Restart**: VPN automatically restarts when rules change
4. **View Logs**: Check "View Logs" to see DNS queries and blocking activity
5. **Stop VPN**: Tap "Stop VPN" to disable blocking

### Example Domains to Block

```
facebook.com
twitter.com
instagram.com
tiktok.com
youtube.com
```

## 🔧 Technical Details

### Permissions Required

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.BIND_VPN_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### VPN Configuration

- **Address**: 10.0.0.2/24
- **DNS Servers**: 8.8.8.8, 8.8.4.4
- **Routes**: Only DNS server IPs (not 0.0.0.0/0)
- **MTU**: 1500
- **Underlying Network**: Set to allow direct internet access

### Project Structure

```
.
└── app/
    └── src/main/java/com/example/blocking/
        ├── vpn/
        │   └── BlockingVpnService.kt    # VPN implementation
        ├── data/
        │   ├── BlockingRulesManager.kt  # Rule management
        │   └── LogManager.kt            # Logging
        └── ui/
            └── BlockingScreen.kt        # UI (Jetpack Compose)
```

## 🔍 How DNS Blocking Works

### Normal DNS Flow
```
App → DNS Query → DNS Server → IP Address → App connects to IP
```

### With VPN Blocking
```
App → DNS Query → VPN intercepts → Check block list
  ├─ Blocked: Return 0.0.0.0 → App fails to connect ✓
  └─ Allowed: Forward to real DNS → Return real IP → App connects ✓
```

### Why Apps Can't Connect to 0.0.0.0
- 0.0.0.0 is a non-routable address
- TCP/UDP connections to 0.0.0.0 immediately fail
- App shows "Can't connect" or "No internet" error

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Async**: Coroutines + Flow
- **Architecture**: MVVM pattern
- **Minimum SDK**: API 21 (Android 5.0)
- **Target SDK**: API 35 (Android 15)

## 🐛 Troubleshooting

### VPN Won't Start
- Check AndroidManifest.xml has all required permissions
- Ensure `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` permission is present (Android 14+)
- Grant VPN permission in Android settings

### No Internet After Starting VPN
- Check logs for "Using underlying network for routing"
- Verify `SetUnderlyingNetworks()` is called in VPN setup
- Ensure only DNS routes are added (not 0.0.0.0/0)

### Blocked Sites Still Accessible
- Check if domain is in block list
- View logs to see if DNS queries are being intercepted
- Close and reopen the app to clear DNS cache
- VPN auto-restarts when rules change

### DNS Cache Issues
- VPN automatically restarts when rules are added/removed
- This clears the DNS cache
- If issues persist, manually restart the VPN

## 📝 Development

### Building from Source

```bash
./gradlew clean assembleDebug
```

### Running Tests

```bash
./gradlew test
```

### Debug Build

```bash
./gradlew assembleDebug
```

### Release Build

```bash
./gradlew assembleRelease
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## ⚠️ Disclaimer

This application is for educational and personal use only. Use responsibly and in accordance with your local laws and regulations. The developers are not responsible for any misuse of this application.

## 🙏 Acknowledgments

- Android VPN API documentation
- Jetpack Compose community
- Kotlin Coroutines
- DNS protocol specifications

## 📧 Contact

For questions or support, please open an issue on GitHub.

## 🔗 Related Projects

- [Android VPN Service Documentation](https://developer.android.com/reference/android/net/VpnService)
- [DNS Protocol RFC 1035](https://www.ietf.org/rfc/rfc1035.txt)

---

**Note**: This is a DNS-based blocking solution. It does not provide encryption or anonymity like traditional VPN services. All traffic (except DNS) goes directly to the internet.
