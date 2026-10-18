# 架构说明

本文件描述现有分层与云音乐源接入模板。新增网易云代码必须遵循本文件，不得另起架构。

## 1. 模块与目录

Gradle 模块：`:app`、`:shared`、`:wear`、`:baselineprofile`。主开发在 `:app`。

```
app/src/main/java/com/theveloper/pixelplay/
├── data/
│   ├── database/     Room 实体、DAO、迁移
│   ├── model/        领域模型（Song、PlayList、SearchFilterType 等）
│   ├── network/      各来源 API 客户端（netease/、qqmusic/、navidrome/、jellyfin/、deezer/、lyrics/）
│   ├── preferences/  DataStore 偏好
│   ├── repository/   统一仓库（MusicRepository 等）
│   ├── service/      播放服务、HTTP 服务、播放器
│   ├── stream/       云音乐流代理基类与安全校验
│   └── <source>/     单来源仓库与流代理（netease/、qqmusic/、navidrome/、jellyfin/、telegram/、gdrive/）
├── di/               Hilt 模块与 qualifier
├── presentation/
│   ├── components/   通用 Compose 组件
│   ├── navigation/   导航图与路由
│   ├── screens/      通用页面
│   ├── viewmodel/    ViewModel 与状态持有者
│   └── <source>/     单来源页面（dashboard/、auth/）
├── ui/               theme/、glancewidget/
└── utils/            扩展与工具
```

依赖方向固定：`presentation -> viewmodel -> repository -> (network | database)`。UI 不得直接访问 `ApiService`/`Dao`。

## 2. 云音乐源接入模板

六种来源（Netease、QQ Music、Navidrome、Jellyfin、Telegram、GDrive）采用同构模式。新增来源按以下顺序落地：

1. 数据表：`data/database/<Source>SongEntity.kt`、`<Source>PlaylistEntity.kt`、`<Source>Dao.kt`，注册进 `data/database/PixelPlayDatabase.kt`。
2. 网络层：`data/network/<source>/<Source>ApiService.kt`，标注 `@Singleton class ... @Inject constructor(...)`。
3. 仓库层：`data/<source>/<Source>Repository.kt`，`@Singleton`，负责鉴权、同步、直链解析。
4. 流代理：`data/<source>/<Source>StreamProxy.kt`，继承 `data/stream/CloudStreamProxy.kt`。
5. 展示层：`presentation/<source>/dashboard/`（主页 + ViewModel）与 `presentation/<source>/auth/`（登录）。
6. 导航：在 `presentation/navigation/Screen.kt` 增加路由常量，在 `presentation/navigation/AppNavigation.kt` 注册，入口卡片放在 `presentation/screens/AccountsScreen.kt`（回调经 `presentation/viewmodel/AccountsViewModel.kt`）。

网易云对应文件：

| 层 | 文件 |
| --- | --- |
| 网络（官方直连） | `data/network/netease/NeteaseApiService.kt`、`NeteaseEncryption.kt`、`CryptoMode.kt` |
| 网络（公共网关） | `data/network/netease/NeteaseCloudApiService.kt` |
| 仓库 | `data/netease/NeteaseRepository.kt` |
| 音源提供者 | `data/netease/url/`（`SongUrlResolver`、`SongUrlProvider`、`Official/Byfuns/Qijieya/Mycelis` 实现） |
| 歌词提供者 | `data/netease/lyric/`（`SongLyricResolver`、`SongLyricProvider`、`Official/Qijieya/Mycelis` 实现） |
| 流代理 | `data/netease/NeteaseStreamProxy.kt` |
| 数据表 | `data/database/NeteaseSongEntity.kt`、`NeteasePlaylistEntity.kt`、`NeteaseDao.kt` |
| 展示 | `presentation/netease/dashboard/`、`presentation/netease/auth/` |
| 路由 | `Screen.kt` 中 `NeteaseDashboard = "netease_dashboard"` |

## 3. 来源判别与统一表

- `data/model/Song.kt` 用来源字段判别来源：`neteaseId`、`qqMusicMid`、`navidromeId`、`jellyfinId`、`telegramFileId`、`gdriveFileId`，以及 `contentUriString`、`path`。
- 云端歌曲的 `contentUriString` 使用 `scheme://id` 形式，网易云为 `netease://<songId>`。
- 来源枚举 `SourceType`（`data/database/SongEntity.kt`）：`LOCAL=0, TELEGRAM=1, NETEASE=2, GDRIVE=3, QQMUSIC=4, NAVIDROME=5, JELLYFIN=6`。
- 云端歌曲同步进统一 `songs` 表，各来源使用大 ID 偏移避免冲突（网易云见 `NETEASE_SONG_ID_OFFSET`，位于 `NeteaseRepository`）。
- `SongEntity.toSongInternal()` 负责把 scheme 还原为来源字段，新增来源时必须同步扩展。

## 4. 搜索

- 统一搜索入口：`presentation/viewmodel/SearchStateHolder.kt`，调用 `MusicRepositoryImpl.searchAll()`，按 `data/model/SearchFilterType.kt`（`ALL/SONGS/ALBUMS/ARTISTS/PLAYLISTS`）分发。
- 歌曲搜索走统一 `songs` 表的 FTS（`MusicDao` + `SongSearchFtsEntity`）。因此云端内容必须先同步入库，在线搜索才可见。
- 各源另有在线搜索方法（如 `NeteaseRepository.searchOnline`），如需暴露到 UI，通过 repository 层返回统一模型，不要泄漏原始 JSON。

## 5. 依赖注入

- 无 per-source Hilt 模块。DAO 在 `di/AppModule.kt` 提供（如 `provideNeteaseDao`）；Repository/ApiService/StreamProxy 均为 `@Singleton @Inject constructor`，由 Hilt 自动提供。
- 共享网络客户端由 `di/AppModule.kt` 的 `provideOkHttpClient` 提供；`di/Qualifiers.kt` 定义 `@FastOkHttpClient`、`@DeezerRetrofit`、`@BackupGson`、`@AppScope`。
- 新增依赖优先 `@Inject constructor`；确需提供时才改 `di/AppModule.kt`。

## 6. 代码风格

- Kotlin + Coroutines/Flow；Hilt 注入；Room 存储；Compose + Material 3；日志用 `Timber`。
- 命名、包结构、注释风格与相邻文件保持一致；不修改公共组件与公共 API 的既有签名。
- 不使用 emoji（含注释与日志）。
