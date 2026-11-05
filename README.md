# 千米之外 / FarShot

PICO Vibe Lab 投稿源码。Android Spatial 应用，版本 0.1 / versionCode 1。

## 构建

本仓库根目录就是 Gradle 工程。准备 JDK 21 和 Android SDK 35，设置 JAVA_HOME 和 ANDROID_HOME，然后执行

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease
```

Windows 使用 `gradlew.bat`。没有外部签名配置时，生成 `app/build/outputs/apk/release/app-release-unsigned.apk`，由发布平台签名。
原开发者签名配置及私钥不在仓库中，也不属于构建的必需输入。

Gradle 8.13 使用华为云镜像，分发文件 SHA-256 固定为官方校验值。所有运行时素材已随源码提供，无需再调用模型生成服务。

包名 `com.vrgames.sniper`。玩法代码、资源及模型来自本地正式 0.1 工程，没有为投稿修改游戏行为。
源码许可见 LICENSE，第三方和生成素材说明见 [素材来源](docs/素材来源.md)。

原项目说明与原始验证边界见 [原项目说明](docs/原项目说明.md)。本次投稿整理未运行模拟器或真机；本地源码构建检查不代表重新完成设备体验验收。

版本说明：原 README 标题写 v0.2，实际原 APK 与 Gradle 元数据仍为 0.1 / code 1。投稿使用实际构建版本，不擅自修改游戏版本或行为。
