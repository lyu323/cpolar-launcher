# Cpolar 启动器 · Android 端 cpolar 客户端启动器

第三方本地启动器：在手机本地运行 **cpolar 官方客户端**，把本机服务映射到公网。
**本仓库不提供任何服务器、节点或隧道服务**；账号、authtoken、节点与套餐规则均由 cpolar 官方决定。

> 非官方项目，与 cpolar 官方无隶属关系。采用 [MIT](LICENSE) 许可。仓库只收录本 App 自己写的 Java 代码与资源。

**包名 `net.lyu323.cpolar`**（v1.2.0 起；更早版本使用不同的包名）。
改包名意味着**不能覆盖安装旧版**，会与旧版并存：建议卸载旧版，新版重新粘贴一次 authtoken（配置只存本机私有目录，跨包名无法自动迁移）。

## 下载

[Releases](https://github.com/lyu323/cpolar-launcher/releases) · 当前版本 **v1.2.0**

| 文件 | 说明 |
| --- | --- |
| `CpolarLauncher-v1.2.0.apk` | 安装版（已内置 cpolar 官方 ARM 客户端） |
| `CpolarLauncher-v1.2.0-source.zip` | 与本仓库一致的源码快照 |
| `CpolarLauncher-v1.2.0-notes.md` | 使用说明与已知限制 |

## 检查更新（连接 GitHub Releases）

设置页「版本更新」或关于页「检查更新」→ 访问本仓库公开接口
`https://api.github.com/repos/lyu323/cpolar-launcher/releases/latest`：

- **只在点击时联网**，不点不联网；只 GET 版本信息，**不上传**任何设备、账号、authtoken 或日志数据；不带任何令牌；
- **10 分钟本机缓存**：重复点不重复联网，弹窗标注是否命中缓存；可「↻ 强制刷新」；
- 结果可「复制 APK 下载直链」，设置页显示上次检查时间；
- 发现新版只给下载入口，**不自动下载、不静默安装**；已是最新显示「当前已是最新版本：1.2.0」；
- 关于页提供项目地址、GitHub Star、更新日志、内联 MIT 许可全文弹窗与另一个项目入口；长按版本号可复制构建信息。

## 联系作者（v1.2.0 修复）

之前「装了 QQ 却跳浏览器」的根因：只用普通 `https` 短链交给系统，且未声明 Android 11+ 包可见性。
现在改为 QQ 官方落地页的 Android 通道 `mqqopensdkapi://bizAgent/qm/qr` 直达加好友页，落地页已内置、点击零联网：

- Manifest 声明 `com.tencent.mobileqq` / `com.tencent.tim` 与 `mqqwpa` / `mqq` / `mqqapi` / `mqqopensdkapi` / `qm.qq.com`；
- 不再「先查询再跳」（部分 ROM 上带包名的 https 查询会假阴性），直接 `startActivity` 靠异常判成败；
- 装了 QQ 时**绝不给系统默认浏览器**；确实没装 QQ / TIM 才用浏览器；
- **不再自动复制链接**：只有点「复制链接」才写剪贴板，3 秒内不重复写；
- 弹窗显示本机检测结论，可填作者 QQ 号走资料卡通道，也有独立「用浏览器打开」按钮。

## 同账号下的两个项目

| 仓库 | 地址 | 说明 |
| --- | --- | --- |
| `cpolar-launcher` | https://github.com/lyu323/cpolar-launcher | 本项目（检查更新连接这里） |
| `chmlfrp-launcher` | https://github.com/lyu323/chmlfrp-launcher | 同账号下的另一个项目（ChmlFrp 启动器，两个架构 APK 都连接它自己的 Releases） |

两个项目各自独立维护、各自开源、包名不同（签名证书相同，可同时安装、互不覆盖），都带检查更新，但**各连各的仓库**，不互相混用。

## 主要能力

- 本机生成 `cpolar.yml`，`start-all` 一个进程启动全部隧道；可视化增删改多条隧道
- 公网地址双通道抓取：stdout `Forwarding` + 本机 `127.0.0.1:4040/api/tunnels`
- 从 `nativeLibraryDir` 执行内核，注入 `HOME` / `TMPDIR` / `SSL_CERT_DIR` 适配 Android
- 日志脱敏、开机自启、快捷磁贴、Web UI 9200、通知栏启停
- 21 套主题、液态玻璃弹窗、隐藏后台、免责声明、设备适配说明
- 支持「导入自定义内核」替换官网最新版客户端
- 检查更新 / 项目地址 / Star / 更新日志 / 开源许可

## 开源边界

**公开**：本 App 自己编写的 Java 源码、资源、构建脚本、文档（MIT）。

**不公开 / 不在仓库与源码 zip 中**：
- 签名密钥 `.jks` 与任何硬编码密码 —— `build.sh` 只从 `KS_PASS` / `KEY_PASS` 读取；
- 第三方闭源 `libcpolar.so` 内核二进制 —— 它是 cpolar 官方发布的客户端，**不属于本仓库代码**，请到官网下载后放入 `CPOLAR_LIBS_DIR`；
- 任何访问令牌、authtoken、账号密码、设备标识、个人节点地址或私有域名。

## 隐私

配置、令牌 / authtoken、日志、隧道列表只存在本机应用私有目录，不上传任何服务器。
外部连接：官方 API 与节点、以及**仅手动触发**的 GitHub 更新检查（`api.github.com` 公开 Release 接口）与浏览器跳转 GitHub 页面。
App 内置的「联系作者」是 QQ 官方公开加好友短链（非凭证），点击只拉起 QQ，不读取设备或账号信息。
无广告 / 统计 / 崩溃上报 / 推送 SDK。


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
