---
title: "应用使用指南"
sidebarTitle: "应用指南"
description: 首页手势、设置项详解与 flycat:// 深度链接
icon: "smartphone"
---

## 首页手势

默认的 Moe 首页支持两种手势，无需进入设置即可操作壁纸与侧栏：

<CardGroup :cols="2">
  <Card title="单击主图" icon="image">
    单击首页顶部的壁纸图片，会直接打开系统相册选择器，选图后进入裁剪界面调整显示范围，确认即更换首页壁纸。
  </Card>
  <Card title="双击主图" icon="panel-left">
    双击壁纸图片，展开或收起左侧信息侧栏（运行时长 / 时钟、快捷入口等）。
  </Card>
</CardGroup>

因此原先「首页侧栏默认展开」开关与「首页壁纸」设置项已从设置中移除——这两项行为现在完全由首页手势接管。

## 应用设置详解

### 行为

<ParamField path="自动启动" type="boolean" default="关闭">
  应用启动和开机时自动启动代理服务。
</ParamField>

<ParamField path="启动更新配置" type="boolean" default="开启">
  启动时自动更新当前订阅配置。
</ParamField>

### 主题配色

<ParamField path="主题模式" type="enum" default="跟随系统">
  应用的主题样式：跟随系统 / 浅色 / 深色。
</ParamField>

<ParamField path="反转主题前景色" type="boolean" default="关闭">
  当自动推导出的前景文字 / 图标颜色看起来怪异时，开启此项反转前景明暗。
</ParamField>

<ParamField path="主题配色" type="color" default="#138A74">
  选择主题种子色，App 据此自动推导浅色 / 深色两套配色。
</ParamField>

### 界面

<ParamField path="应用语言" type="enum" default="跟随系统">
  应用语言：跟随系统 / 简体中文 / 繁體中文 / English / 日本語 / Русский。
</ParamField>

<ParamField path="应用背景" type="enum" default="动态渐变">
  全局页面背景效果（含二级页）：关闭 / 动态渐变 / 壁纸图片。选择「壁纸图片」后可设置壁纸来源（相册 / URL）并调整壁纸模糊强度（0 – 25）。
</ParamField>

<ParamField path="滑动隐藏底栏" type="boolean" default="开启">
  内容向下滑动时自动隐藏底部浮动导航栏，向上滑动时重新显示。
</ParamField>

<ParamField path="页面缩放" type="number" default="100%">
  调整 App 整体界面缩放比例，范围 80% – 120%。
</ParamField>

### 首页

<ParamField path="经典首页" type="boolean" default="关闭">
  回退到旧版（经典）首页布局，关闭则使用默认 Moe 首页。
</ParamField>

<ParamField path="一言" type="boolean" default="关闭">
  仅在开启「经典首页」时出现：在经典首页顶栏显示一言内容。
</ParamField>

<ParamField path="首页一言" type="string" default="一个人走 默守一隅清欢">
  Moe 首页显示的一言文本，留空时回退内置默认文案。
</ParamField>

<ParamField path="一言作者" type="string" default="Firefly">
  一言落款。
</ParamField>

### 导航

<ParamField path="预测返回手势" type="boolean" default="关闭">
  启用 Android 预测返回手势，下次启动应用后生效。
</ParamField>

<ParamField path="预测返回幅度" type="number" default="50%">
  预测返回动画的跟随幅度，范围 1% – 100%。
</ParamField>

### 隐私

<ParamField path="隐藏应用图标" type="boolean" default="关闭">

  隐藏桌面图标后，可通过拨号盘输入 `*#*#0721#*#*` 重新打开 App。

</ParamField>

<ParamField path="隐藏后台卡片" type="boolean" default="关闭">
  启用后，App 不会出现在系统「最近任务」列表（后台卡片）中。
</ParamField>

### 服务

<ParamField path="显示流量通知" type="boolean" default="开启">
  在通知栏中显示实时流量使用情况。
</ParamField>

<ParamField path="后台隐藏时释放界面" type="boolean" default="关闭">
  当 App 退到后台且界面完全不可见时，主动释放界面以节省内存，同时保留正在运行的代理服务。
</ParamField>

<ParamField path="特权服务" type="页面">
  特权服务（Privileged Server）状态与启动入口：Root / 无线 ADB / 静态端口 / 手动 / Shizuku 四通道启动，含配对弹窗与启动日志。
</ParamField>

<ParamField path="超级岛" type="boolean" default="开启">
  在 HyperOS 超级岛显示当前节点、实时流量与订阅用量。
</ParamField>

<ParamField path="Android 16 实时通知" type="boolean" default="开启">
  在 Android 16 及以上系统将服务通知提升为实时通知（Live Update），置顶显示于通知栏并在状态栏展示实时速率；超级岛开启时自动让位。
</ParamField>

<ParamField path="电池优化无限制" type="系统设置">
  显示当前后台是否受系统电池优化限制；点击跳转系统设置，建议设为「无限制」以降低后台被杀概率。
</ParamField>

<ParamField path="通知内容" type="页面">
  自定义通知栏第一行~第四行显示内容（配置名称、运行状态、当前节点、流量等可选），并支持按当前配置实时预览。
</ParamField>

### 网络

<ParamField path="更新通道" type="enum" default="Stable">
  检查应用更新时使用的 GitHub Release 通道：Stable / Pre-release / Smart。
</ParamField>

<ParamField path="自动检查应用更新" type="boolean" default="关闭">
  开启后，应用在前台时每小时自动检查一次 GitHub Release 更新。
