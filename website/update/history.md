---
title: "更新日志"
description: "FlyCat 项目的版本更新记录和变更历史"
---

# 更新日志

FlyCat 项目的版本更新记录和变更历史。本页与仓库 [`CHANGELOG.md`](https://github.com/LM-Firefly/FlyCat/blob/Moes/CHANGELOG.md) 同步维护。

<Card title="GitHub Releases" icon="download" horizontal href="https://github.com/LM-Firefly/FlyCat/releases">

  前往 GitHub Releases 下载各历史版本安装包。

</Card>

## v0.6.0 · 液态玻璃视觉体系、规则页修复与连接链路提速 {#v0-6-0}

*2026-11-07 · 功能更新 / 性能优化 / 问题修复 / 变更*

### 液态玻璃视觉体系

- 全局 UI 迁移至基于 RuntimeShader 的 miuix 液态玻璃：新增玻璃着色器与样式参数体系，对话框、底部弹层、弹窗、导航栏、顶栏、标签栏、下拉与级联菜单全面玻璃化；无 RuntimeShader 的设备自动回退实色 + 发丝级边缘光，保证兼容
- 顶栏委托 GlassTopAppBar（渐进色带随滚动渐显、折叠大标题、pinned 钉住模式），底栏改 GlassNavigationBar（指针跟随胶囊指示器），移除 haze 依赖；玻璃采样统一走兄弟录制层，规避 HyperOS hwui 自采样崩溃
- 新增壁纸背景特效：「应用背景」可选关闭 / 动态渐变 / 壁纸图片（含壁纸来源与模糊强度），首页、对话框与输入框组件同步玻璃化
- 新增 About 页 Hero 布局与 FlyCat 矢量图标（含亮色图标资源）

### 规则页与界面交互

- 修复 Root TUN / eBPF 模式下规则页空白：规则查询改经 root 守护进程（新增 queryRulesJson / setRuleDisabled 跨进程接口），不再依赖应用进程内的空内核；搜索框改为常驻内联可编辑，移除点击弹出的整页搜索
- 代理页新增「标签页模式」（ProxyGroupTabs + HorizontalPager，与钉取式互斥）；通知栏节点面板并入 AppActionBottomSheet 弹层宿主，显示设置经 ProxyDisplaySettingsStore 跨界面双向同步
- 新增通知内容设置页（通知两行内容自定义与预览）；网络设置内核管理卡片化（下拉切换已安装内核、检查更新、自定义导入与重启提示）；访问控制新增级联操作菜单与导入策略（合并 / 覆盖）

### 连接与 JNI 性能

- Root 链路 connections / overview / rules / providers 原样透传原生 JSON，省去服务端「解码成对象再重编码」的三重往返；代理组与日志改分隔符化传输，消除逐行 JSON 编解码
- 新增按 id 查询连接（Go QueryConnectionsByIDs + Rust JNI 绑定），连接页增量轮询仅水合新连接，旧内核自动回退全量快照

### 服务与可靠性

- 更新体系改为前台每小时自动检查 GitHub Release + 全局「发现新版本」弹窗 + 会话级「稍后」抑制
- 备份 Merge 策略：写前快照、目录只补缺失、列表本机优先归并，回滚 zip 尽力回滚；崩溃落盘升级（tombstone / ANR trace 抽取可读字符串行并按 reason 分类命名）
- Tun 模式内置 tun 强制 auto-detect-interface，防止核心出站流量回环进 tun；访问控制 UID 解析按模式裁剪，通知取消路径改 NonCancellable 释放 XMSF 防火墙 bypass

### 变更

- 最低系统要求提升：minSdk 26 → 33（Android 8.0 → Android 13），液态玻璃依赖 RuntimeShader / RenderEffect 能力
- 构建链升级：Gradle 9.8.2、AGP 9.6.0-alpha02、Go 1.27、privKit 0.17.9、versionCatalogUpdate 1.1.2；开源许可页同名库改分组展开
- 校验新增注释与实现的一致性，英文注释统一译为中文

[下载链接](https://github.com/LM-Firefly/FlyCat/releases/tag/v0.6.0)

## v0.5.9 · 日志中心、特权服务与打包管线升级 {#v0-5-9}

*2026-10-25 · 功能更新 / 性能优化 / 问题修复 / 变更*

### 日志中心

- 拆分 mihomo 日志 / App 日志双入口，新增 mihomo 实时日志预览：级别筛选、搜索、导出，可见时才订阅
- App 日志分区展示应用日志与崩溃日志，右上角快切日志级别；Go 端取消内核日志级别过滤，改客户端实时筛选

### 特权服务

- 接入 priv-kit 特权服务：Root/无线ADB/静态端口/手动/Shizuku 四通道启动，XMSF 防火墙绕过改走 priv-kit
- 新增特权服务设置页（状态卡+四 Tab+配对弹窗+启动日志），配对支持通知栏内嵌输码；Shizuku 降级为外部启动通道

### 代理页与界面

- 策略组图标不显示修复（Close #208）：恢复单栏/双栏卡片图标渲染，渲染守卫仅加载成功才显示，图片请求设显式尺寸，根治图标永不加载死锁
- 策略组图标与节点国旗全局拼接显示（面包屑/链路/卡片）；国旗改用 Noto Emoji region-flags 真实比例矩形图标
- 连接页/流量统计页表头固定出列表，随 TopBar 折叠收缩

### 性能优化

- 延迟结果改 per-node 流发布：测速只更新受影响徽标不再替换整列表，查询与发布原子化消除旧快照覆盖
- 高频载荷（日志行、连接事件、代理组、单点延迟）改分隔符/裸 int 编码，免去数百 KB JSON 双侧重序列化；root 模式日志改按需订阅；测速热路径去冗余

### 设置与内核

- 自定义内核导入改底部面板，内核链接/本地插件文件双模式安装
- MIUI/HyperOS 开机自启适配：开启自动启动时引导授予小米私有自启动权限（AppOps 10008）
- eBPF 统计回调补丁重定基至 liuran001 02581ff38，消费端契约不变

### 变更

- 本地化实现重构：移除 fytxt 代码生成插件，多语言改为直接提交的 Kotlin + Lyricist（KSP）实现，翻译源迁移至 `locale/src`
- 打包管线：MMKV 按 ABI 分线，universal32/universal64 双分支取代 4 ABI 总包，发布前产物自检并写 .apk.json 身份 sidecar；非引导 so 原地 XZ 压缩，删除 :pack 模块；APK 命名删除 geo 段

[下载链接](https://github.com/LM-Firefly/FlyCat/releases/tag/v0.5.9)

## v0.5.8 · 节点搜索、切节点防卡死与 emoji 对齐 {#v0-5-8}

*2026-10-09 · 功能更新 / 问题修复 / 变更*

### 代理页体验

- 顶栏放大镜唤出节点搜索框：输入即筛、滚动自动收起；配套与其它顶栏图标同规格的线条放大镜
- emoji 混排对齐修复：面包屑、代理链路、节点卡片 emoji 与正文墨迹中心共线，不再下沿越界
- 面包屑按压反馈补全：按下瞬时缩放与降透明、松手弹簧回弹，短按也清晰可见
- 双栏/平板：左栏「代理」点击滚回策略组列表顶，右栏组名标题点击滚回节点列表顶

### 切节点防卡死

- 长时间/高网速后点击节点无响应、无提示的根因修复：原生 `release_object` 改非阻塞入队，关连接移到后台，连接关闭事件跨 JNI 前合并限流
- 可遗弃超时让阻塞 JNI 调用的超时真正生效，超时后重建控制链并重试，仍阻塞则明确提示「切换超时」
- 刷新查询锁改外层释放，消除「query already in flight」永久跳过刷新

### 其它

- 自定义路由：手动 YAML 编辑优先于预设改动，区域与条目文案本地化
- 移除简洁显示模式，统一为详细布局，旧偏好与备份自动归一化
- 移除 gropify 构建插件及其 Jackson 3 传递依赖，根治相关 Dependabot 告警

[下载链接](https://github.com/LM-Firefly/FlyCat/releases/tag/v0.5.8)

## v0.5.7 · 代理稳定性、功耗优化与超级岛通知 {#v0-5-7}

*2026-09-27 · 功能更新 / 性能优化 / 问题修复*

### 代理体验与稳定性

- 修复代理页长时间后台返回假死、配置文件切换后节点数据过期：查询加超时防死锁、前台恢复强刷、事件前重置连接
- 下拉刷新触发延迟测试，逐节点测速进度展示（并发限流、完成即上报）
- 代理页大标题改为面包屑（代理 > 组名），列表行错位揭示动画；弹窗关闭改为动画结束后收尾
- 内核未运行时切节点明确提示并拦截；强制选中前对未测速节点先测活，防止高亮跳回
- 新增应用图标缓存，修复列表滚动图标概率丢失；节点搜索栏固定于列表外，修复定位偏移

### 功耗优化

- JNI 查询移出互斥锁，防并发查询死锁；配置编译单飞互斥与空结果负缓存，防并发编译堆积
- 灭屏完全挂起 UI 轮询，App 后台停止分组同步；自动更新检查仅前台执行
- Go 运行时 GC 调优（GOGC=100 + GOMEMLIMIT 512MiB），连接事件瘦身，会话切换清理陈旧流量

### 运行时可靠性

- 新增 RuntimeRecoveryWorker 周期自愈；前台服务启动竞态修复（startForeground 前移、最新请求收尾）
- PayloadInstaller 改用 PathClassLoader，修复主题/字体变更触发的进程崩溃
- 移除 PROCESS_OUTGOING_CALLS 权限，暗码仅保留 SECRET_CODE 路径；恢复失败降级 WorkManager 重试
- 遥控器接管探测后拒绝启动并 stopSelf；RemoteSwitch 统一探测/挂起/回退，连续 3 次失败才回退本地

### HyperOS 超级岛

- 新增超级岛通知（当前节点、实时流量、订阅用量）与 Shizuku 授权支持
- XMSF bypass 改会话式持有引用计数，修复首帧展开与遥控器接管竞态

### TUN / eBPF

- eBPF TUN 模式修复：始终注入 eBPF listener、UID 访问控制策略、动态获取运行模式
- 新增 Mips TUN 协议栈；Root TUN 启动重试改为快慢两阶段

### 配置与自动化

- 新增配置自动更新间隔（最小 30 分钟）；修复添加本地配置文件崩溃
- 新增 Wi-Fi 自动化配置文件切换
- 覆写保存逻辑简化，空链路改为通知 runtime 清除

[下载链接](https://github.com/LM-Firefly/FlyCat/releases/tag/v0.5.7)

## v0.5.6 · FlyCat 独立维护初始化 {#v0-5-6}

*2026-09-09 · 里程碑 / 功能更新*

### FlyCat 正式作为独立项目维护

与上游 YumeBox 仓库解除 fork 关系，合并全部历史 commit 为单个初始化提交。

#### 架构重构

- 全局重命名 `dev.yume` → `dev.flycat`
- 重构为模块化架构：`:core` / `:ui` / `:data` / `:runtime:{api,client,service}` / `:feature:{about,dashboard,editor,home,log,override,profiles,proxy,settings,substore}`
- `:core` 引入契约接口层；模型按领域重组
- `:data` 扁平化：移除中间层，重组为 store/repository/executor/controller
- `:runtime:client` / `:runtime:service` 重写

#### 原生层

- 移除 C++ JNI 层，Rust JNI 大幅扩展
- Go native 重构：新增 tunnel/config/app/tun/proxy 子模块
- Rust JS 引擎与 compiler 模块重组

#### 新增功能

- Moe 首页（布局/骨架屏/壁纸/拓扑图/速度图/流量展示）
- 连接管理页面（详情面板/速度显示/一键关闭）
- 流量统计（分时段柱状图/分应用排序）
- WebDAV 备份恢复 / WiFi 自动化 / Age 加密
- GitHubUpdateManager 自动更新
- RootTun 服务架构
- 日语/俄语/繁体中文 locale 支持

#### 网站

- 深链 scheme 更正为 `flycat://`
- 模块依赖图按实现重写
- Mermaid 暗色模式适配
- SPA 导航修复（GitHub Pages 子路径部署）
- 应用指南设置项与实现对齐

[下载链接](https://github.com/LM-Firefly/FlyCat/releases/tag/v0.5.6)
