# Kiosk Launcher

Kiosk Launcher is an Android app that allows you to turn any Android device into a kiosk mode device. It locks down the device to a single app or a set of apps and prevents users from accessing any other features or settings.

## Features

- Set a single app or multiple apps as kiosk mode apps
- Enable or disable the status bar, navigation bar, and notification access
- Set a password to exit kiosk mode or access device settings
- Schedule kiosk mode to start and stop at specific times
- Restart the device automatically at a specific time
- Local settings UI + remote policy sync (HTTP JSON)
- Support for portrait and landscape orientations
- Support for Android 10 and above (Device Owner required)

## Usage
To exit kiosk mode or open settings:

1. Long-press the hidden hotspot at the top-left corner.
2. Enter your PIN.
3. Use the Settings screen to stop kiosk mode or open system settings.

## Device Owner requirement (Android 10+)
This app expects Device Owner mode to fully lock the device and apply status bar restrictions.
You can set it via ADB on a fresh device:

```
adb shell dpm set-device-owner com.osamaalek.kiosklauncher/.MyDeviceAdminReceiver
```

## Remote policy JSON example

```json
{
  "kioskUrl": "https://your-kiosk-url",
  "allowedPackages": ["com.osamaalek.kiosklauncher", "com.example.app"],
  "disableStatusBar": true,
  "disableNotifications": true,
  "hideNavigationBar": true,
  "exitPinHash": "sha256_hex_hash",
  "scheduleStart": "08:00",
  "scheduleStop": "20:00",
  "rebootTime": "03:00",
  "remoteUrl": "https://policy.example.com/device/123",
  "remoteToken": "your-token"
}
```

The remote token is sent as `Authorization: Bearer <token>`.

## Article

If you want to learn more about the technical details and the design process of this project, you can read my article on Medium:

https://medium.com/@osamaalek/how-to-build-a-kiosk-launcher-for-android-part-1-beb54476da56
https://medium.com/@osamaalek/how-to-build-a-kiosk-launcher-for-android-part-2-9a529f503c11

## License

Kiosk Launcher is licensed under the Apache License 2.0. See [LICENSE](https://github.com/osamaalek/Kiosk-Launcher/blob/master/LICENSE) for more details.

## Contact

If you have any questions, feedback, or suggestions, feel free to contact me at osamaalek@gmail.com or open an issue on GitHub.
