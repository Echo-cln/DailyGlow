# DailyGlow Android

<p align="center">
  <strong>DailyGlow 的 Android WebView 客户端</strong><br>
  使用 Android 应用打开线上 DailyGlow 网站，让网页端更新直接呈现在应用内。
</p>

<p align="center">
  <a href="https://github.com/Echo-cln/DailyGlow">GitHub 仓库</a> ·
  <a href="https://github.com/Echo-cln/DailyGlow/actions">构建工作流</a> ·
  <a href="https://github.com/Echo-cln/DailyGlow/issues">问题反馈</a>
</p>

## 项目导航

- [项目说明](#项目说明)
- [功能](#功能)
- [技术组成](#技术组成)
- [本地开发](#本地开发)
- [配置网站地址](#配置网站地址)
- [CI 签名构建](#ci-签名构建)
- [安全与隐私](#安全与隐私)
- [故障排查](#故障排查)
- [贡献](#贡献)
- [许可证](#许可证)

## 项目说明

DailyGlow Android 是轻量 Android 客户端。它以 Android WebView 加载已部署的 DailyGlow / DailyFlow 网站；网站负责页面、账户和业务数据，因此网页内容更新通常不需要重新构建 APK。

本应用不是独立的原生词汇或训练应用。登录、学习内容和训练数据由所加载的网站及其服务处理；没有网络或网站不可用时，相关功能也不可用。

## 功能

- 在应用内打开已部署的 DailyGlow 网站。
- 使用网站登录状态及 WebView Cookie / 本地存储。
- 显示页面加载进度，支持 Android 返回键。
- 网络失败时提供重试。
- 将外部 HTTPS 链接交给系统浏览器打开。
- 通过 GitHub Actions 构建签名 APK 工件。

具体页面和账户功能由线上网站提供。

## 技术组成

- Kotlin / Android
- Android WebView
- Gradle Kotlin DSL
- GitHub Actions 自动构建

## 本地开发

### 环境要求

- Android Studio
- JDK 17
- Android SDK Platform 35（或项目 Gradle 配置要求的版本）
- Android SDK Build Tools 与平台工具

### 克隆与构建

```bash
git clone https://github.com/Echo-cln/DailyGlow.git
cd DailyGlow
```

在 Android Studio 中打开仓库根目录，等待 Gradle Sync 完成，然后运行应用或构建 debug APK：

```bash
./gradlew assembleDebug
```

Windows PowerShell 使用：

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 通常位于 `app/build/outputs/apk/debug/`。

## 配置网站地址

WebView 目标地址由 `BuildConfig.DAILYFLOW_URL` 配置，当前值定义在 `app/build.gradle.kts`。只有在生产站点域名发生变化时才需要修改。修改后请确认：

1. 地址为有效的 HTTPS URL。
2. 网站允许在 WebView 中使用所需的登录流程。
3. OAuth 或第三方登录回调能够正确返回应用中的 WebView。
4. 隐私政策与 Cookie 行为符合网站的实际实现。

## CI 签名构建

工作流文件：`.github/workflows/android-webview.yml`。

工作流在 `feat/dailyflow-webview-android` 分支的 push 上运行，也可从 GitHub Actions 手动触发。签名构建依赖以下仓库 Secrets：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

成功后 APK 作为 `dailyglow-webview-signed-apk` 工作流工件上传；当前工作流不会自动创建 GitHub Release。请勿把 keystore、密码或签名凭据提交进仓库。

## 安全与隐私

- 应用因网站需要而启用 JavaScript 与 DOM Storage。
- 禁用明文 HTTP、文件访问与混合内容请求。
- 外部 HTTPS 链接交由系统浏览器打开。
- 网站账户及浏览记录仍受线上站点、WebView 存储和服务端隐私规则约束。
- 卸载应用或清除应用数据会影响本地 WebView 会话；服务端保存的数据按网站策略处理。

发布前请检查目标网站的 TLS、登录回调、外链行为和隐私政策。不要在公开 Issue 中提交账户凭据、Cookie 或个人数据。

## 故障排查

### 页面显示空白或无法加载

检查设备网络、生产站点是否可访问，以及 `DAILYFLOW_URL` 是否为正确的 HTTPS 地址。

### 登录后仍需重复登录

检查网站是否依赖不支持 WebView 的第三方登录方式，或 WebView Cookie / DOM Storage 设置是否发生变化。

### GitHub Actions 签名失败

确认四项 Android Secrets 已设置且 keystore Base64 内容完整；不要把密钥粘贴到日志或 Issue。

## 贡献

欢迎通过 Issue 报告问题或提出建议。提交缺陷时请提供 Android 版本、设备型号、应用版本、复现步骤和脱敏日志。涉及登录、网络权限或外链策略的改动应说明其安全影响。

## 许可证

请以仓库根目录的许可证文件为准。
