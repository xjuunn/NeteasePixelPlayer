# AGENTS.md

本仓库是 PixelPlayer 的二次开发分支。核心目标：在不破坏现有本地音乐能力的前提下，完整接入网易云音乐（搜索、歌单、歌曲列表、播放、下载、歌词），并保持与现有架构和 UI 风格一致。

本文件是所有 AI/人类贡献者必须遵守的常驻规则。细节按主题拆分到 `docs/`，本文件只保留入口与硬性约束。

## 1. 文档索引

改动前先读对应文档。`docs/` 新增文件必须登记到下表。

| 文档 | 内容 |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | 目录分层、云音乐源接入模板、DI/Room/命名约定、来源判别 |
| [docs/netease-integration.md](docs/netease-integration.md) | 网易云功能范围、接口映射、音源优先级与重试、数据映射、下载 |
| [docs/playback-and-streaming.md](docs/playback-and-streaming.md) | 播放链路、流代理、缓存、重试与容错、播放优化 |
| [docs/ui-style-guide.md](docs/ui-style-guide.md) | UI/主题/组件复用与样式适配规则 |
| docs/external-apis.md | 外部接口清单与可用状态，音源封装的唯一依据（本地文档，勿提交） |
| [docs/performance-report.md](docs/performance-report.md) | 性能诊断报告格式与导出方式 |

文档维护规则：AGENTS.md 只放必须常驻的规则；当某个主题篇幅变大（清单、字段表、流程图等），必须拆到 `docs/` 并在上表登记，本文件只留一行摘要与链接。禁止把长清单堆进本文件。

## 2. 硬性约束

1. 禁止在任何文档、代码注释、提交信息、UI 文案中使用 emoji。
2. 尽可能保留软件原有功能，一切新能力都在原有功能之上叠加。禁止以重构、替换或简化之名删除、降级或破坏既有能力。
3. 未经确认不得删除或改写用户已有代码、资源与分支；改动前先阅读上下文。
4. 只封装 `docs/external-apis.md` 中标记为可用的接口。不可用接口（如 B4 VKEYS）不得接入；受限接口（如 A17 Mycelis）必须做判空与降级。
5. 保持架构整洁：遵循现有分层与同构模式，不引入与本项目不一致的新框架或新范式。
6. 语言简洁通顺，不啰嗦；规则与文案优先中文。
7. 提交信息使用 `type(scope): 摘要` 格式，一次提交只做一类改动。

## 3. 架构约束

1. 依赖方向固定：`presentation -> viewmodel -> repository -> (network | database)`。UI 不得直接调用 `ApiService` 或 `Dao`。
2. 云音乐源统一采用“实体+Dao / ApiService / Repository / StreamProxy / 展示层”同构模板，模板与路径见 docs/architecture.md。
3. 网易云代码放在既有目录：`data/netease/`、`data/network/netease/`、`data/database/`、`presentation/netease/`。不得新建平行架构。
4. 新表/新字段必须接入 `PixelPlayDatabase` 与 `di/AppModule.kt`，并提供 Room 迁移。
5. 网络请求复用共享 OkHttp（`di/AppModule.kt` 的 `provideOkHttpClient`）与现有超时/重试工具。避免另起客户端。

## 4. 网易云功能范围

必须支持：搜索、歌单列表、歌曲列表、播放、下载、歌词。可基于 `docs/external-apis.md` 扩展歌手、分类歌单、播客等能力。

接口清单、字段映射与状态以 docs/netease-integration.md 与 docs/external-apis.md 为准。

## 5. 音源优先级与重试

1. 直链解析顺序：直连官方接口（含登录态）→ Byfuns（B3）→ Qijieya（B1）；全部失败时回落到官方接口返回的原始链接（含试听片段）。`mycelis.dpdns` 不接入直链链路，仅歌词仍用 A18。
2. 重试次数按音源单独设定（官方 1 次、Byfuns 1 次、Qijieya 2 次）；候选直链必须通过与流代理共用的主机名白名单，代理会拒绝的链接不得进缓存。
3. 必须为每个音源单独适配其响应形态（JSON / 纯文本 / 音频流），不得共用同一套解析逻辑。
4. 解析失败要降级到下一音源，不得崩溃、不得阻塞主线程。
5. 具体重试、去重、缓存与节流策略见 docs/playback-and-streaming.md。

## 6. UI 与样式

以复用现有组件、最小化样式改动为原则：沿用 Material 3 主题与现有列表项、加载、空态、错误组件，不硬编码颜色与尺寸。详细规则见 docs/ui-style-guide.md。

## 7. 播放体验

播放必须顺滑：请求去重、URL 缓存与预取、音质回退、失败自动重试并给出明确提示。完整链路与容错要求见 docs/playback-and-streaming.md。

## 8. 构建与验证

- 编译：`./gradlew :app:assembleDebug`
- 单测：`./gradlew :app:testDebugUnitTest`
- 构建变体：`debug` / `release` / `benchmark`。
- 提交前必须保证编译通过；涉及播放、音源、同步的改动需自测真实请求路径。
- 排查性能问题参考 docs/performance-report.md。
