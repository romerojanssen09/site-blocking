# Native Libraries Directory

## What Goes Here

Place the `libtun2socks.so` files in the architecture-specific folders:

```
jniLibs/
├── arm64-v8a/
│   └── libtun2socks.so  ← Place here
├── armeabi-v7a/
│   └── libtun2socks.so  ← Place here
└── x86_64/
    └── libtun2socks.so  ← Place here
```

## How to Get the Files

### Quick Steps:

1. **Download Shadowsocks APK:**
   - Go to: https://github.com/shadowsocks/shadowsocks-android/releases/latest
   - Download any APK file (universal version recommended)

2. **Extract the APK:**
   - Rename `.apk` to `.zip`
   - Extract using Windows Explorer or 7-Zip

3. **Find the libraries:**
   - Look in `lib/arm64-v8a/libtun2socks.so`
   - Look in `lib/armeabi-v7a/libtun2socks.so`
   - Look in `lib/x86_64/libtun2socks.so`

4. **Copy to this project:**
   - Copy each `libtun2socks.so` to the matching folder here

## Verify

After copying, you should have:
- `app/src/main/jniLibs/arm64-v8a/libtun2socks.so`
- `app/src/main/jniLibs/armeabi-v7a/libtun2socks.so`
- `app/src/main/jniLibs/x86_64/libtun2socks.so`

## Test

Build and run the app. Check logs for:
```
✓ tun2socks native library loaded successfully
```

If you see that, it worked! 🎉

## Need Help?

See: MANUAL_DOWNLOAD_GUIDE.md in the project root
