# V4 基础交付（Phase 0–3）状态

## 状态总览（截至 2026-10-27，feat/v4-foundation，gate-16 全绿）

### 已验证
- Gradle 9.6.1 / AGP 9.4.1 / Kotlin 2.4.21 工具链；模块 :app、:core:common、:core:model、:core:designsystem、:core:network、:core:data；无新增 XML 布局。
- minSdk 26 / targetSdk 35 / compileSdk 37；release 缺正式凭据时 fail-fast，不产出 unsigned 包。
- 签名私钥在仓库外 `~/.config/abdl-space/signing/v4-release.keystore`；仓库内无密钥/密码；allowBackup=false 且备份/数据提取规则全排除。
- **单元门禁 125 项全部通过**（gate-16 JUnit XML）：
  - core/model 6；core/network 52（真实 TLS loopback harness：Auth/Mastodon/Messages/NBW/NetworkCall/Link 分页/传输安全）。
  - core/data 30（AES-GCM 会话加解密、代际 lease 拒迟到回包、登录 verify 流程、退出隔离 quarantine、隔离标记失败后只能完成注销、存储恢复重试、确认重置、时间线分页/去重/offset/退出丢包）。
  - core/designsystem 22（含最差玻璃合成的对比度、policy 纯函数、底栏 Tab/Button 语义与 48dp、API 26/28/33 不透明像素降级）。
  - app 15（Hilt 启动、真实导航与状态恢复、进程重建后首屏重新请求、200% 字号、只读时间线 fixture 含 CW/拖拽滚动/追加失败重试、8 张真实层级 PNG）。
- `:app:assembleRelease` 成功；签名证书 SHA-256 `fd2098a3…` 与 3.2.0(34) 一致；APK 13,311,971 字节。
- UI：四页主壳 + 真实背景玻璃（API 33+）/低版本不透明降级；密码登录、外观偏好持久化、退出/切账号；首页三流只读时间线（真实分页、去重、CW 折叠、加载与重试状态）；无媒体加载、无写操作、无虚构内容。

### 未验证/待办（不得宣称）
- 真机/模拟器运行、真实 GPU 玻璃、Macrobenchmark、TalkBack 全量、上传/推送/IM 真实流程：本机无设备，Robolectric CPU 渲染不能替代。
- 真实账号闭环（登录后真实时间线、媒体、收藏）未测；NBW 能力检测未接。
- main 分支未动；无 CI；R8 未启用；Robolectric 4.14.1 与 Compose v2 测试规则升级待办。

## 关键工程决策
- 网络：真实 loopback + 离线 DNS；origin 锁定、禁重定向、每请求 Authorization；NativeClientIdentity 由 BuildConfig 注入；无全局 token、无日志转储。
- 会话：AndroidKeyStore AES-GCM + noBackup DataStore；lease=(accountId,generation) 拒迟到请求；退出先写 fail-closed 隔离标记，保存失败跨重启仍锁定；仅显式确认 reset 清除 V4 凭据与密钥。
- 时间线：all/home 用 max_id cursor、popular 用 offset；guest 不自动请求；退出后旧响应丢弃。
- 玻璃：miuix-blur 0.9.4 仅 API 33+ 且非低 RAM/未减弱透明；其余不透明降级；降级像素有测试。

## 下一步优先级
1. 有设备时：release 包真实登录 + 时间线/媒体/发帖闭环、GPU 玻璃与性能基准。
2. NBW capabilities/options 接通；IM 同步与发送；通知 + JPush。
3. 相册/认证/赞助/扩列卡/地图/纸尿裤逐域迁移。
4. CI（test+debug assemble+artifact）；macrobenchmark；R8 与 APK 体积预算。
