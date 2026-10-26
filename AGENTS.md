# AGENTS.md — ABDL Space V4 工作规则

给在本仓工作的代理/协作者。违反下列纪律的历史教训都真实发生过。

## 分支与推送（严格）

- `develop` = 开发主线；`main` = 上线分支。功能开发用 `feat/*`，完成后合回 `develop`。
- **仅当用户明确指示**才做 develop→main 发版。
- 每轮完整操作（构建/测试通过）后提交并推送；推送前 `git status` 确认无意外文件。
- 本仓与旧仓 `ABDL-Space-APP*`、后端 `abdl-space*`、两站网页仓完全独立：**旧仓一律只读**，后端与网页端**零改动**。

## 构建与测试

- 一切构建走 `./gradlew`（wrapper 9.6.1）。AGP 9.4.1 内置 Kotlin：**不要**给模块添加 `org.jetbrains.kotlin.android` 插件，Kotlin 配置写在模块 `kotlin { compilerOptions { } }`。
- 提交前最低门禁：`./gradlew test`（全部模块）+ 相关 `assembleDebug`/`assembleRelease`。
- 测试纪律（继承 android-test-rewrite.md 反模式清单）：不造假 fixture、不反射改私有状态、不写永真测试；交付承诺只能基于真实跑过的测试。
- 测试包 = 正式签名 release（同线上签名，可覆盖安装）；签名密码在 `local.properties`（已 gitignore）或环境变量中，私钥只从仓库外读取。任何 `*.keystore`/`*.jks`、访问令牌及构建证据中的凭据都不得入库；不要因为旧仓曾提交私钥而沿用该做法。

## 设计与实现基准

- UI 规格：`docs/v4-ui-spec.md`；tokens：`docs/v4-design-tokens.md`（颜色/几何/字体必须走 tokens，禁止散落魔法值；改动先改文档再改代码）。
- 设计系统入口：`core/designsystem`（V4Theme/V4ColorTokens/V4Dimens/V4Text/V4 组件）。新组件先放这里，被≥2 个功能域使用后才算稳定。
- 液态玻璃：API 33+ 启用（miuix-blur/AGSL），低版本走纯色降级；降级路径必须实际构建验证，不允许只写不跑。

## 架构基线

- 纯 Kotlin + Compose；禁新增 XML 布局与 View 体系 UI（地图 SDK 的 View 容器是唯一例外，必须 `AndroidView` 包裹）。
- 网络：一切请求收编 `core/network`（Retrofit + kotlinx.serialization）；禁止在功能代码里裸 new OkHttp/硬编码域名（用 `ApiConfig`）。
- ID：wire 模型使用不透明字符串或 `TypedId` 包装，禁止 long/Double 强转、猜前缀和自造零值；不同功能域的已核实格式只在各自适配边界处理。
- 后端协议契约（分页、NBW receipt、能力三态、401 三分）平移时以旧仓实现 + `nbw-v2` 文档为准，不臆造。

## 已知边界（勿踩）

- 旧 App 3.2.0 的本地数据不迁移不读取；聊天历史靠服务端 sync。
- KV/D1 迁移、生产后端操作不属于本仓职责。
- 上游许可：reader-core（GPL-3.0）、liquid 栈（Kyant0/AndroidLiquidGlass Apache-2.0）、itshover（Apache-2.0）——保留 NOTICE 与许可头。
