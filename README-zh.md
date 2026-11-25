# RAF Capsule OS

<p align="center">
  <strong>🌐 语言:</strong>
  <a href="README.md">English</a> •
  <a href="README-in.md">Bahasa Indonesia</a> •
  <a href="README-ja.md">日本語</a> •
  <a href="README-ko.md">한국어</a> •
  <a href="README-zh.md"><strong>简体中文</strong></a> •
  <a href="README-es.md">Español</a>
</p>

<p align="center">
  <strong>适用于 Android 13–17 的动态前置摄像头灵动胶囊与交互状态悬浮层</strong><br/>
  基于 Kotlin 2.x • Jetpack Compose • Material 3 Expressive • Navigation 3 • AGSL 着色器 • Shizuku • 整洁架构打造
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-v1.4.0-1976D2" alt="版本 v1.4.0" />
  <img src="https://img.shields.io/badge/Min%20SDK-33%20(Android%2013)-3DDC84?logo=android&logoColor=white" alt="Min SDK 33" />
  <img src="https://img.shields.io/badge/Target%20SDK-37%20(Android%2017)-0078D7?logo=android&logoColor=white" alt="Target SDK 37" />
  <img src="https://img.shields.io/badge/Languages-14%20种语言-9C27B0" alt="14 种语言" />
</p>

**RAF Capsule OS** 将手机前置摄像头挖孔变为支持**竖屏与横屏双模式**、1:1 硬件精准对齐的动态交互胶囊，内置 VSYNC 同步弹簧物理引擎、AGSL 实时神经光效着色器、超轻连续触感反馈与纯 Kotlin 状态协调器。

---

## 视觉展示

<table>
  <tr>
    <td align="center" width="50%">
      <img src="docs/media/media-player.gif" alt="媒体播放器" width="100%" /><br/>
      <strong>媒体播放器与实时秒表时钟</strong>
    </td>
    <td align="center" width="50%">
      <img src="docs/media/separated-bubble.gif" alt="独立状态气泡" width="100%" /><br/>
      <strong>独立状态气泡与液态融合动画</strong>
    </td>
  </tr>
  <tr>
    <td align="center" colspan="2">
      <img src="docs/media/volume-mixer.gif" alt="音量混音器" width="50%" /><br/>
      <strong>全新 M3 Expressive 音量混音器</strong>
    </td>
  </tr>
</table>

### 横屏模式特性

| 功能 | 横屏表现 |
|---|---|
| **侧边纵向紧凑胶囊** | 跟随旋转后的摄像头位置，以纵向胶囊贴合侧边显示 |
| **纵向状态气泡** | 配备垂直指示点并支持上下滑动切换活跃状态 |
| **全高展开卡片** | 对称内边距、横向毛玻璃渐变，手电筒保持紧凑方形例外 |
| **自然横屏手势** | 横向滑动展开/收起，纵向滑动切换卡片或媒体会话 |
| **挖孔两侧实时栏** | 展示应用图标名称、带秒数的实时时钟以及点击直达日历的完整日期 |

---

## 核心特性

- **三态动态胶囊（竖屏 & 横屏）**：1:1 挖孔电量环、紧凑状态胶囊（竖屏横向 / 横屏纵向）以及可自定义圆角（`0–56 dp` 或自动贴合屏幕圆角）的毛玻璃展开卡片。
- **完整横屏模式**：跟随旋转后的摄像头位置，提供侧边纵向胶囊、纵向状态气泡与垂直指示点、全高卡片（手电筒保持紧凑方卡）、横向展开收起与纵向滑动切换。
- **挖孔两侧实时信息栏**：在展开卡片顶部左右分别展示应用图标名称，以及带秒数的实时时钟和点击直达日历的完整日期。
- **独立状态气泡**：支持手电筒、热点、实时活动、飞行模式、勿扰模式、省电模式、VPN、静音/振动、流量节省共 9 种状态。
- **节拍响应音频可视化（零第三方依赖）**：采用 Android 原生 `Visualizer`（`audioSessionId = 0`）、经 `AudioManager.getStreamVolumeDb` 补偿的 32 位 `MEASUREMENT_MODE_PEAK_RMS` 硬件响度检测与纯 Kotlin 三频段 FFT 分析（`bass`、`mid`、`high`、`beatPulse`），实现均衡器级动态响应——舒缓音乐柔和律动，重低音鼓点强劲爆发，覆盖 **State 3 展开卡片**（AGSL 音浪背景、边框神经流光、屏幕外圈光晕、精致专辑框、旋转 M3 曲奇按钮）及 **State 2 / State 1 紧凑视图**（`横向胶囊`、`摄像头环`、`专辑环` 与实时三频段 EQ 条）。
- **媒体播放器、LRC 歌词与全新音量混音器**：多会话切换、实时歌词、音频输出切换、支持点击静音与实时百分比显示的磨砂音量条及响铃/振动/静音分段栏。
- **独立权限中心 + Shizuku + 小米澎湃 OS 专项兼容**：支持 **Shizuku** 免电脑一键授予 `WRITE_SECURE_SETTINGS`、ADB 命令一键复制，并针对小米 / Redmi / POCO 设备提供防崩溃保护与自启动/省电无限制/后台弹出界面快捷入口（不影响 Pixel 行为）。

---

## 安装与授权

1. 前往 [GitHub Releases](https://github.com/RidhwanAF/RAF-Capsule/releases/latest) 下载最新 `app-release.apk` 并安装（推荐使用 `adb install -r app-release.apk`）。
2. 打开 **RAF Capsule OS → 权限与设置**，授予无障碍服务和通知使用权。
3. 授予 `WRITE_SECURE_SETTINGS`（隐藏系统重复悬浮通知）与 `RECORD_AUDIO`（音频节拍可视化）：
   - **Shizuku（无需电脑）**：通过无线调试启动 [Shizuku](https://shizuku.rikka.app/)，在应用内点击 **通过 Shizuku 授权**。
   - **ADB**：
     ```bash
     adb shell pm grant com.raf.capsuleos android.permission.WRITE_SECURE_SETTINGS
     adb shell pm grant com.raf.capsuleos android.permission.RECORD_AUDIO
     adb shell appops set com.raf.capsuleos RECORD_AUDIO allow
     adb shell appops set com.raf.capsuleos SYSTEM_ALERT_WINDOW allow
     ```

---

### 卸载前恢复 Android 默认设置（ADB）

RAF Capsule OS 运行时可抑制 Android 系统原生悬浮横幅通知（`heads_up_notifications_enabled = 0`），使通知集中显示在灵动胶囊内。如需卸载本应用（或卸载后发现系统横幅通知仍未弹出），请运行以下 ADB 命令恢复系统默认设置：

```bash
# 重新启用系统原生悬浮横幅通知弹窗
adb shell settings put global heads_up_notifications_enabled 1

# （可选）如曾修改状态栏图标隐藏列表，可恢复默认
adb shell settings delete secure icon_blacklist
```

*（提示：卸载前在应用内关闭**隐藏系统横幅通知**或暂停/关闭无障碍服务，系统也会自动恢复该设置。）*
