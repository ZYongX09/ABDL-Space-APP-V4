# V4 文字发布、消息只读与 NBW 能力阶段

日期：2026-10-27。接续基础阶段 `a5c0cae`；本轮开发分支 `feat/v4-publishing-messages-nbw`。本文只记录本轮已经实现的功能，不把功能入口或模型存在等同于全迁移完成。

## 已接通范围

### 文字发布

- 编辑器调用真实的 `POST /api/v1/statuses`，使用显式 Bearer、Idempotency-Key 和类型化请求；当前仅支持公开文字帖，不提供假的媒体上传或可见范围选择器。公开帖子是否由后端建立 NBW 同步意图取决于现行服务端合作策略；客户端尚未实现 NBW 分类不能被解释为已强制“仅本站”发布。
- 同一账号代际、同一 payload 的进程内重试复用 key；payload 改变、确认成功或切换账号后不会继承旧 key。
- 失败保留编辑文字；超时、连接中断、5xx 或无法解码的成功响应明确标为结果未知，不能声称未发送，更不能伪造成功。key 与草稿目前不是持久化 outbox，杀进程后须先核实远端结果再重试。
- 确认成功才产生 `publishedId` 完成事件；主壳消费该事件后清草稿、关闭编辑器并返回首页。首页可稍后关闭的提示与该事件独立，不会在恢复时误清新草稿。
- 发布中禁止重复提交、修改和返回退出；注销、切账号后拒绝旧结果。应用入口再次按完整会话 lease 过滤 UI 状态，避免异步清理前短暂显示旧账号事件。

### 通知与私信会话列表

- 通知使用会话域的 V1 `/api/v1/notifications`；支持 `max_id`、`types[]`、`exclude_types[]` 契约，UI 本轮不提供过滤器。
- 分页仅解析同源同路径 Link 的不透明游标，不跟随 Link URL 携带凭据。追加或刷新失败保留旧行与游标；HTTP 200 但游标无效也不是成功页。
- 未知通知类型保留原始字符串并降级显示，缺少必要字段的单项按已核实规则剔除；损坏的响应结构仍报协议错误。通知摘要遇到 sensitive、mental_crisis 或 CW 标记只显示“敏感内容已折叠”，不泄漏正文；该行为有 Compose 回归覆盖。
- 私信使用现有自研 `/api/messages/conversations`，不是 Mastodon conversations 接口。显示真实摘要、时间与未读值；头像采用本地占位，不自动加载远程图片。
- 首次进入已登录消息页时读取；后续手动刷新，无轮询、已读写入或推送注册。会话详情目前是明确标注的迁移说明页，不可发送消息。
- MainActivity 已装配 `V4MessagesViewModel` 的状态和回调；访客零请求，切账号后清缓存并丢弃旧回包，状态绑定 lease 后才交给页面。

### NBW 能力与分类目录

- 全局注入的仓库观察会话，已登录时预取 capabilities；入口按 TTL 复核，5 分钟内不重复刷新。
- 本地模式、只读、完整能力三态使用现有 `/api/app` 协议；任一能力或目录验证失败均按 local_only 处理，不保留可误用的部分权限。
- 完整能力下展示真实板块目录与强制分类标志；目录快照不是发布权限证明。
- capability 加载、目录与 consent 各自使用受控单飞；generation 与 job 身份防止取消旧请求时清理新任务，也防止同 lease 目录清理后被旧请求回填。使用单调时钟，覆盖零时钟与精确 TTL 边界。
- fallback-consent 只有仓库方法与离线测试，没有界面写入口。NBW 内容时间线、OAuth 连接、发帖分类/跨站 receipt 尚未接通。

## 验证

本轮最终门禁 `phase45-cw-final-gate`：**209 项 Debug 单元及主机侧 Compose 测试全部通过，无失败、错误或跳过。**

| 模块 | 测试数 |
|---|---:|
| app | 51 |
| core/data | 69 |
| core/network | 61 |
| core/model | 6 |
| core/designsystem | 22 |

同轮已构建 `assembleDebug`、`assembleDebugAndroidTest` 和正式签名 `assembleRelease`。Instrumentation APK 仅构建，未在设备执行。最终再次执行 `test` 与上述三项构建，全部成功；当前 AGP 的 `test` 聚合只调度 Debug 主机侧测试，没有单独执行 Release 单元变体，不能把 Release 构建、缓存命中或重复执行计为另一套新增测试。

测试使用真实本地 HTTP/TLS MockWebServer 和生产类型化解析，外加明确标注的离线 UI 数据。覆盖幂等 key、未知发布结果、取消、lease 隔离、首次注入与 collector 顺序、通知游标失败重试、NBW 并发/TTL/迟到清理，以及发送中返回和旧提示不清新草稿。

正式测试包元数据：`top.abdl_space.app`、4.0.0/code35、minSdk 26、targetSdk 35、compileSdk 37。APK 签名证书 SHA-256：

`fd2098a3d3493222c247a7ba2787ec48fd8f62d38e9432ed4d6290a057dea475`

已通过 apksigner 与 16K zipalign 校验；APK 的完整 SHA-256、大小、源码提交及测试汇总在仓库外的本轮 evidence 记录中。

## CI 与发布边界

- 新增 GitHub Actions：推送/PR 到 develop 或 main 时运行 Debug 测试、开发及 instrumentation APK 构建、上传测试报告。权限仅 contents:read，不持久保存 checkout 凭据；无正式 keystore、无签名 secret。
- Gradle wrapper 增加发行 ZIP SHA-256 校验，并通过 Gradle setup action 校验 wrapper。
- 本地 CI 同等命令已通过不代表远端 CI 跑绿。GitHub CLI 当前未认证，因此本轮不读取或宣称 GitHub Actions 结果；正常 Git 推送与远端 CI 验证分开报告。
- 只合回 develop，不创建正式 Release、不修改 main、后端、网页或下载入口。

## 未完成与运行证据限制

- 最后核验设备当前为 3.2.0/code34，launcher 为旧仓 SplashActivity；V4 MainActivity 不存在。未再次覆盖日常旧版，不能引用旧版画面作为 V4 截图。
- 之前安装/启动命令成功或没有 FATAL 日志，不等于 V4 已在前台运行。当前没有 V4 真机四页、真实 GPU 玻璃或登录后业务闭环通过证据。
- 本轮未使用真实账号发送生产帖子、消息或 consent。真实发帖、通知、NBW 能力仍须在专门测试账号/设备完成最终验收。
- 媒体上传/查看、投票、定时、回复引用与高级发帖、IM 详情/发送/同步/推送、地图、扩列卡、相册、认证、赞助、纸尿裤及小说全量迁移仍未完成。
- R8、完整 lint、Macrobenchmark/Perfetto、真实 TalkBack 与性能对比不在本轮完成范围；不能声称 V4 已比旧版或竞品更流畅。
