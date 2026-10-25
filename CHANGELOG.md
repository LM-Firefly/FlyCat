# Changelog

本文件记录 FlyCat 的重要变更。更新日志按主题分组整理，与网站[更新日志](website/update/history.mdx)同步。

## [Unreleased]

## [0.5.9] - 2026-10-25

### 新增

- **日志中心双入口**：拆分 mihomo 日志 / App 日志入口，新增 mihomo 实时日志预览（级别筛选、搜索、导出，可见时才订阅，模式感知 local/root/remote）；App 日志分区展示应用日志与崩溃日志，右上角快切日志级别；Go 端取消内核日志级别过滤，改客户端实时筛选（`feat(log)`）
- **特权服务**：接入 priv-kit 特权服务，Root/无线ADB/静态端口/手动/Shizuku 四通道启动 Privileged Server，XMSF 防火墙绕过改走 priv-kit；新增设置页（状态卡+四 Tab+配对弹窗+启动日志，通知栏内嵌输码配对），Shizuku 降级为外部启动通道（`feat(privilege)`）
- **策略组图标与国旗拼接显示**：策略组图标与节点国旗在面包屑、代理链路、节点卡片全局拼接展示（`feat(proxy)`）
- **自定义内核导入**：导入改底部面板，内核链接/本地插件文件双模式，支持本地插件文件安装激活（`feat(settings)`）
- **MIUI/HyperOS 开机自启适配**：小米设备开启「自动启动」时若私有自启动权限（AppOps 10008）未授予，弹 toast 并跳转系统自启动管理页（`feat(settings)`）

### 修复

- **策略组图标不显示**：0.5.8 布局统一误删了策略组卡片的图标渲染调用；恢复单栏/双栏卡片图标（Close #208），渲染守卫改为仅加载成功才显示（原 `result != null` 对失败态也为真），并为图片请求设显式尺寸——避免「仅成功才绘制」与「尺寸依赖绘制」互相死锁导致图标永不加载；失败/加载中整块折叠不留空白（`fix(proxy)`）
- **国旗图标比例**：改用 Noto Emoji region-flags 真实比例矩形图标（`fix(ui)`）
- **连接页/流量统计页表头**：表头固定出列表，随 TopBar 折叠收缩（`fix(dashboard)`）
- **eBPF 统计回调补丁**：重定基至 liuran001 02581ff38，适配内核统计层休眠唤醒机制（trafficWake），OnJoin/OnLeave/OnTrafficUpdate 消费端契约不变（`fix(patches)`）
- **Javet 库解析**：按候选名兜底解析落盘库，XZ 解压改按魔数判断，扩展包缺库时回退主 APK（`fix(build)`）

### 性能优化

- **延迟测试刷新**：延迟结果改 per-node 流发布，测速结果只更新受影响徽标不再整列表替换（消除 NodeCard 重组卡顿）；查询与发布移入同一锁内原子化，消除慢查询旧快照覆盖测速结果；批量测速期改 700ms 渐进刷新+收尾对账（`perf(proxy)`）
- **高频载荷序列化**：Go↔Kotlin 与 root binder 的高频小对象（日志行、连接关闭事件、代理组、单点延迟）改分隔符/裸 int 编码，免去数百 KB JSON 双侧重序列化（保留 JSON 兜底，旧原生优雅降级）；root 模式日志改按需订阅（预览打开才持有逐行订阅）；测速热路径去冗余，700ms 周期刷新改内存增量重算（`perf(runtime)`）

### 变更

- **本地化实现重构**：移除 `ren.shiror.fyl.fytxt` 代码生成插件，多语言改为直接提交的 Kotlin + Lyricist（KSP）实现，翻译源迁移至 `locale/src`（`refactor(locale)`）
- **打包管线**：MMKV 按 ABI 分线（arm64-v8a/x86_64 对 mmkv64 2.4.3、armeabi-v7a/x86 对 mmkv32 1.3.14），universal 拆分 universal32/universal64 双分支、4 ABI 总包弃用；发布前产物自检（zip CRC/必备原生库/aapt2 badging/zipalign/apksigner）并写 `.apk.json` 身份 sidecar；更新匹配按位宽过滤合包（`build`）
- **so 原地压缩打包**：非引导 so strip 后原地 XZ 压缩，删除 :pack 模块与 Rust 载荷提取器，运行时按 XZ 魔数解载并注入 classloader 搜索路径（`feat(privilege)`）
- **APK 命名**：删除 geo 段（builtin/Extension→Extension），CI 改按完整连字符段严格提取 abi/build_id（`fix(build)`）
- **内核与依赖**：mihomo 与 native go 模块同步更新；Cargo.lock 依赖更新（boa、serde_json、syn 等）；CI 升级 checkout@v7 与 NDK 30.0.16248370（`chore`）

## [0.5.8] - 2026-10-09

### 新增

- **顶栏节点搜索**：顶栏放大镜唤出搜索框，输入即筛、滚动自动收起；配套与 Eye/Speed 同规格线条图标（`feat(proxy)`）

### 修复

