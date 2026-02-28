# Kiosk Launcher

Kiosk Launcher 是一款 Android 应用，可将设备转变为策略驱动的 Kiosk 启动器。它可以锁定设备到一个或多个已配置的应用，应用锁屏任务限制，并为操作员提供受保护的管理入口。

## 功能特性

- 基于应用的 Kiosk 启动器（无 WebView 主页）
- 单应用模式（短延时后自动启动已配置应用）
- 多应用模式（仅显示已配置且已安装的应用）
- 锁屏任务控制：状态栏、通知栏及沉浸式导航行为
- PIN 保护的管理入口与 Kiosk 退出
- 两步防误触退出手势（5 次点击 + 1.2 秒长按）
- 本地设置 UI + 远程策略同步（HTTP JSON）
- 定时启动/停止 Kiosk 及定时重启
- Android 10+（建议使用设备所有者模式以实现完整限制）

## 使用方法

### 配置 Kiosk 应用

1. 从管理入口打开设置。
2. 选择允许的包名。
3. （可选）启用单应用模式以自动启动指定应用。
4. 保存并应用策略。

### 进入管理 / 退出 Kiosk

使用左上角热区：

1. 在 3 秒内快速点击 5 次以激活管理入口。
2. 长按 1.2 秒。
3. 输入 PIN 打开设置。
4. 需要退出 Kiosk 时使用「退出Kiosk模式」。

## 设备所有者要求

若要获得完整的锁屏任务行为（尤其是状态栏/通知栏限制），请使用设备所有者配置。

在全新配置的设备上设置设备所有者：

```bash
adb shell dpm set-device-owner com.osamaalek.kiosklauncher/.MyDeviceAdminReceiver
```

若未设置设备所有者，部分厂商 ROM 可能回退为固定模式或忽略部分锁屏任务策略。

## 远程策略 JSON 示例

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

远程 token 以 `Authorization: Bearer <token>` 形式发送。`kioskUrl` 保留以兼容旧版本，在应用启动器模式下不作为主页使用。

## 故障排除

- 出现「App is pinned」：当前 ROM/配置下设备所有者未完全生效。
- Kiosk 中无法启动所选应用：确认应用已安装且包含在 `allowedPackages` 中。
- 限制未生效：验证设备所有者已激活，然后在设置中重新保存策略。

## 技术文章

如需进一步了解本项目的技术细节与设计过程，可阅读 Medium 上的系列文章：

https://medium.com/@osamaalek/how-to-build-a-kiosk-launcher-for-android-part-1-beb54476da56
https://medium.com/@osamaalek/how-to-build-a-kiosk-launcher-for-android-part-2-9a529f503c11

## 许可证

Kiosk Launcher 遵循 Apache License 2.0。详见 [LICENSE](https://github.com/osamaalek/Kiosk-Launcher/blob/master/LICENSE)。

## 联系方式

如有问题、反馈或建议，欢迎通过 osamaalek@gmail.com 联系，或在 GitHub 上提交 Issue。
