# 播放与串流

本文件定义云端歌曲从模型到出声的链路、流代理约定、重试与容错，以及播放优化要求。

## 1. 播放链路

```
Song.contentUriString ("netease://<id>")
  -> utils/MediaItemBuilder.kt        构建 MediaItem（scheme 原样透传）
  -> data/service/player/DualPlayerEngine.kt  resolveCloudUri() 按 scheme 分发
  -> data/netease/NeteaseStreamProxy.kt      ensureReady() 后解析
  -> data/netease/NeteaseRepository.kt       getSongUrl() 解析真实直链
  -> http://127.0.0.1:<port>/netease/<id>    本地代理地址交给播放器
```

- `DualPlayerEngine.resolveCloudUri` 按 `uri.scheme` 分发：`netease`、`qqmusic`、`navidrome`、`jellyfin`、`telegram`、`gdrive`。新增来源必须在此登记。
- 解析结果缓存于 `resolvedUriCache`。解析失败时返回原 URI，不得抛异常中断播放。
- 旁路消费者：`data/service/wear/PhoneDirectWatchTransferCoordinator.kt` 有同样的 scheme 分发，改动时需同步。

## 2. 流代理约定

`data/stream/CloudStreamProxy.kt` 是共享基类，子类声明：

- `uriScheme`、`routePath`、`routeParamName`、`routePrefix`：本地路由与 scheme。
- `allowedHostSuffixes`：上游直链的宿主白名单。
- `cacheExpirationMs`：直链缓存时长。
- `parseRouteParam`、`validateId`、`formatIdForUrl`、`resolveStreamUrl`。

基类行为：在 `127.0.0.1` 随机端口启动 Ktor CIO 服务，校验 Range 与上游安全，按 64KB 转发，返回 `200/206` 与 `Accept-Ranges`。

安全校验统一在 `data/stream/CloudStreamSecurity.kt`：scheme 对应 ID 校验、Range 校验、内容类型白名单、大小上限（2GB）、上游宿主白名单。新增来源必须在此添加校验函数与宿主。

## 3. 重试与容错

- 音源级重试：单音源最多重试 3 次，指数退避加抖动；失败降级到下一音源（顺序见 docs/netease-integration.md）。
- `mycelis.dpdns` 作为最后手段，仅在其它音源全部失败后请求，控制调用频率。
- 请求去重：同一歌曲的并发解析合并为一次（in-flight `CompletableDeferred` 映射）。
- 节流：单曲冷却加全局最小间隔（互斥锁），避免触发上游限流。
- 音质回退：按档位链回退（如 `lossless -> exhigh -> higher -> standard`）。
- 上游错误：`301` 等会话错误先补会话再重试一次；无效直链（空、非 `http`）视为失败。
- 全部失败：向上层返回可识别错误，播放器自动跳过或提示重试；写 `Timber` 日志，不打印敏感 Cookie。

## 4. 缓存与预取

- 直链按其有效期缓存（`cacheExpirationMs`），避免重复解析。
- 支持在切换歌曲前预热下一首（参考 `QqMusicStreamProxy` 的预热方法），减少起播等待。
- 起播前调用 `ensureReady(timeout)` 确保代理就绪，避免首帧失败。
- 封面走 Coil 与现有图片缓存，不新增并行图片缓存。

## 5. 播放优化要求

- 不在主线程做网络与 JSON 解析，统一在 IO 调度器执行。
- 首帧优先：先可用低音质起播，再按需升级，避免长时间缓冲。
- 切歌/循环/随机播放下的解析结果复用缓存，减少抖动。
- 播放失败要有明确反馈与自动恢复（重试或跳下一首），保证体验顺滑。

## 6. 下载

- 复用第 3 节解析出的直链与共享 OkHttp 下载，不重复实现解析。
- 下载前校验内容类型与大小，超限拒绝。
- 保存到媒体目录并写入统一表，复用现有文件与媒体工具。
- 下载失败按重试策略处理，并向 UI 反馈进度与错误。

## 7. 兼容性

- 不得更改 `DualPlayerEngine`、`MediaItemBuilder`、`CloudStreamProxy` 的公共契约；确需变更必须向后兼容并说明。
- 新来源若无法继承 `CloudStreamProxy`，需复刻其安全校验与 Range 语义。