</ParamField>

<ParamField path="内核与订阅 User-Agent" type="string" default="（空）">
  拉取订阅与内核时使用的 User-Agent，留空则使用默认值（ClashMetaForAndroid）。
</ParamField>

## 网络设置详解

### 运行模式

<ParamField path="运行模式" type="enum" default="Vpn Service">
  接管系统流量的方式：Vpn Service（兼容性最好，系统 VPN 全局代理）/ Tun（需要 Root 的虚拟网卡）/ eBPF（需要 Root 的 eBPF 转发代理）。页面会显示当前生效模式。
</ParamField>

### 服务配置（VPN 模式）

<ParamField path="绕过私有网络" type="boolean" default="开启">
  绕过私有网络和本地地址，不经代理。
</ParamField>

<ParamField path="TUN 协议栈" type="enum" default="System">
  VPN 模式使用的 TUN 协议栈：System / GVisor / Mixed / Mips。
</ParamField>

<ParamField path="DNS 劫持" type="boolean" default="开启">
  将所有 DNS 请求重定向到 FlyCat 处理。
</ParamField>

<ParamField path="允许应用绕过" type="boolean" default="开启">
  允许其他应用通过自身设置绕过本 VPN。
</ParamField>

<ParamField path="运行 IPv6" type="boolean" default="关闭">
  允许通过 VPN 路由 IPv6 流量。
</ParamField>

<ParamField path="VPN 系统代理" type="boolean" default="开启">
  仅在 VPN 模式下，为未走 TUN 的应用设置 HTTP 代理。
</ParamField>

<ParamField path="Wi-Fi 自动化" type="页面">
  按 Wi-Fi SSID 自动化：连接指定 Wi-Fi 时启动 / 停止 VPN 或切换到指定配置（仅 VPN 模式下可用，需位置权限）。
</ParamField>

### 服务配置（Root TUN）

<ParamField path="接口名称" type="string" default="FlyCat">
  Root TUN 创建的虚拟网卡名。
</ParamField>

<ParamField path="MTU" type="number" default="9000">
  Root TUN 链路的最大传输单元。
</ParamField>

<ParamField path="自动路由" type="boolean" default="开启">
  自动添加转发所需的路由。
</ParamField>

<ParamField path="严格路由" type="boolean" default="关闭">
  仅允许命中的流量走 Root TUN。
</ParamField>

<ParamField path="自动重定向" type="boolean" default="开启">
  自动启用 Root TUN 所需的重定向规则。
</ParamField>

<ParamField path="DNS 模式" type="enum" default="RedirHost">
  Root TUN 的 DNS 处理方式：RedirHost / FakeIP。
</ParamField>

<ParamField path="FakeIP IPv4 地址段" type="string" default="198.18.0.1/16">
  仅在 FakeIP 模式下生效。
</ParamField>

<ParamField path="FakeIP IPv6 地址段" type="string" default="fc00::/18">
  仅在 FakeIP 模式下生效。
</ParamField>

### 服务配置（eBPF）

<ParamField path="中国 IP 绕行" type="boolean" default="关闭">
  eBPF 监听器旁路中国 IP 规则集。
</ParamField>

### 高级选项

<ParamField path="禁用所有覆写" type="boolean" default="关闭">
  开启后直接运行原配置（含 Tun、eBPF 参数），不再应用任何覆写。
</ParamField>

### 访问控制

<ParamField path="访问控制模式" type="enum" default="允许所有">
  全局访问控制策略：允许所有 / 允许选择 / 拒绝选择。
</ParamField>

<ParamField path="管理访问控制列表" type="操作">
  为应用和域名配置访问控制规则，支持按中国 / 海外应用批量选择与剪贴板导入包名。
</ParamField>

<ParamField path="导入策略" type="enum" default="合并">
  从剪贴板导入包名时对设备上已有选择的处理：合并（保留并补充）/ 覆盖（整体替换）。
</ParamField>

### 内核管理

<ParamField path="当前内核" type="操作">
  下拉切换已安装内核（内置 Alpha / 已下载内核），切换后重启应用生效。
</ParamField>

<ParamField path="检查更新" type="操作">
  检查并下载所选内核通道的可用更新。
</ParamField>

<ParamField path="安装自定义内核" type="操作">
  内核链接 / 本地插件文件双模式导入。插件为 zip，内含 kernel-index.json 与 kernel-{id}.so.xz（仅 HTTPS 链接）。
</ParamField>

## 深度链接 flycat://

可在网页、快捷方式或其他 App 中使用以下链接打开 FlyCat 并定位到指定界面。

### 主页面

| 链接 | 目标 |
| --- | --- |
| `flycat://page/home` | 首页 |
| `flycat://page/proxy` | 代理 |
| `flycat://page/profiles` | 配置 |
| `flycat://page/settings` | 设置 |

### 二级界面

| 链接 | 目标 |
| --- | --- |
| `flycat://screen/appsettings` | 应用设置 |
| `flycat://screen/network` | 网络设置 |
| `flycat://screen/access` | 访问控制 |
| `flycat://screen/override` | 覆写 |
| `flycat://screen/providers` | 提供者 |
| `flycat://screen/connection` | 连接 |
| `flycat://screen/traffic` | 流量统计 |
| `flycat://screen/log` | 日志 |
| `flycat://screen/about` | 关于 |

::: info
未识别的 host / 路径会被忽略，App 正常停留在当前界面。
:::
