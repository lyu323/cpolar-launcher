# Cpolar 启动器 · Android 端 cpolar 客户端启动器

第三方本地启动器：在手机本地运行 **cpolar 官方客户端**，把本机服务映射到公网。
**本仓库不提供任何服务器、节点或隧道服务**；账号、authtoken、节点与套餐规则均由 cpolar 官方决定。

> 非官方项目，与 cpolar 官方无隶属关系。采用 [MIT](LICENSE) 许可。仓库只收录本 App 自己写的 Java 代码与资源。

**包名 `net.lyu323.cpolar` 保持不变**：v1.3.0 可**直接覆盖安装 v1.2.0**，authtoken、隧道配置与主题全部保留。

## 下载

[Releases](https://github.com/lyu323/cpolar-launcher/releases) · 当前版本 **v1.3.0**

| 文件 | 说明 |
| --- | --- |
| `CpolarLauncher-v1.3.0.apk` | 安装版（已内置 cpolar 官方 ARM 客户端） |
| `CpolarLauncher-v1.3.0-source.zip` | 与本仓库一致的源码快照 |
| `CpolarLauncher-v1.3.0-notes.md` | 使用说明与已知限制 |

## 检查更新 · 本地下载 · 更新日志（v1.3.0）

**启动后自动检查一次**：App 启动几秒后访问本仓库公开接口
`https://api.github.com/repos/lyu323/cpolar-launcher/releases/latest`：

- **有新版本才弹窗**，没有就完全静默（不 Toast、不打扰）；
- **不是强制更新**：同一版本只提醒一次，6 小时内重复启动不重复联网；
- **设置里可关闭**「启动后自动检查更新」，关掉后只有手动点「检查更新」才联网；
- 手动检查带 **10 分钟本机缓存**，可「↻ 强制刷新」忽略缓存；
- 只 GET 版本信息（tag / 标题 / 更新说明 / 下载直链），**不上传**任何设备、账号、令牌 / authtoken 或日志；不带任何令牌。

**App 内本地下载（不跳浏览器）**：更新弹窗点「⬇ 本地下载」→ 直接从 GitHub Release 资产把 APK 下载到 App 私有目录（带进度条），完成后**调起系统安装界面，是否安装由你确认**。不自动下载、不静默安装、不绕过系统签名校验；下载地址只接受 `github.com` 及其资产 CDN，重定向后的域名仍会再校验一次，并校验文件长度与 ZIP 魔数。下载失败可回退「打开 Releases 下载页」或「复制下载直链」。

**更新日志**：弹窗里的 Release 说明会自动整理成好读的文字（去掉 Markdown 标记）；点「查看更新日志」直接打开本仓库 [CHANGELOG.md](https://github.com/lyu323/cpolar-launcher/blob/main/CHANGELOG.md) 看每一版更新了什么，也可在关于页 / 设置页进入。


## 3D 动态启动特效（可关闭）

v1.3.0 新增纯 Canvas 手写、零第三方依赖的启动界面：透视投影的旋转线框二十面体（三层描边辉光）、外层反向立方体、三条倾斜光环、60 个环绕粒子、中心光晕、扫描光带、应用名与呼吸进度条，约 2.3 秒后淡出。

**设置 → 版本更新 → 「3D 动态启动特效」可关闭**，关闭后启动更快、更省电；特效加载失败也不影响主流程。

> 免责声明版本已升级，**首次打开本版会重新弹一次**免责声明，点同意即可（本机配置不受影响）。


## 联系作者（v1.2.0 已修复）

装了 QQ 却跳浏览器的问题已修复：走 QQ 官方落地页的 `mqqopensdkapi://bizAgent/qm/qr` 通道直达加好友页，落地页内置、点击零联网；装了 QQ 时不再交给系统浏览器。弹窗里可看本机检测结论、填作者 QQ 号、单独「用浏览器打开」，且**不再自动复制链接**。

## 同账号下的两个项目

| 仓库 | 地址 | 说明 |
| --- | --- | --- |
| `cpolar-launcher` | https://github.com/lyu323/cpolar-launcher | 本项目（检查更新 / 本地下载连接这里） |
| `chmlfrp-launcher` | https://github.com/lyu323/chmlfrp-launcher | 同账号下的另一个项目（ChmlFrp 启动器，两个架构 APK 都连接它自己的 Releases） |

两个项目各自独立维护、各自开源、包名不同（签名证书相同，可同时安装、互不覆盖），都带检查更新与本地下载，但**各连各的仓库**，不互相混用。

## 主要能力

- 本机生成 `cpolar.yml`，`start-all` 一个进程启动全部隧道；可视化增删改多条隧道
- 公网地址双通道抓取：stdout `Forwarding` + 本机 `127.0.0.1:4040/api/tunnels`
- 从 `nativeLibraryDir` 执行内核，注入 `HOME` / `TMPDIR` / `SSL_CERT_DIR` 适配 Android
- 日志脱敏、开机自启、快捷磁贴、Web UI 9200、通知栏启停
- 21 套主题、液态玻璃弹窗、隐藏后台、免责声明、设备适配说明
- 支持「导入自定义内核」替换官网最新版客户端
- 检查更新 / 本地下载 / 项目地址 / Star / 更新日志 / 开源许可

## 开源边界

**公开**：本 App 自己编写的 Java 源码、资源、构建脚本、文档（MIT）。

**不公开 / 不在仓库与源码 zip 中**：
- 签名密钥 `.jks` 与任何硬编码密码 —— `build.sh` 只从 `KS_PASS` / `KEY_PASS` 读取；
- 第三方闭源 `libcpolar.so` 内核二进制 —— 它是 cpolar 官方发布的客户端，**不属于本仓库代码**，请到官网下载后放入 `CPOLAR_LIBS_DIR`；
- 任何访问令牌、authtoken、账号密码、设备标识、个人节点地址或私有域名。

## 隐私

配置、令牌 / authtoken、日志、隧道列表只存在本机应用私有目录，不上传任何服务器。
外部连接：官方 API 与节点、`api.github.com`（启动自动检查一次 + 手动检查）、浏览器跳转 `github.com`，以及**仅在你点「本地下载」时**的 GitHub 资产 CDN。
App 内置的「联系作者」是 QQ 官方公开加好友短链（非凭证），点击只拉起 QQ。
无广告 / 统计 / 崩溃上报 / 推送 SDK。下载与安装均需你手动触发和确认。


## 从源码构建

需要自备 JDK 17、Android SDK build-tools（`aapt2` / `d8` / `zipalign` / `apksigner`）、`android-35/android.jar`，以及 **cpolar 官方 ARM 客户端**（改名 `libcpolar.so` 放到 `lib/<abi>/` 下）。

```bash
export JH=/path/to/jdk17
export BT=/path/to/android-build-tools
export AJ=/path/to/android-35/android.jar
export CPOLAR_LIBS_DIR=/path/to/cpolar-libs   # 下含 lib/armeabi-v7a/libcpolar.so
export OUT_DIR=./out
# 可选签名（不设置则输出未签名 APK，仅供本地调试）
export KS_FILE=/path/to/your.jks KS_PASS='密码' KEY_PASS='密码'
bash build.sh
```

## 已知限制

- 内置的是较早期官方 ARM 客户端，可能与当前服务端不兼容；可在 App 内「导入自定义内核」换成官网最新版。
- 免费版随机域名约 24 小时变化；固定域名需官方面板配置。
- 未做真机隧道连通验证；DNS / CA 路径在 Android 与 Linux 上存在差异。

## 许可

[MIT](LICENSE) · 第三方 `libcpolar.so` 客户端二进制**不在本许可范围内**，其权利归 cpolar 官方所有。

项目主页、下载与反馈入口统一使用 GitHub 地址：https://github.com/lyu323/cpolar-launcher
