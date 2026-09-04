# EasyClash Android

EasyClash 的 Android 客户端，基于 [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) 精简改造。

极简代理：点订阅即用，智能分流，双订阅互拉（与 [EasyClash 桌面版](https://github.com/zhang673101454/EasyClash) 同一产品思路）。

## 功能

- 订阅 URL 添加 / 编辑 / 刷新 / 流量显示
- 一键开/关代理（Android VPN）
- 节点列表与切换
- 智能模式（`GEOSITE,cn` + `GEOIP,CN` 直连）
- 已隐藏：日志、规则提供者、高级网络/TUN 选项、文件/二维码导入

## 下载

[GitHub Releases](https://github.com/zhang673101454/EasyClash-Android/releases) — 推荐 **arm64-v8a** APK。

## 典型用法

1. 安装 APK，打开 EasyClash
2. 点 **订阅** → **+** → 粘贴订阅 URL（可填备注）
3. 选中订阅后回到主页，点卡片 **开启代理**
4. 在 **节点** 里切换线路

双订阅：先启用能用的订阅并开代理，再在另一条订阅里点更新。

## 构建（可选）

云端编译：push 到 `main` 后 GitHub Actions **Build Debug** 自动打包，或手动 Run workflow。

本地需 OpenJDK 11+、Android SDK、Go、CMake：

```bash
git submodule update --init --recursive
./gradlew app:assembleAlphaRelease
```

## 许可

基于 CMFA（GPLv3）。修改版源码见本仓库。
