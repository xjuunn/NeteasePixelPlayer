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
- A17 Mycelis：对版权/VIP 歌曲返回 `code=404`、`url=null`，仅在其余音源全部失败后尝试，且必须判空。
- 官方直链试听片段：官方接口对无完整播放权限的歌曲（VIP/付费专辑）会返回可播放但仅前 30 秒的直链，并带 `freeTrialInfo` 或 `freeTrialPrivilege`（`resConsumable`/`cannotListenReason`）。命中即视为失败并降级到第三方，避免 VIP 歌曲被截断。默认档位为 `exhigh`。
- A4 歌曲详情：部分字段可能为 `null`，读取时返回默认值，不抛异常。

## 4. 音源优先级与重试

直链解析按以下顺序，高优先级失败才降级：

1. 直连官方接口（含登录态）。
2. B3 Byfuns。
3. B1 Qijieya。
4. A17 Mycelis（`mycelis.dpdns`，最后使用）。

实现位置：`data/netease/url/`。`SongUrlResolver.resolve` 按 `SongUrlProvider.priority` 升序调用，`NeteaseRepository.getSongUrl` 在其外层做 in-flight 去重与单曲冷却。

规则：

- 每个音源最多重试 3 次（指数退避 + 抖动），全部失败才进入下一音源。
- `mycelis.dpdns` 只在所有其它音源失败后请求，尽量减少调用。
- 每个音源单独适配响应形态，不得共用解析代码。
- 各音源返回“候选直链列表”，播放器按顺序尝试；先到先得。
- 同一歌曲做 in-flight 去重与冷却节流，避免重复与并发风暴（细节见 docs/playback-and-streaming.md）。

## 5. 各音源响应适配

| 音源 | 返回形态 | 适配要点 |
| --- | --- | --- |
| 官方直连 | JSON | 读直链字段；`code=301` 时先补会话再重试一次；按音质档位回退 |
| B3 Byfuns | 纯文本 | 响应体即直链，需以 `http` 开头，否则视为无效；不使用 `lossless`（返回 FLAC） |
| B1 Qijieya | 音频流 | URL 本身即直链；探测响应头 `Content-Type` 以 `audio/` 开头即视为可用 |
| A17 Mycelis | JSON | 读 `data[0].url`，非 `http(s)` 视为无效；档位 `standard -> higher -> exhigh -> lossless` |
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

## 8. 下载

- 下载复用第 4 节解析出的直链与共享 OkHttp，不得为下载单独实现一套音源解析。
- 下载前校验 URL 与内容类型；超过大小上限拒绝。
- 保存到媒体目录并写入统一表，复用现有文件/媒体工具，不新造并行存储逻辑。
- 失败按第 4 节重试，并向上层反馈进度与错误。
