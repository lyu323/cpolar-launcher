# 更新日志

## v1.0

- 本机写 `cpolar.yml`，`start-all` 单进程启动全部隧道，支持可视化增删改多条隧道
- 公网地址双通道获取：stdout `Forwarding` + 本机 `4040/api/tunnels`
- 从 `nativeLibraryDir` 执行官方内核，注入 Android 所需的 HOME / TMPDIR / SSL_CERT_DIR
- 日志脱敏、开机自启、快捷磁贴、Web UI 9200、通知栏启停
- 21 套主题、液态玻璃弹窗、隐藏后台、免责声明、设备适配说明
- 支持导入自定义内核，便于替换新版官方客户端

### 开源发布处理

- 源码仓库与源码 zip **不含**第三方 `libcpolar.so`、签名密钥、硬编码密码、令牌与个人配置
- 构建脚本的工具链路径与签名密码全部改为环境变量
- 文档统一使用 GitHub 提供的项目地址
