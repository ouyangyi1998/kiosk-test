# Android 10+ 潜在 Bug 排查报告

> minSdk 29, targetSdk 33

## 一、需关注的问题

### 1. SCHEDULE_EXACT_ALARM 在 API 29/30 上的兼容性

**现状**：`SCHEDULE_EXACT_ALARM` 自 API 31 引入，在 API 29/30 上该权限不存在。

**影响**：声明不存在的权限通常会被系统忽略，不会导致安装失败。在 API 29/30 上 `setExactAndAllowWhileIdle` 仍可正常使用。

**建议**：保持现状即可。若在部分设备上出现异常，可考虑用 `tools:ignore` 或按 API 做条件声明。

---

### 2. 精确闹钟在 Android 12+ 的运行时权限

**现状**：API 31+ 上，`SCHEDULE_EXACT_ALARM` 需用户授权；Android 14 (API 34) 上默认拒绝。

**影响**：设备可能不提示授权，导致精确闹钟不可用，定时功能被推迟或失效。

**建议**：在首次设置定时任务时，对 API 31+ 增加检查：

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val am = context.getSystemService(AlarmManager::class.java)
    if (!am.canScheduleExactAlarms()) {
        // 引导用户前往「设置 > 应用 > 闹钟和提醒」开启
    }
}
```

---

### 3. `onBackPressed()` 已废弃 (API 33)

**现状**：`Activity.onBackPressed()` 在 API 33 被标记为 deprecated。

**影响**：当前仍可用，但后续版本可能移除。

**建议**：迁移到 `OnBackPressedDispatcher`：

```kotlin
onBackPressedDispatcher.addCallback(this) {
    val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
    if (fragment is AppsListFragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerView, HomeFragment()).commit()
    } else {
        isEnabled = false
        onBackPressedDispatcher.onBackPressed()
        isEnabled = true
    }
}
```

---

### 4. `systemUiVisibility` 已废弃 (API 30)

**现状**：`KioskUtil.applyWindowPolicy()` 和 `stopKioskMode()` 中使用 `View.systemUiVisibility`，该 API 在 API 30 被废弃。

**影响**：在 API 30+ 上仍可用，但推荐使用 `WindowInsetsController`。

**建议**：长期可迁移到 `WindowInsetsControllerCompat`；短期可保持现有实现，在 API 30+ 上加 `@Suppress("DEPRECATION")`。

---

### 5. 包可见性与 queryIntentActivities

**现状**：`AppsUtil.getAllApps()` 和 `KioskUtil.findAlternativeHome()` 通过 `queryIntentActivities` 查询应用和 Launcher。  
应用声明了 `QUERY_ALL_PACKAGES`，理论上可见所有包。

**影响**：  
- `QUERY_ALL_PACKAGES` 上架 Google Play 需审核；  
- 不依赖该权限时，可改用 `<queries>` 精确声明所需 Intent。

**建议**：若上架 Play，可增加更细粒度的 `<queries>` 作为后备，降低对 `QUERY_ALL_PACKAGES` 的依赖。

---

### 6. BootReceiver 与开机时机

**现状**：仅监听 `BOOT_COMPLETED`，未处理 `ACTION_LOCKED_BOOT_COMPLETED`。

**影响**：在直接启动（Direct Boot）场景下，若设备在用户解锁前即启动，`BOOT_COMPLETED` 可能尚未发出，定时调度不会被恢复。

**建议**：如支持直接启动，可额外注册 `ACTION_LOCKED_BOOT_COMPLETED`；Kiosk 场景多为用户已解锁，通常可接受当前行为。

---

### 7. data_extraction_rules 与低版本

**现状**：`android:dataExtractionRules` 为 API 31 新增属性。

**影响**：在 API 29/30 上该属性不存在，系统一般会忽略，不影响运行。

**建议**：将对应配置放到 `res/xml-v31/` 等 API 限定目录，可避免在低版本上解析无关配置。

---

## 二、逻辑与边界情况

### 8. 定时任务触发时设备处于深度 Doze

**现状**：使用 `setExactAndAllowWhileIdle`，应在 Doze 时也能唤醒。

**影响**：部分厂商对 Doze 做了深度定制，可能仍会推迟闹钟。

**建议**：对关键部署设备做实际测试；必要时对 Device Owner 应用申请忽略电池优化（需用户授权）。

---

### 9. TimeUtil 时区

**现状**：`Calendar.getInstance()` 使用系统默认时区。

**影响**：设备更换时区或跨时区部署时，“08:00” 等时间可能不符合预期。

**建议**：固定场景一般可接受；有明确需求时，可增加时区或本地时区配置。

---

### 10. 退出 Kiosk 时的 alternativeHome

**现状**：`findAlternativeHome()` 取第一个非本应用的 HOME Activity。

**影响**：若存在多个 Launcher，可能不是用户预期的默认 Launcher；部分 ROM 可能返回异常结果。

**建议**：当前逻辑在大多数设备上可用；若需更精确，可尝试 `PackageManager.getLaunchIntentForPackage("launcher.package")` 等接口。

---

## 三、已确认无问题的部分

| 项目 | 说明 |
|------|------|
| PendingIntent.FLAG_IMMUTABLE | API 31+ 必需，已使用 |
| Vibrator / VibratorManager | 已做 API 分支 |
| VibrationEffect | 已按 API 26+ 分支 |
| LockTask 相关 API | 均在 minSdk 29 以上支持 |
| DevicePolicyManager | 已有 try-catch 处理厂商差异 |

---

## 四、修复优先级建议

1. **高**：在设置中增加 `canScheduleExactAlarms()` 检查与引导（API 31+）
2. **中**：将 `onBackPressed` 迁移到 `OnBackPressedDispatcher`
3. **低**：将 `systemUiVisibility` 迁移到 `WindowInsetsController`
4. **低**：按需增加 `ACTION_LOCKED_BOOT_COMPLETED` 支持
