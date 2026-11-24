# 啾比教务 (Qiubi JW)
进适用于**广东海洋大学**的教务系统，以此纪念伟大的 **广海空间**
一款基于 **Kotlin + Jetpack Compose + Material 3** 开发的原生 Android 教务与个人日程客户端，支持统一身份认证、课表与便签日程管理、自定义考试安排、成绩与分项构成查询、动态主题配色、自定义磨砂玻璃底部栏以及基于 GitHub Releases 的应用内自动更新推送。

## 主要功能

- **日程与课表视图**
  - 按周左右滑动切换周次，支持多学年/学期课表同步与非本周课程显隐。
  - **自定义便签日程**：提供作息时间段选择与 8 款折角便签配色；与课程时间冲突时优先展示课程并在左侧露出的便签边条上点击切换。
  - **考试日程联动**：考试信息以多彩渐变卡片直接呈现在日程表对应时间槽中。
- **考试安排与成绩绩点**
  - 自动同步考试安排，同时支持手动添加/编辑/删除自定义考试（刷新同步时自动保留）。
  - 汇总加权平均绩点（GPA）与总学分，点击单科成绩可实时拉取并缓存**平时/实验/期中/期末**等分数详细构成。
- **个性化外观**
  - **自定义主题引擎**：支持 10 组预设配色、HSL 调色盘、HEX 色值输入及浅色/深色/跟随系统模式。
  - **自定义磨砂玻璃底部栏**：支持悬浮胶囊 / 贴底玻璃双样式，内置实时**透明度**与**磨砂度**拖动条。
- **后台同步与版本更新推送**
  - Android Keystore 本地加密凭据存储，WorkManager 定时后台刷新与数据变动通知提醒。
  - **应用版本更新推送**：内置 GitHub Releases 自动检测（支持镜像加速与自动回退）、高优先级系统通知推送、应用内实时进度下载与一键调起系统安装。

## 本地构建

环境要求：JDK 17、Android SDK（API 36）。

```bash
# 运行单元测试并打包 Debug APK
./gradlew testDebugUnitTest assembleDebug

# 打包正式签名 Release APK（需在根目录配置 keystore.properties，切勿提交密钥）
./gradlew assembleRelease
```

`keystore.properties` 示例（已加入 `.gitignore`）：

```properties
storeFile=signing/qiubi-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=qiubi_release
keyPassword=YOUR_KEY_PASSWORD
```

## 发布新版本与自动推送

1. 在 `app/build.gradle.kts` 中递增 `versionCode` 与 `versionName`。
2. 使用同一份正式签名打包 `assembleRelease`。
3. 在本仓库创建新的 **GitHub Release**（Tag 如 `v1.0.1`），上传编译好的 `.apk` 附件并填写更新日志，客户端即可自动检测并推送更新。
