# Changelog

本文件记录 FlyCat 的重要变更。更新日志按主题分组整理，与网站[更新日志](website/update/history.mdx)同步。

## [Unreleased] - 2026-09-25

### 新增

- **超级岛通知**：HyperOS 超级岛通知（当前节点、实时流量、订阅用量）与 Shizuku 授权支持；XMSF bypass 改会话式持有引用计数，修复首帧展开与遥控器接管竞态（`feat(notification)`）
- **节点延迟测试**：下拉刷新触发延迟测试并显示逐节点进度（`feat(proxy)`）
- **配置自动更新间隔**：创建配置可指定自动更新间隔（最小 30 分钟）（`feat(profiles)`）
- **Wi-Fi 自动化**：支持按 SSID 自动切换配置文件（`feat(wifi)`）
- **Mips TUN 协议栈**：`TunStack` 新增 Mips 条目（`fix(tun)`）

### 修复

- **代理页假死**：长时间后台返回假死、配置文件切换后节点数据过期——查询加超时防死锁、前台恢复强刷、事件前重置连接（`fix(proxy)`）
- **本地配置崩溃**：修复添加本地配置文件时崩溃 #185（`feat(profiles)`）
- **进程崩溃**：`PayloadInstaller` 改用 PathClassLoader，修复主题/字体/overlay 变更触发 `updateApplicationInfo` 导致的崩溃（`fix(runtime)`）
- **前台服务竞态**：`startForeground` 前移至文件 IO 之前，仅最新请求收尾（`fix(runtime)`）
- **eBPF/Root TUN**：eBPF 可用性检查与 Root TUN 启动逻辑改进，恢复时使用实际运行模式（`fix(tun)`）
- **覆写空链路**：空绑定链路改为通知 runtime 清除覆写而非报错（`fix(override)`）
- **遥控器接管**：服务在 onCreate 探测到遥控器接管时拒绝启动并 stopSelf（`refactor(runtime)`）

### 性能优化

- **JNI 锁**：重量级 JNI 查询移出互斥锁，防止后台返回前台时死锁（`refactor(runtime)`）
- **功耗**：灭屏完全挂起 UI 轮询、App 后台停止分组同步、自动更新检查仅前台执行、外部 IP 查询缓存（`refactor(runtime)`）
- **GC/事件**：Go 运行时 `GOGC=100` + `GOMEMLIMIT 512MiB`；连接事件瘦身为身份字段并携带增量；会话切换清理陈旧流量（`refactor(runtime)`）
- **运行时自愈**：新增 `RuntimeRecoveryWorker` 周期自愈；恢复失败降级 WorkManager 一次性重试（`fix(runtime)`）

### 变更

- **权限精简**：移除 `PROCESS_OUTGOING_CALLS` 权限与外呼广播监听，暗码仅保留 SECRET_CODE 路径（`fix(runtime)`）
- **遥控器架构**：新增 `RemoteSwitch` 统一外部控制器探测/挂起/回退生命周期（`refactor(runtime)`）
- **构建/文档**：CI 工作流与 Gradle 依赖升级、Rust YAML 库重构、README 重写、国旗 URL 归一化（`chore`）
