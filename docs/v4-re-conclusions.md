# V4 参考应用逆向结论（Phase 0）

日期：2026-10-25 · 工具：jadx 1.5.3（`/home/ZYongX/Projects/projects/.tools-v4/bin/jadx`）+ aapt2 + Pillow 像素采样

> **2026-10-26 更正：本文的技术栈概括是早期推测，已由 [APK 静态证据复核](v4-apk-evidence-review.md) 取代。** 酷安仅证实携带 Flutter module，不能由单 dex/so 名称推定首页、“我的”或主要 UI 全用 Dart；Activity 实测是 267 个加 1 个 alias，非 307；资源命名不能证明特定混淆工具；截图不能证明运行时 blur。旧文本保留供溯源，不应再当架构结论引用。下面颜色采样与几何估算未在复核中重新验证。

像素值来自截图采样，几何/字号为估算（标注 est.）。

## 1. 三个参考 APK 的身份与技术栈

| APK | 包名 / 版本 | 技术栈结论 | 证据 |
|---|---|---|---|
| CoolApk | `com.coolapk.market` 16.6.5 (2610162)，minSdk 24 / targetSdk 34 / compileSdk 37 | **Flutter 混合应用**：主要 UI 跑在 Dart（libapp.so 6.0MB + libflutter.so），Java 壳仅 1 个 classes.dex；原生侧有 IM（libImSDK，腾讯云 IM）、ffmpeg 视频链、腾讯 TPNS 推送、联合加固类 so（libNetHTProtect/libEncryptorP） | so 清单；单 dex；manifest 307 个 Activity 均为 `com.coolapk.market.view.*` 壳 |
| furryfurry | `cn.furryfurry.app` 1.2.9 (261018)，minSdk 24 / targetSdk 36 | **Flutter 应用**（libflutter.so + libapp.so + libdartjni.so），带自研安全 so（libfur_api_security 27MB、libfur_runtime 13.7MB）、ONNX Runtime（AI 能力）、ML Kit 条码（barhopper） | so 清单；单 dex |
| 百度贴吧 | `com.baidu.tieba` 22.7.1.1 (369557765)，minSdk 26 / targetSdk 30 | **原生 Android 重度混淆应用**：13 个 dex、百度地图 SDK、自有推送 bdpush、baiduprotect 加固，资源与代码名均混淆 | so/dex 清单 |

**结论**：两个现代化参考（酷安、furryfurry）都是 Flutter；贴吧是原生但不可逆向到资源值。因此：
- 视觉 tokens 以**截图像素采样**为唯一权威来源（酷安资源表被 AndResGuard 混淆成 `00P.xml` 式文件名，无提取价值）；
- 它们的实现栈**不构成** V4 的技术选型输入——V4 维持 Kotlin + Compose（已确认决策），只取视觉/交互规格。
- 酷安 manifest 未混淆，可按需在后续阶段反查特定页面的 Activity 结构与导出行为。

## 2. 像素采样结果（暗色主题，720×1640 截图）

### 酷安（主参考，暗色）
| Token | 值 | 采样位置 |
|---|---|---|
| 页面背景 page | `#101010` | 内容区页底色（非纯黑） |
| 卡片面 card | `#1C1C1E` | 「我的关注」卡 / 功能宫格卡（iOS 系暗色面） |
| 浮起面 elevated | `#2C2C2E` | 顶部搜索胶囊内芯 |
| 品牌强调 accent | `#1AA193`（青绿） | 底部中央 ＋ FAB 大圆钮（采样峰值 #1aa193，644px） |
| 激活态文字/图标 | ≈`#128E83` | 顶栏「数码」激活页签文字 |
| 主文字 primary | `#D2D2D2`（卡内）/ `#FFFFFF`（强调，如用户名） | 卡片标题、正文 |
| 次级文字 secondary | `#8E8E8E` | 「更多 >」 |
| 底栏悬浮胶囊 | 深色玻璃（采样 #181818~#303030 混合，带 backdrop blur），悬浮于内容之上、底部留边 | 底栏区域垂直扫描 |

### 贴吧（信息流参考，暗色）
| Token | 值 |
|---|---|
| 页面背景 | `#080808`（边缘）~`#141414`（内容区主导） |
| 卡片面 | `#1F1F1F` |
| 主文字 | `#E8E8E8` |
| 品牌强调 | `#5070F0`（蓝，页签激活/按钮） |

### furryfurry（浅色参考）
| Token | 值 |
|---|---|
| 页面/卡片背景 | `#FFFFFF`（卡片以浅灰描边/阴影区分） |
| 品牌强调 | `#F0A010`（橙，FAB/页签下划线） |
| 辅助色 | `#309060`（绿，成功/积分类图标） |

### ABDL 自有品牌色（abdl_icon.xml，观察）
婴儿蓝 `#A1D9F7` + 奶油黄 `#FFF9B2` + 白。V4 强调色据此定为 **#64BFF2**（婴儿蓝加深以通过暗底对比度），赞助/尊贵元素辅助色 **#FFD98E**（奶油黄）。

## 3. 几何估算（est.，720px 宽 ≈ 360dp @2x）

- 页面左右留白：10dp；卡片间距：10dp；卡片内边距：≈12-16dp。
- 卡片圆角：≈14dp（est.，酷安卡片角曲线半径 28px±）；搜索胶囊为全圆角（stadium）。
- 底栏：悬浮胶囊，左右留边 ≈6-8dp，高 ≈56-64dp（est.），全圆角；4 个页签 + 中央 ≈56dp FAB 圆钮（酷安 FAB 略突出胶囊上沿）。
- 顶栏页签行高 ≈40dp，激活页签带下划线短条 + 品牌色文字。

## 4. 后续反查入口（按需使用）

- jadx 已解出酷安 manifest：`/home/ZYongX/Projects/projects/.tools-v4/coolapk-res/resources/AndroidManifest.xml`（307 Activity）。
- 贴吧/furryfurry 未做深度反编译（混淆重、参考价值低）。若某页规格存疑，优先用模拟器安装参考 APK 实测，而非继续静态逆向。
