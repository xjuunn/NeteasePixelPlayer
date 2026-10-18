# 网易云接入说明

本文件定义网易云功能范围、接口映射、音源优先级与数据映射。接口的可用状态与响应示例以 `docs/external-apis.md` 为准。

## 1. 功能范围

必须支持：搜索、歌单列表、歌曲列表、播放、下载、歌词。
可选扩展：歌手详情/歌曲、分类热门歌单、热门作者、播客（电台）详情与节目。

## 2. 接口映射

路径与状态来自 `docs/external-apis.md`。封装位置遵循 `data/network/netease/NeteaseApiService.kt` 与 `data/netease/NeteaseRepository.kt`。

| 能力 | 接口 | 状态 | 说明 |
| --- | --- | --- | --- |
| 综合搜索 | A5 `/cloudsearch` | 可用 | type：1 歌曲、100 作者、1000 歌单、1002 用户、1009 播客 |
| 歌单详情 | A1 `/playlist/detail` | 可用 | 名称、创建者、曲目数 |
| 歌单曲目 | A2 `/playlist/track/all` | 可用 | 分页 `limit`/`offset`，返回数小于 `limit` 视为末页 |
| 用户歌单 | A3 `/user/playlist` | 可用 | 读 `more` 判断有无下一页 |
| 歌曲详情 | A4 `/song/detail` | 可用 | `ids` 支持逗号分隔；字段可能为 `null`，需容错 |
| 作者详情 | A6 `/artists` | 可用 | 可选扩展 |
| 作者歌曲 | A7 `/artist/songs` | 可用 | `limit=1` 读 `total` |
| 作者热门 | A8 `/artist/top/song` | 可用 | 可选扩展 |
| 热门作者 | A9 `/top/artists` | 可用 | 可选扩展 |
| 歌单分类 | A10 `/playlist/hot` | 可用 | 分类标签 |
| 分类热门歌单 | A11 `/top/playlist` | 可用 | `order=hot` |
| 播客详情 | A12 `/dj/detail` | 可用 | 数据在 `data` 节点 |
| 播客节目 | A13 `/dj/program` | 可用 | 读 `programs[].mainSong.id` 用于播放 |
| 节目详情 | A14 `/dj/program/detail` | 可用 | 可选扩展 |
| 热门播客 | A15 `/dj/hot` | 可用 | 可选扩展 |
| 播客分类 | A16 `/dj/category/recommend` | 可用 | 可选扩展 |
| 单曲直链 | A17 `/song/url/v1` | 受限 | 仅作保底，必须判空 `data[0].url` |
| 歌词回退 | A18 `/lyric` | 可用 | 读 `lrc.lyric`，`code` 必须为 200 |

第三方与素材接口：

| 能力 | 接口 | 状态 |
| --- | --- | --- |
| 音频流 | B1 Qijieya `type=url` | 可用，支持 VIP |
| 歌词 | B2 Qijieya `type=lrc` | 可用，纯文本 |
| 音频直链 | B3 Byfuns `level` | 可用，支持 VIP |
| 音频直链 | B4 VKEYS | 不可用，禁止封装 |
| 封面/音频流 | C1、C2 | 可用 |

## 3. 不可用与受限处理

- B4 VKEYS：业务层恒返回 `code=500`，直接跳过，不封装。
- A17 Mycelis：对版权/VIP 歌曲返回 `code=404`、`url=null`。当前不接入直链链路（类保留，仅歌词仍用 A18）。
- 官方直链试听片段：官方接口对无完整播放权限的歌曲（VIP/付费专辑）会返回可播放但仅前 30 秒的直链，并带 `freeTrialInfo` 或 `freeTrialPrivilege`（`resConsumable`/`cannotListenReason`）。命中即视为失败并降级到第三方，避免 VIP 歌曲被截断。默认档位为 `exhigh`。权限属于歌曲而非音质档位，因此命中后立即结束档位循环，并记一条负缓存，避免每次播放都重复整轮请求。
- A4 歌曲详情：部分字段可能为 `null`，读取时返回默认值，不抛异常。

## 4. 音源优先级与重试

直链解析按以下顺序，高优先级失败才降级：

1. 直连官方接口（含登录态）。
2. B3 Byfuns。
3. B1 Qijieya。
4. 官方直链兜底：以上全部失败时，直接采用官方接口返回的链接（含试听片段），与本应用原始行为一致——宁可播 30 秒也不直接失败。

实现位置：`data/netease/url/`。`SongUrlResolver.resolve` 按 `SongUrlProvider.priority` 升序调用，全部失败后才走兜底；`NeteaseRepository.getSongUrl` 在其外层做 in-flight 去重与失败冷却。

规则：

