# Cpolar 启动器 · Android 端 cpolar 客户端启动器

第三方本地启动器：在手机本地运行 **cpolar 官方客户端**，把本机服务映射到公网。
**本仓库不提供任何服务器、节点或隧道服务**；账号、authtoken、节点与套餐规则均由 cpolar 官方决定。

> 这是**非官方**项目，与 cpolar 官方无隶属关系。仓库只收录本 App 自己写的 Java 代码与资源。

## 下载

到 [Releases](https://github.com/lyu323/cpolar-launcher/releases) 下载，当前版本 **v1.0**：

| 文件 | 说明 |
| --- | --- |
| `CpolarLauncher-v1.0.apk` | 安装版（已内置 cpolar 官方 ARM 客户端） |
| `CpolarLauncher-v1.0-source.zip` | 与本仓库一致的源码快照 |
| `CpolarLauncher-v1.0-notes.md` | 使用说明与已知限制 |

同账号下的 [ChmlFrp 启动器](https://github.com/lyu323/chmlfrp-launcher) 是姊妹项目，签名证书各自独立。

## 主要能力

- 本机生成 `cpolar.yml`，`start-all` 一个进程启动全部隧道
- 可视化增删改多条隧道：名称 / 协议 / 本地端口 / 地区 / 自定义域名
- 公网地址双通道自动抓取：进程 stdout 的 `Forwarding ...` + 本机 `127.0.0.1:4040/api/tunnels`
- 从 `nativeLibraryDir` 执行内核，注入 `HOME` / `TMPDIR` / `SSL_CERT_DIR` 适配 Android
- 日志脱敏、开机自启、快捷设置磁贴、Web UI 9200、通知栏启停
- 21 套主题、液态玻璃弹窗、隐藏后台、免责声明、设备适配说明
- 支持「导入自定义内核」：可自行替换成官网下载的最新版客户端

## 隐私与开源边界

**公开的内容**：本 App 自己编写的 Java 源码、资源、构建脚本、文档。

**不公开的内容**（这是本次开源刻意保留的部分）：
- 签名密钥 `.jks` 与任何硬编码密码（构建脚本只从环境变量读取）；
- 第三方闭源的 `libcpolar.so` 内核二进制 —— 它是 cpolar 官方发布的客户端，**不属于本仓库代码**，请到官网下载后自行放入 `CPOLAR_LIBS_DIR`；
- 任何访问令牌、authtoken、账号密码、设备标识、个人节点地址或私有域名；
- APK 里的内核仅作为已编译产物随 Release 分发，源码仓库与源码 zip 中均不含二进制。

配置、authtoken、日志、隧道列表**只存在本机应用私有目录**，不上传任何服务器。App 内置的「联系作者」是 QQ 官方公开加好友短链，点击只拉起 QQ，不读取设备或账号信息。

项目主页、下载与反馈入口统一使用 GitHub 提供的地址：https://github.com/lyu323/cpolar-launcher

## 从源码构建

需要自备：JDK 17、Android SDK build-tools（含 `aapt2` / `d8` / `zipalign` / `apksigner`）、`android-35/android.jar`、以及 **cpolar 官方 ARM 客户端**（改名 `libcpolar.so` 放到 `lib/<abi>/` 下）。

```bash
export JH=/path/to/jdk17
export BT=/path/to/android-build-tools
export AJ=/path/to/android-35/android.jar
export CPOLAR_LIBS_DIR=/path/to/cpolar-libs   # 下含 lib/armeabi-v7a/libcpolar.so
export OUT_DIR=./out

# 可选：签名（不设置则输出未签名 APK，仅供本地调试）
export KS_FILE=/path/to/your.jks
export KS_PASS='你的签名库密码'
export KEY_PASS='你的密钥密码'

bash build.sh
```

## 已知限制

- Release 里的 APK 内置的是较早期 ARM 版官方客户端，可能与当前服务端不兼容；可在 App 内「导入自定义内核」换成官网最新版。
- 免费版随机域名约 24 小时变化；固定域名需官方面板配置。
- 未做真机隧道连通验证；DNS / CA 路径在 Android 与 Linux 上存在差异，日志出现 `lookup failed` 时请看日志页。

## 反馈

[Issues](https://github.com/lyu323/cpolar-launcher/issues) 或 App 内「✉ 联系作者」。

## 许可

[MIT](LICENSE) · 第三方 `libcpolar.so` 客户端二进制**不在本许可范围内**，其权利归 cpolar 官方所有。
