# EasyClash Android

EasyClash 的 Android 客户端，基于 [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) 精简改造。

与 [EasyClash 桌面版](https://github.com/zhang673101454/EasyClash) 同一产品思路：**点订阅即用、智能分流、多订阅互刷**。

## 保留功能

- 订阅 URL 添加 / 编辑 / 删除 / 刷新（流量与节点）
- **点订阅**：开启或关闭代理（再点同一条关闭）
- **多订阅互刷**：先点开能用的订阅 → 再对其它订阅菜单「更新」
- 节点列表 + 一键刷新测速
- 智能模式（规则分流）
- 通知栏流量（默认开）
- 固定浅色，无设置页

已隐藏：日志、规则提供者、网络/TUN/Override、访问控制、文件/二维码导入、模式切换等。

## 典型用法

1. 右上角 **+** 添加订阅 URL  
2. **点该订阅** → 授权 VPN → 上网  
3. 双订阅：先点能直连更新的订阅开代理，再对另一条点菜单 **更新**，然后点切换过去  

## 下载

[GitHub Releases](https://github.com/zhang673101454/EasyClash-Android/releases) — 推荐 **arm64-v8a**。换包前请先卸载旧版（签名可能变化）。

## 预览

静态界面稿：`docs/ui-preview.html`
