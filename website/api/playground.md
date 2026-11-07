---
title: "调试台"
description: "基于 Swagger UI 的交互式 API 调试台"
outline: false
---

# 调试台

交互式 API 调试台，数据源为 [`api/openapi.json`](https://github.com/LM-Firefly/FlyCat/blob/Moes/website/api/openapi.json)。接口说明见 [API 概览](./)。

::: warning
接口默认请求本站域名。调试自己的后端时，请把 Server 的 host / port 改为你自己的 external-controller（mihomo 示例配置为 `127.0.0.1:9093`），并在配置了 secret 时在授权栏填入 `Bearer <secret>`。Unix socket / Windows named pipe 访问不校验 secret。
:::

<ApiPlayground />