- 重试次数按音源单独设定：官方 `maxAttempts=1`（一次 resolve 已遍历音质档位），Byfuns `1`，Qijieya `2`；重试间隔为线性退避加抖动。全部失败才进入下一音源。
- 候选直链必须通过主机名白名单（`NETEASE_STREAM_HOST_SUFFIXES`）校验，白名单与 `NeteaseStreamProxy` 共用一份：代理会拒绝的链接如果被当作成功返回，播放器会在 15 分钟缓存内反复拿到同一个 502。
- 被代理拒绝、上游 403/404/416 或响应非音频的链接立即从缓存剔除，下次重新解析。
- 官方音源带歌曲级负缓存（默认 10 分钟）与试听链接缓存（默认 3 分钟），登录或登出时一并清空。
- 每个音源单独适配响应形态，不得共用解析代码。
- 同一歌曲做 in-flight 去重，避免重复与并发风暴（细节见 docs/playback-and-streaming.md）。
- `NeteaseRepository.getSongUrl` 只缓存成功结果 10 分钟；冷却只作用于连续失败，成功结果不再被冷却拦截。

## 5. 各音源响应适配

| 音源 | 返回形态 | 适配要点 |
| --- | --- | --- |
| 官方直连 | JSON | 读直链字段；`code=301` 时先补会话再重试一次；命中试听即停止档位循环并记负缓存 |
| B3 Byfuns | 纯文本 | 响应体即直链，需以 `http` 开头，否则视为无效；不使用 `lossless`（返回 FLAC） |
| B1 Qijieya | 音频流 | URL 本身即直链；探测响应头 `Content-Type` 以 `audio/` 开头即视为可用 |
| A17 Mycelis | JSON | 直链链路未接入，保留实现备查（见第 4 节）；`data[0].url` 非 `http(s)` 视为无效 |
| B2 Qijieya 歌词 | 纯文本 LRC | 首选，直接解析，无需 JSON |
| A18 Mycelis 歌词 | JSON | 回退，`code=200` 时取 `lrc.lyric` |

歌词策略：官方 EAPI 优先，失败回退 B2 Qijieya 纯文本，再回退 A18 Mycelis。实现位置：`data/netease/lyric/`（`SongLyricResolver` 按优先级调用各 `SongLyricProvider`），由 `NeteaseRepository.getLyrics` 统一暴露。

## 6. 数据映射

统一映射到 `data/model/Song.kt` 与 `data/database/NeteaseSongEntity.kt`。字段读取需容忍缺失与类型不符。

| 目标 | 来源字段 |
| --- | --- |
| 歌曲 ID | `id` |
| 歌曲名 | `name` |
| 歌手名/ID | `ar[0].name`/`ar[0].id`，回退 `artists[0]` |
| 封面 | `al.picUrl`，回退 `album.picUrl` |
| 时长 | `dt`（毫秒，long） |
| 专辑名 | `al.name` |
| 播放标识 | `contentUriString = "netease://<id>"`、`neteaseId` |

分页约定：`limit`/`offset` 递增，返回数量小于 `limit` 视为末页。歌单/节目分页同理。

## 7. 同步与登录

- 登录：`NeteaseRepository` 使用 `EncryptedSharedPreferences("netease_prefs")`，以 `MUSIC_U` 判定登录态。
- 同步：复用 `syncUserPlaylists()`、`syncPlaylistSongs()`、`syncAllPlaylistsAndSongs()`，同步后写入统一 `songs` 表（`MusicDao.incrementalSyncMusicData`），并镜像歌单到应用歌单。
- 不在同步流程中引入长耗时同步网络调用；分批与节流。

### 7.1 删除类写入的防护契约

本地缓存（`netease_playlists`、`netease_songs`）与镜像到音乐库的应用歌单都以同步结果为准写入，因此“拿不到数据”必须与“服务器上确实没有数据”严格区分，否则一次网络抖动就会永久删除已导入的音乐。与 `JellyfinRepository`、`NavidromeRepository` 的既有约定一致：

1. `syncUserPlaylists()` 只有在远端快照完整时才清理陈旧歌单。空响应（本地仍有歌单）直接返回失败、不做任何删除；命中分页上限、翻页无新增条目、或返回数量小于服务端 `count` 时视为不完整快照，跳过清理。
2. `syncPlaylistSongs()` 只有在服务端明确报告歌单为空（`trackCount == 0`）时才清空本地歌曲。歌单声明有歌曲但一条都没解析到时返回失败，保留本地歌曲与音乐库歌单。
3. 上层调用必须把 `Result.failure` 当作“保持现状”，不得据此清空统一库（见 `syncUnifiedLibrarySongsFromNetease`）。

修改同步逻辑时不得放宽以上三条；如需新增删除类写入，必须先补齐同等防护与日志。

## 8. 下载

- 下载复用第 4 节解析出的直链与共享 OkHttp，不得为下载单独实现一套音源解析。
- 下载前校验 URL 与内容类型；超过大小上限拒绝。
- 保存到媒体目录并写入统一表，复用现有文件/媒体工具，不新造并行存储逻辑。
- 失败按第 4 节重试，并向上层反馈进度与错误。
