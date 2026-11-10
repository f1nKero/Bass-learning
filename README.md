# BassMemo 🎸

从零开始记住四弦贝斯指板音名的离线安卓应用。

## 功能
- 标准 E A D G 调弦，0–12 品互动指板
- 点按查看音名（含升号）
- 位置识音、反向找音两种测验
- 逐级解锁：空弦 → 0–3 品 → 0–5 品 → 0–12 品
- 错题加权抽取、连续答对与正确率
- 进度保存在手机本地，无需网络

## 安装
在 GitHub **Actions** 页面运行 **Build Android APK**（或推送代码触发），成功后在该次运行的 **Artifacts** 下载 `BassMemo-debug-apk`，解压安装 APK。

## 技术
Kotlin / Jetpack Compose / Android Gradle Plugin 8.7.3 / Gradle 8.9 / JDK 17。最低 Android 8.0 (API 26)。

## 音名规则
标准调弦从低到高 E1 A1 D2 G2。每升一品升高一个半音。第一版统一显示升号（如 F#），不区分同音异名。

## 说明
目前为可编译的 MVP 源码，APK 由 GitHub Actions 构建，实际构建结果以 Actions 日志为准。