- **切节点假死**：长时间/高网速后点击无响应且无提示——`release_object` 改非阻塞入队、关连接移到后台 goroutine、OnLeave 跨 JNI 前合并限流；`withAbandonableTimeout` 使阻塞 JNI 超时真正生效（`withTimeoutOrNull` 打不断原生调用），超时后重建 gateway 重试，仍阻塞则提示「切换超时」；刷新查询锁改外层释放，消除 `query already in flight` 永久跳过（`fix(proxy)`）
- **emoji 混排对齐**：面包屑、代理链路、节点卡片 emoji 与正文墨迹中心共线，修正下沿越界与上下超出不对称；面包屑按压反馈补全（按下 snap、松手弹簧回弹，短按也可见）（`fix(proxy)`）
- **双栏标题滚顶**：双栏左栏「代理」滚回策略组列表顶、右栏组名标题可点滚回节点列表顶（`fix(proxy)`）
- **自定义路由**：手动 YAML 编辑优先于预设改动，区域与条目文案本地化（`fix(custom-routing)`）
- **国旗空占位**：无国家码时不再渲染名称前的空白圆（`fix(proxy)`）

### 变更

- **显示模式统一**：移除简洁显示模式，仅保留详细布局；`ProxyDisplayMode.normalized()` 将旧 SIMPLE 枚举折叠至 DETAILED，旧偏好与备份自动归一化（`fix(proxy)`）
- **构建依赖精简**：移除 gropify 插件及其 Jackson 3 传递依赖，根治相关 Dependabot 告警；升级 mipstack、sing-tun 并同步 eBPF patch（`fix(proxy)`）
- **配置导入**：`ProfileImportType` 枚举替代导入类型常量，清理冗余文件（`refactor(profiles)`）

## [0.5.7] - 2026-09-27

### 新增

- **超级岛通知**：HyperOS 超级岛通知（当前节点、实时流量、订阅用量）与 Shizuku 授权支持；XMSF bypass 改会话式持有引用计数，修复首帧展开与遥控器接管竞态（`feat(notification)`）
- **代理页面包屑导航**：大标题改面包屑（代理 > 组名），点击回组/滚回顶部；列表行错位揭示动画（`feat(proxy)`）
- **节点延迟测试**：下拉刷新触发延迟测试并显示逐节点进度，逐节点完成即上报（`feat(proxy)`）
- **配置自动更新间隔**：创建配置可指定自动更新间隔（最小 30 分钟）（`feat(profiles)`）
- **Wi-Fi 自动化**：支持按 SSID 自动切换配置文件（`feat(wifi)`）
- **Mips TUN 协议栈**：`TunStack` 新增 Mips 条目（`fix(tun)`）

### 修复

- **代理页假死**：长时间后台返回假死、配置文件切换后节点数据过期——查询加超时防死锁、前台恢复强刷、事件前重置连接（`fix(proxy)`）
- **预览态切节点**：内核未运行时切节点明确提示并拦截，不再静默失败（`fix(proxy)`）
- **节点选择跳回**：强制选中前对未测速节点先测活，刷新加宽限延迟，防止高亮跳回自动节点（`fix(ui)`）
- **应用图标丢失**：新增进程级应用图标缓存，修复列表滚动时图标概率丢失（`fix(ui)`）
- **节点搜索栏**：搜索栏移出滚动列表固定显示，修复定位偏移（`fix(proxy)`）
- **弹窗关闭动画**：外部点击关闭改走动画结束收尾，不再直接中断关闭动画（`fix(proxy)`）
- **本地配置崩溃**：修复添加本地配置文件时崩溃 #185（`feat(profiles)`）
- **进程崩溃**：`PayloadInstaller` 改用 PathClassLoader，修复主题/字体/overlay 变更触发 `updateApplicationInfo` 导致的崩溃（`fix(runtime)`）
- **前台服务竞态**：`startForeground` 前移至文件 IO 之前，仅最新请求收尾（`fix(runtime)`）
- **eBPF/Root TUN**：eBPF 可用性检查与 Root TUN 启动逻辑改进，恢复时使用实际运行模式（`fix(tun)`）
- **覆写空链路**：空绑定链路改为通知 runtime 清除覆写而非报错（`fix(override)`）
- **遥控器接管**：服务在 onCreate 探测到遥控器接管时拒绝启动并 stopSelf（`refactor(runtime)`）

### 性能优化

- **JNI 锁**：重量级 JNI 查询移出互斥锁，防止后台返回前台时死锁（`refactor(runtime)`）
- **代理刷新**：配置编译单飞互斥与空结果负缓存防并发编译堆积；排序移出主线程；慢速刷新移出操作锁（`fix(proxy)`）
- **功耗**：灭屏完全挂起 UI 轮询、App 后台停止分组同步、自动更新检查仅前台执行、外部 IP 查询缓存（`refactor(runtime)`）
- **GC/事件**：Go 运行时 `GOGC=100` + `GOMEMLIMIT 512MiB`；连接事件瘦身为身份字段并携带增量；会话切换清理陈旧流量（`refactor(runtime)`）
- **运行时自愈**：新增 `RuntimeRecoveryWorker` 周期自愈；恢复失败降级 WorkManager 一次性重试（`fix(runtime)`）

### 变更

- **权限精简**：移除 `PROCESS_OUTGOING_CALLS` 权限与外呼广播监听，暗码仅保留 SECRET_CODE 路径（`fix(runtime)`）
- **遥控器架构**：新增 `RemoteSwitch` 统一外部控制器探测/挂起/回退生命周期，连续 3 次探测失败才回退本地运行时（`refactor(runtime)`）
- **构建/文档**：CI 工作流与 Gradle 依赖升级、Rust YAML 库重构、README 重写、国旗 URL 归一化（`chore`）
