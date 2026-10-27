# ABDL Space V4

ABDL Space Android 客户端的独立重构项目，使用 Kotlin 与 Jetpack Compose。设计参考国内成熟社区应用的内容层级、卡片流与悬浮胶囊导航，保留 ABDL 婴儿蓝品牌和局部背景玻璃。V4 不是旧 Moshidon fork 上换皮，也不重建生产后端。

> **开发中，不能替代正式版。** 已进入文字发布、通知/私信会话只读与 NBW 能力接入阶段，不代表现有 App 全功能已迁移。`top.abdl_space.app` 与线上包名相同，正式签名测试包会覆盖旧版；不要把开发测试包装到日常使用设备上。

## 范围

- 主导航：首页、发现、发布动作、消息、我的。发布不是可选中的第五个目的地。
- 继续兼容现有账号及 Mastodon `/api/v1`、NBW `/api/app` 和自定义业务协议；只读核对旧客户端契约，不修改旧仓、后端或网页端。
- 分阶段迁移时间线、媒体发布、IM、地图、扩列卡、纸尿裤、相册、认证、赞助、小说与 NBW。
- 旧版数据库、账号文件和草稿不做原地迁移；复用包名不等同于已完成旧数据升级方案。

## 工程结构

```text
app                    Hilt 应用入口、导航、UI、应用级状态装配
core/common            通用结果与调度器
core/model             类型化模型与兼容协议解码
core/designsystem      颜色、几何、主题、组件、API 33+ 背景玻璃
core/network           Retrofit / OkHttp / 协议与请求安全边界
core/data              加密会话、账号代际隔离、外观偏好、认证仓库
```

版本固定于 `gradle/libs.versions.toml`。AGP 9.4.1 内置 Kotlin、Kotlin Compose 编译插件 2.4.21、Compose BOM 2026.09.00、miuix 0.9.4、Gradle 9.6.1；minSdk 26、compileSdk 37、targetSdk 35。targetSdk 的商店发布要求须在实际发版时重新核对。

## 本地构建

需要 JDK 17 或兼容 JDK、Android SDK 37、已接受的 SDK 许可。创建不入库的 `local.properties`，其中 `sdk.dir` 指向本机 SDK。

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew :app:assembleRelease
```

开发构建可以使用 debug 签名；对外测试交付只使用已有正式签名 release。release 必须设置以下环境变量或本地属性（缺失就失败，不能回退 debug/unsigned）：

- `SIGNING_STORE_FILE`：**仓库外** keystore 的绝对路径。
- `SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`：已有签名凭据，不要放入源码。

本地属性可写在已忽略的 `local.properties` 或 `signing.properties`。私钥、密码、APK、运行缓存均不入库。构建成功不表示已经正式发布或真机验收通过。

## 设计与验收

- [设计 Tokens](docs/v4-design-tokens.md)
- [全功能 UI 目标](docs/v4-ui-spec.md)（目标不是当前完成清单）
- [参考应用初步分析](docs/v4-re-conclusions.md)
- [APK 证据复核](docs/v4-apk-evidence-review.md)
- [基础阶段验收记录](docs/v4-foundation-delivery.md)
- [文字发布、消息与 NBW 的交付边界](docs/v4-publishing-messages-nbw-delivery.md)

GitHub Actions 对 `develop`/`main` 的推送和 PR 执行主机侧测试、开发 APK 与 instrumentation APK 构建；不包含正式签名密钥，也不把开发签名构建当作对外测试交付。工作流是否在 GitHub 上实际跑通，以对应 run 结果为准。

玻璃仅在 API 33+、非低 RAM、硬件渲染且用户未要求减少透明效果时启用；否则完全不透明降级。本阶段是背景捕获与模糊，不宣称已经完成镜片折射效果或真实 GPU 验收。Robolectric 和 HTTP 契约测试也不能替代真实账号、设备、弱网、上传、推送与性能测试。

## 协作与许可

`develop` 为开发分支；功能走 `feat/*` 并合回 `develop`。只有用户明确发版指令才合入 `main`，推送不等于发布。

GPL-3.0-only，参见 [LICENSE](LICENSE)。不复制商业参考应用的代码、图标或素材；未来平移开源模块必须逐项保留其 SPDX、LICENSE 和 NOTICE。
