# Kiosk Launcher

Kiosk Launcher is an Android app that turns a device into a policy-driven kiosk launcher. It can lock the device to one or more configured apps, apply lock-task restrictions, and provide a protected management entry for operators.

## Features

- App-based kiosk launcher (no WebView homepage)
- Single-app mode (auto-launches the configured app after a short delay)
- Multi-app mode (shows only configured and installed apps)
- Lock-task controls: status bar, notifications, and immersive navigation behavior
- PIN-protected management access and kiosk exit
- Two-step anti-mistouch exit gesture (5 taps + 1.2s long press)
- Local settings UI + remote policy sync (HTTP JSON)
- Schedule start/stop kiosk and scheduled reboot
- Android 10+ (Device Owner recommended for full restrictions)

## Usage

### Configure kiosk apps

1. Open Settings from the management entry.
2. Choose allowed packages.
3. Enable single-app mode (optional) to auto-launch one app.
4. Save and apply policy.

### Enter management / exit kiosk

Use the hotspot in the top-left corner:

1. Tap 5 times quickly (within 3 seconds) to arm management entry.
2. Long-press for 1.2 seconds.
3. Enter PIN to open Settings.
4. Use "退出Kiosk模式" if you need to leave kiosk.

## Device Owner Requirement

For full lock-task behavior (especially status/notification restrictions), use Device Owner provisioning.

Set Device Owner on a freshly provisioned device:

```
adb shell dpm set-device-owner com.osamaalek.kiosklauncher/.MyDeviceAdminReceiver
```

Without Device Owner, some vendor ROMs may fall back to pinned mode or ignore parts of lock-task policy.

## Remote policy JSON example

```json
{
  "kioskUrl": "",
  "allowedPackages": ["com.osamaalek.kiosklauncher", "com.example.app"],
  "singleAppMode": false,
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
`kioskUrl` is retained for compatibility but is not used as homepage in app-launcher mode.

## Troubleshooting

- `App is pinned` appears: Device Owner is not fully active on current ROM/provisioning state.
- Can't launch selected app in kiosk: ensure the package is installed and included in `allowedPackages`.
- Restrictions not applied: verify Device Owner is active, then re-save policy in Settings.

## Article

If you want to learn more about the technical details and the design process of this project, you can read my article on Medium:

https://medium.com/@osamaalek/how-to-build-a-kiosk-launcher-for-android-part-1-beb54476da56
https://medium.com/@osamaalek/how-to-build-a-kiosk-launcher-for-android-part-2-9a529f503c11

## License

Kiosk Launcher is licensed under the Apache License 2.0. See [LICENSE](https://github.com/osamaalek/Kiosk-Launcher/blob/master/LICENSE) for more details.

## Contact

If you have any questions, feedback, or suggestions, feel free to contact me at osamaalek@gmail.com or open an issue on GitHub.
