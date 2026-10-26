# V4 APK 静态证据复核：技术栈、组件边界与不可推断项

日期：2026-10-26。性质：**针对两份指定 APK 的授权、离线、有限范围静态分析，不是运行时测试，也不是完整源码恢复。**

本报告复核 `/home/ZYongX/Projects/projects/ABDL-Space-V4/docs/v4-re-conclusions.md` 中的技术栈和实现推断；不修改该文件，不复核其截图像素采样，不涉及第三份贴吧 APK。V4 仓库内本轮仅新增本报告，没有导入竞品源码、反编译源码文件、图片、字体、资源、模型或二进制库。

## 1. 先给结论

1. **CoolApk 可以证实“APK 携带 Flutter AOT/引擎，并声明 Flutter module 与独立 Flutter Activity”，不能证实“主要 UI、首页、我的均由 Flutter 实现”。** 可见 DEX 是 wrapper/JNI 装载层，两个业务 Activity 的单类反编译均未找到类。主页面及“我的”的 Native/Flutter 归属均为**未知**；Manifest 的原生组件声明也不能反过来证明这些页面全部用 Android View 渲染。
2. **CoolApk 的唯一 DEX 不等于 Java 薄壳。** 该文件实际为 98,084,684 字节，公开 DEX 表却只有 30 个类；标准 data 区终点为 63,160，后面仍有 98,021,524 字节。结合 wrapper Application 的 JNI 装载/代理调用，应把“受封装/保护影响、业务代码不可见”列为覆盖限制，而不是把可见类数当作原应用业务规模。
3. **“307 个 Activity，全部是 `com.coolapk.market.view.*` 壳”不成立。** 本次对源 APK 的 AXML 与既有解码 Manifest 交叉计数，均为 **267 个 `<activity>`、1 个 `<activity-alias>`**；其中 153 个 Activity 名称以该 view 前缀开头。声明不等于实现，更不等于“壳”。
4. **furryfurry 有比“存在两个 Flutter so”更强的 Flutter 宿主证据。** 原生启动屏跳转 MainActivity；MainActivity 的父类链最终包含 Flutter 引擎/视图接入。可表述为“原生启动屏 + Flutter 主宿主 + 原生插件/JNI”。但本轮未恢复 Dart 页面调用图，不能把任意截图或所有子页面逐一归属。
5. **furryfurry 的两个自有命名 `.so` 可以证实到 JNI/原生库接口层，不能证实 Rust、自研归属、具体安全算法或真实调用频率。** 当前找到 Java 端加载声明、对应 JNI/动态导出及一个 C++ ABI 符号；在指定库的有限检索中没有 Rust 编译路径/桥接指纹。没有指纹也不构成“绝无 Rust”的证明。
6. **静态可见的缩略图、预缓存、分页主要来自 furryfurry 的本地相册插件。** 不能把这套机制外推成远端首页信息流、CDN 缩略图或推荐算法。V4 可以借鉴组件职责分离，继续使用 Kotlin + Compose；**“不用同一工具栈就更快”没有证据支持。**

## 2. 身份、证据等级与复现边界

### 2.1 两个源 APK 的精确身份

后文每条证据用源 ID `C` 或 `F` 绑定下列完整 SHA-256；同名、同版本但哈希不同的 APK 不在本结论范围内。

| 源 ID | 绝对路径 | SHA-256 | APK 字节数 | `aapt dump badging` 观察 |
|---|---|---|---:|---|
| C | `/home/ZYongX/Downloads/CoolApk-16.6.5-2610162-coolapk-arm64-sign.apk` | `65ffb78612a20dcf0bad7b7227aad692fa027cacd33c88c9bb4d06c88ae1785e` | 113,900,115 | `com.coolapk.market`，16.6.5 / 2610162；minSdk 24、targetSdk 34、compileSdk 37；声明 ABI `arm64-v8a` |
| F | `/home/ZYongX/Downloads/base.apk` | `612dbda0acecf82e1e648c77368464b4e345ba3483180083202e8f3855c14c31` | 156,038,137 | `cn.furryfurry.app`，绒绒，1.2.9 / 261018；minSdk 24、targetSdk 36、compileSdk 36；声明 ABI `arm64-v8a` |

身份工具：`sha256sum` 与 Python `hashlib` 独立计算后，REA 的源摘要也一致。**未做签名证书信任链/官方发行来源认证**；文件名中的 `sign` 不是官方签名证明。

### 2.2 证据等级

- **观察 O**：源 APK 的成员、声明、公开结构表、符号、字节串或反编译得到的静态调用。
- **推断 I**：多个观察支持的有限解释，例如“符合 add-to-app/module 集成形态”。推断不等于执行记录。
- **未知 U**：因加固/封装、混淆、Dart AOT 未恢复调用图、未运行目标等限制，当前不能回答的部分。
- 反编译偏移来自 JADX JSON；本报告按 `classes.dex` 内工具定位值引用，不把它解释成 CPU 地址。ELF 导出表的 `virtual_address` 与文件偏移另列，二者不能混用。
- AXML `line=` 是二进制 XML 携带的源行号；既有解码 XML 的文本行号是另一种坐标，下面明确区分。

### 2.3 实际使用的工具与资源限制

| 工具 | 位置/版本 | 使用范围 |
|---|---|---|
| JADX | `/home/ZYongX/Projects/projects/.tools-v4/bin/jadx`，1.5.3；JAR 位于 `/home/ZYongX/Projects/projects/.tools-v4/lib/jadx-1.5.3-all.jar` | 仅 `--single-class` 定点输出 JSON；不做全项目源码导出 |
| REA CLI | `/home/ZYongX/.npm/_npx/9b9809298fe40d2c/node_modules/.bin/rea`，缓存包 6.7.0 | 两次 `identify-flutter-build`；未安装、下载或修改 MCP |
| aapt / aapt2 | `/usr/sbin/aapt`、`/usr/sbin/aapt2`；aapt2 显示 `2.20-15087165` | 读取源 APK 的 badging 与 Manifest XML 树 |
| Python 标准库 | `zipfile`、`hashlib`、`struct`、`re`、`json` | ZIP 成员、公开 DEX 表、有限 URI 字节串、所选 ELF 结构与动态符号；不导入/执行目标代码 |

JADX 子进程统一使用 `JAVA_OPTS='-Xms64m -Xmx384m -XX:ActiveProcessorCount=1 -XX:+UseSerialGC'`、`-j 1`；变量仅对该子进程生效，没有改父会话/Gradle 环境。REA 使用 `NODE_OPTIONS='--max-old-space-size=384 --v8-pool-size=1'` 和 `UV_THREADPOOL_SIZE=1`。这里的 heap/old-space 上限不等于进程 RSS 上限。

按要求首先调用 `reverse-engineer-anything` Skill，工具返回重名歧义；随后直接读取已有 Skill、Android 与连接恢复指引。当前会话没有 REA MCP 工具，采用现有 CLI 和本地工具，没有执行 doctor/setup、重装或下载引擎。`identify-flutter-build`、`inspect-artifact` 的本地注册 schema 与公开 `--help` 均已核对：前者接收位置参数 APK 路径；后者接收路径及 `--integrity-policy`。**本轮没有实际执行 `inspect-artifact`**，因为定向 ZIP 成员读取已经回答包结构问题；没有试探未知命令。

## 3. 可复核证据表

### 3.1 Manifest、封装边界与宿主

| 证据 ID / 源哈希 ID | 源路径、类或方法定位 | 工具及静态观察 O | 有限推断 I | 未知/禁止外推 U |
|---|---|---|---|---|
| C-M1 / C | APK `AndroidManifest.xml`，AXML application 源行 298；既有解码 XML 文本行 217–233 | aapt 与既有 XML：Application 为 `com.netease.nis.wrapper.MyApplication`，有 `hardwareAccelerated=true`、`largeHeap=true`；factory 为 `androidx.core.app.CoreComponentFactory` | 存在 Android Application/组件层 | 这些开关不是流畅性证明，不能推定 Compose/View/Flutter 页面占比 |
| C-M2 / C | 同成员，AXML 源行 423–434；既有 XML 文本行 310–340 | launcher 为 `com.coolapk.market.view.main.MainActivity`，MAIN + LAUNCHER；另有 VIEW/deep-link 声明 | 平台入口及部分 Intent 路由可见 | MainActivity 的父类、内部 Fragment、首页与“我的”实际渲染器未知；Intent 声明不证明路由运行成功 |
| C-M3 / C | 同成员，AXML 源行 632、1515、1522；既有 XML 文本行 476–480 | 声明 `com.coolapk.market.view.flutter.CoolFlutterActivity`；application metadata 为 `flutterProjectType=module`、`flutterEmbedding=2` | 支持 Flutter module/add-to-app 集成形态 | Activity 名称/metadata 本身不证明继承关系、当前入口路由及哪些业务页面使用它 |
| C-M4 / C | APK Manifest 的 application 子节点；缓存 `/home/ZYongX/Projects/projects/.tools-v4/coolapk-res/resources/AndroidManifest.xml` | aapt XML 树与 ElementTree 对缓存独立计数：267 Activity + 1 alias；153 Activity 属于 `com.coolapk.market.view.*` 前缀；其余包含第三方组件名称 | 原报告 307/全部 view 前缀的说法需撤回 | 不能称 267 个“已确认原生业务页”，也不能称它们全是壳 |
| C-D1 / C | APK `classes.dex`，头字段 `string_ids_size`、`class_defs_size`、`data_off/data_size` | Python `struct`：仅一个 dex 成员，98,084,684 字节；955 字符串、133 类型、596 方法引用、30 类定义；`data_off=14616`、`data_size=48544`、终点 `0xf6b8`；终点后 98,021,524 字节。30 类为一个 `a.auu.a` 和 29 个 `com.netease.nis.wrapper*` 类 | 公开结构明显不足以代表原业务代码；与封装/保护装载层一致 | 尾部内容性质、原始 dex 数量、加密/压缩算法、业务类分布未知；未解包/绕过保护 |
| C-D2 / C | `MyApplication.attachBaseContext(Context)` `0x96b8`；`onCreate()` `0x9b94`；`a(Context)` `0x94dc` | JADX 单类：静态存在 `MyJni.load` 调用点 `0x97b2`、`MyJni.run` `0x9bd8`、Application 创建/生命周期转交；`a(Context)` 有 ClassLoader 加载另一 Application 的结构。`MyJni` 另声明 native `cl/load/run/ra/rp/ip/cp` 等接口 | **包装/装载/代理层有方法级证据**，不能只凭单 dex 判断“Java 很薄、业务都在 Dart” | 尚未观察 native 实际装载、解密或业务执行；namespace/so 文件名不等于某加固产品被确认、联合加固或算法识别 |
| C-D3 / C | `com.coolapk.market.view.main.MainActivity`、`com.coolapk.market.view.flutter.CoolFlutterActivity` | 两次 JADX `--single-class` 均报 `Input class not found`；公开 class_defs 也无这两个类 | 在当前静态可见代码范围，无法追踪业务宿主链 | 这不是“类从未存在/运行”、不是“全部 Flutter”的证据，也不应继续从 Activity 名称猜页面 |
| F-M1 / F | APK `AndroidManifest.xml`，application 源行 83，Activity 源行 90、102，metadata 源行 111、354 | aapt：Application 为 `android.app.Application`；7 Activity + 4 alias；launcher 为 `cn.furryfurry.app.FurSplashActivity`；MainActivity 为 `singleTask`、硬件加速，含 `io.flutter.embedding.android.NormalTheme`；`flutterEmbedding=2` | 有独立原生启动屏、Flutter embedding 配置和主宿主候选 | 仅从 Manifest 不能判断 Flutter 中所有页面、底栏状态保存和返回栈策略 |
| F-D1 / F | `FurSplashActivity.onCreate(Bundle)` `0x2cfaf8` | JADX 单类：构建原生 ImageView 并设置启动图片，创建 MainActivity Intent（`0x2cfb6c`）、启动（`0x2cfb7c`）、结束启动屏（`0x2cfb82`） | **启动屏是 Android 原生视图**，后续进入 MainActivity 的静态链成立 | 未运行，不确认冷启动时间、首帧无闪烁或动画观感 |
| F-D2 / F | `MainActivity` → 原始 descriptor `LW2/n;` → `LB5/e;` → `android.app.Activity`；`B5.e.onCreate` `0x129218` | DEX 父类表 + JADX 单类：W2.n/B5.e 是混淆类；B5.e 有 Flutter engine/activity 接入、renderer 与视图构建，并在 `0x12a368` 设置宿主内容。W2.n.onCreate `0x21d510` 调用父类 | **MainActivity 是 Flutter 宿主**，证据不依赖单纯 so 名称或父类重命名猜测 | 未还原 Dart 页面树；不是“启动屏、每个子页面、平台 View 都完全 Dart”的证明；父类输出有依赖方法报错，见限制 |

### 3.2 Flutter 构建、assets 与原生库

| 证据 ID / 源哈希 ID | 定位 | 工具及观察 O | 推断 I / 未知 U |
|---|---|---|---|
| C-F1 / C | `lib/arm64-v8a/libapp.so`、`libflutter.so` | REA `identify-flutter-build`：识别 Flutter；libapp 6,030,256 字节、有效支持范围内 ELF 头；从两个快照位置得到同一 hash `78da37fed6bf1489361a312568249f3f` | 可证实 Dart AOT/Flutter payload，不是页面归属、框架版本映射或业务占比证据 |
| F-F1 / F | 同成员 | REA：libapp 23,987,120 字节；快照 hash 也为 `78da37fed6bf1489361a312568249f3f` | 与 F-M1/F-D2 合并可支持 Flutter 主宿主；so 大小不能换算 Dart 代码行数、页面数或性能 |
| CF-F2 / C、F | 两包的 `lib/arm64-v8a/libflutter.so` | REA 与成员 SHA-256：两者均为 11,317,712 字节、SHA `50d733719c7c9eb23c5aa8ea62ef06755e20a8a83e9fcbb0434ebde643f6f0ac`；ELF build ID `d73e2148690c74c5118334d5bfdac1dd153b1cbb`；`dart_version=null` | 只能证明这两个**引擎成员字节相同**；不证明应用代码相同、两者速度相同或确切 Flutter/Dart 版本。未查询外部 hash 映射 |
| C-A1 / C | ZIP 中 `assets/flutter_assets/` 前缀及含 flutter 的成员名 | Python zipfile 全成员目录过滤：常规 Flutter assets 前缀下 **0 个成员**；名称含 flutter 的成员只有引擎 so | 缺少普通 assets 布局与可见 module 声明并存；assets 可能有其他封装/位置/生成机制，但本轮**未验证任何一种解释**，不能认定“不含 Flutter”或“已动态下载” |
| F-A1 / F | `assets/flutter_assets/` | 132 个成员、未压缩合计 15,345,801 字节；包含 `AssetManifest.bin` 11,595 字节、`FontManifest.json` 226 字节、`NOTICES.Z` 121,643 字节、`NativeAssetsManifest.json` 45 字节；后者解析为 format-version `[1,0,0]`，native-assets 空对象 | 标准 Flutter 资源打包证据；空 native-assets 不否定通过 JNI/System.loadLibrary 集成原生库；未导出图片/字体至 V4 |
| F-A2 / F | Flutter assets 的 `packages/pro_image_editor/...`、`packages/record_web/...`、`packages/wakelock_plus/...` | ZIP 可见字体/shader/JS 等成员名；只记录目录与大小 | 能作为包资源种子；Android APK 带有 web 资源不证明 Android 正在执行相同 JS，更不能据此判断编辑/唤醒策略 |
| C-N1 / C | `lib/arm64-v8a/` 全部 `.so` 成员 | 共 49 个成员；完整路径/大小/SHA 在 `coolapk-inventory.json`。可见 `libImSDK.so`、`libNetHTProtect.so`、`libEncryptorP.so`、`libffmpegkit.so`、`libavcodec.so`、`libxgVipSecurity.so` 等文件名 | 仅证实这些命名的成员被打包。本轮未追到对应业务调用，不认定“腾讯 IM 正在用于私信”“联合加固”“ffmpeg 已用于某视频页”或推送送达机制 |
| F-N1 / F | `lib/arm64-v8a/` 全部 `.so` 成员 | 共 11 个；完整路径/大小/SHA 在 `furryfurry-inventory.json`，见下表 | 除有额外方法/符号支撑的两库外，文件名本身不是算法、作者、当前调用或体验证据 |

两个 REA 完整记录均为纯离线解析，本轮工具返回的 `coverage=complete` **只适用于构建识别字段覆盖**，不是“完整应用分析”。证据 ID：

- C：`ev_39de496d9b600250b1b75b8b1624690e78cf5e363e0474bd30392b248c39374f`
- F：`ev_2fdd25701c90456c5b04f9f01e6738de75d019491c1312ada7486cf303c02cc5`

#### furryfurry 原生库成员清单（观察，不自动解释职责）

共同路径前缀为 `lib/arm64-v8a/`，源均为 F；工具为 ZIP 成员读取。

| 成员文件名 | 未压缩字节数 | 本轮额外核实 |
|---|---:|---|
| `libapp.so` | 23,987,120 | REA Dart AOT 识别 |
| `libflutter.so` | 11,317,712 | REA 引擎身份 |
| `libdartjni.so` | 124,776 | GeneratedPluginRegistrant 有 `jni`、`jni_flutter` 插件注册分支；未追逐调用 |
| `libfur_api_security.so` | 27,014,728 | Java 加载声明与对应 JNI 导出，见 F-N2 |
| `libfur_runtime.so` | 13,721,352 | Java 加载声明、JNI_OnLoad 及目标变换导出，见 F-N3 |
| `libonnxruntime.so` | 16,276,816 | DEX 另有 62 个 `ai.onnxruntime.*` 类定义；只支持打包原生/Java 接入组件，不证明具体 AI 功能或推理已运行 |
| `libonnxruntime4j_jni.so` | 78,328 | 成员；未验证模型加载/推理调用 |
| `libbarhopper_v3.so` | 4,946,720 | 同包有 mobile_scanner 注册分支；不把文件名直接当成实际扫码/模型路径证据 |
| `libdatastore_shared_counter.so` | 7,112 | 成员；未追调用 |
| `libimage_processing_util_jni.so` | 32,528 | 成员；未追调用 |
| `libsurface_util_jni.so` | 4,896 | 成员；未追调用 |

### 3.3 furryfurry 的 JNI、Rust 与实现语言边界

| 证据 ID / 源 | 类/成员/方法定位 | 观察 O | 可以说 / 不可以说 |
|---|---|---|---|
| F-N2 / F | `FurApiSecurityNative.<clinit>` `0x2cef7c`，加载点 `0x2cef9e`；native `sendAuthRequest(...)`、`signDevicePayload(PrivateKey,String)` | JADX：声明加载 `fur_api_security`，并声明两个 JNI 接口。ELF PT_DYNAMIC/DT_GNU_HASH 动态符号解析：存在 `Java_cn_furryfurry_app_FurApiSecurityNative_signDevicePayload` 与 `..._sendAuthRequest`，虚拟地址分别 `0x68bc4`、`0x65eac`；符号表项文件偏移 `0x197d2c0`、`0x1981cb8` | 可说 Java/JNI ABI 对应关系存在、具备这些命名的原生接口。不可说用了何种签名算法、签名真实内容、安全强度、自研归属或线上确实调用；未读取 PrivateKey 实例/凭据 |
| F-N3 / F | `FurRuntimeNative.<clinit>` `0x2cfa9c`，加载点 `0x2cfabe`；native `transformRequestTarget(String)` | JADX：声明加载 `fur_runtime`。ELF 动态导出有 `JNI_OnLoad`（VA `0x64f04`）、`transformRequestTargetNative`（VA `0x64c88`）及 C++ ABI 命名 `_ZN3fur9transport22transformRequestTargetEP7_JNIEnvNSt6__ndk117basic_string_viewIcNS3_11char_traitsIcEEEE`（VA `0x63b48`） | 可证实存在原生装载入口、目标变换相关命名接口和 C++ ABI 符号；未反汇编 JNI_OnLoad，不能声称已完整证明动态注册映射或该方法具体变换/加密规则 |
| F-N4 / F | 上述两库的 ELF section table、动态区和原始字节 | Python：两库均为 ELF64 little-endian、ET_DYN、AArch64；section 名仅空项、`.dynstr`、`.dynamic`、`.shstrtab`，没有 `.comment`/`.dep-v0`。没有常规 `.dynsym` section 名，因此通过动态区与 GNU hash 读取符号，而非据 section 名缺失判“无符号” | 公开元数据较少/布局非普通未处理库，是静态覆盖限制；没有识别具体保护器、解包或执行 |
| F-N5 / F | 两个选定库完整字节的**有限指纹**搜索 | `/rustc/<40 hex>/`、`rust/cargo/tokio` ASCII、`library/(std或core)/src/`、`flutter_rust_bridge/frbgen/RustOpaque` 等模式命中均为 0；libapp 的桥接模式搜索亦无支持性命中 | **Rust 未证实。** 负结果仅约束这些模式、这两个库和选定 AOT 模式；被去符号、保护、静态链接或其他语言混合等可能性未排除。C++ ABI 符号也不能证明所有代码都用 C++ |

两库成员 SHA-256：

- `libfur_api_security.so`：`9c1acdad36f28fe99123791575a59ef31c2be02169ba1a61a0b59020947ae95d`
- `libfur_runtime.so`：`e4f4e10fa6eb7a19172834357b3dd7198d0c9698108b27d8170b720dcf8ef811`

结论应停在 **Flutter 宿主 + JNI/原生库边界**。本轮既没有恢复 Rust 源码，也没有证实 Rust；“自研安全 so”“Rust 性能优化”“原生算法负责推荐/IM/AI”等措辞均超出证据。

### 3.4 DEX namespace / Dart URI 种子：只用于下一步定位

公开 DEX 类表：F 为一个 `classes.dex`，6,353,404 字节，38,435 字符串、6,987 类定义。其中 `cn.furryfurry.*` 7 类、`io.flutter.*` 101 类、`com.bumptech.glide.*` 29 类；这些前缀有包含关系，不能相加当作总数。`coil/coil3`、`androidx.paging`、`androidx.navigation` 等指定前缀在当前类名表中的零命中，**不证明其 Dart 等价机制不存在**，也不能排除混淆导致漏检。

| 源 / 成员文件偏移 | 原始 URI 字节串或 descriptor | 证据强度与边界 |
|---|---|---|
| C / `libapp.so` `0x2bb87`、`0x36b5c` | `package:flutter_embed_module/app/routes.dart`；`package:flutter_embed_module/core/api/host_api_bridge.dart` | module/routing/host bridge 的命名种子，与 C-M3 相互印证；没有恢复桥接调用关系 |
| C / 同成员 `0x73724` | `package:flutter_embed_module/features/home/pages/home_page.dart` | **不能把其中 home 等同于酷安 launcher 首页**；只是模块内保留的编译 URI |
| C / 同成员 `0x3e6e8`、`0x5487d`、`0x89058` | `.../ai_ask_result.dart`、`.../ai_ask_history.dart`、`.../ask_history_panel.dart` | 相关命名聚集可供后续找模块入口；未证实 AI 功能实际运行、模型/服务，也未定位它在 UI 哪一页 |
| F / `classes.dex` string 数据 `0x525955` | `Lcn/furryfurry/app/MainActivity;` | 类型/父类表种子，可继续到 F-D2，而不是停在字符串猜测 |
| F / `libapp.so` `0xb820a`、`0x13443e`、`0x120a7d`、`0x17c402` | `package:rongrong_app/main.dart`、`.../app/fur_main_shell.dart`、`.../app/widgets/fur_app_bottom_nav.dart`、`.../app/fur_app_action_router.dart` | 支持 Dart 主壳/导航候选的存在；尚无各 Tab 对应路由、返回栈或状态恢复的静态调用图 |
| F / 同成员 `0x13ee48`、`0xc1837` | `package:cached_network_image/src/image_provider/multi_image_stream_completer.dart`、`package:flutter_cache_manager/src/cache_manager.dart` | 支持这些组件相关 URI 被编译保留，**不证明首页用了默认配置、某版本、缓存 TTL 或特定淘汰算法** |
| F / 同成员 `0x164e09`、`0x1e1fbb` | `package:rongrong_app/core/cache/fur_image_cache_manager.dart`、`.../fur_public_image_cache.dart` | 自有命名的缓存层候选；未反编译函数，不从文件名推导实现/容量/隐私保证 |

工具为 Python 在两个 `libapp.so` 原始字节上匹配受限模式 `package:<标识符>/<路径>.dart`，C/F 分别命中 652/2,410 次；这不是 Dart 快照对象解码、包管理锁文件或源码恢复，命中次数不等于依赖文件/页面总数。报告只保留必要路径种子，不导入商业实现。

## 4. 缩略图、缓存、分页、导航、图片库与性能：能看到什么

以下方法定位均为源 F 的 `classes.dex`，源哈希见 §2.1。关键链条不是“看见 thumbnail 字符串”，而是注册入口 → channel → 分发方法的有限静态跟踪。

| 证据 ID / 主题 | 类、方法与偏移；工具 | 静态观察 O | 可支持的有限结论 I | 仍未知 U |
|---|---|---|---|---|
| F-P1 / 插件归属 | `io.flutter.plugins.GeneratedPluginRegistrant.registerWith(LC5/c;)V` `0x388b44`；photo_manager 注册分支 `0x388c98` / 日志标识 `0x388ca6`；JADX | 注册分支构造原始类 `x3.b`；该类 `onAttachedToEngine(LI5/a;)V` `0x488dc0` 在 `0x488e12` 绑定 channel `com.fluttercandies/photo_manager`，handler 为原始类 `z3.h`（JADX alias `C2129h`） | 后续行为可以归到**本地媒体管理插件**，不是任意首页业务 | Dart 何时/在哪页发起 channel 调用未知；静态存在注册分支不是运行注册成功记录 |
| F-P2 / 缩略图 | `z3.h.d(LH3/c;Z)V` `0x4b74e8`，getThumb 分支 `0x4b75da`；JADX | 读取 asset id 和 option，取尺寸、压缩格式、quality 等参数，再在 `0x4b7662` 调用缩略图处理入口；有尺寸相关错误处理 | 插件具备参数化本地缩略图能力，尺寸/质量不是只能请求原图 | 实际请求尺寸/质量、信息流是否复用此接口、CDN 派生图参数/服务器转码策略未知 |
| F-P3 / 预缓存与图片库 | 同方法 requestCacheAssetsThumb 分支 `0x4b83be`；Glide 调用 `0x4b8476`；JADX | ids/option → 本地路径列表 → Glide LOW 优先级请求 → 按 option 尺寸创建 future/target → 保存异步任务 | **局部本地媒体缩略图预缓存分支使用 Glide，有低优先级请求设置** | 不是“全应用图片都用 Glide”；也不证明首页预加载窗口、帧率、内存收益或命中率 |
| F-P4 / 缓存清理 | `z3.h.w(LL5/q;LM0/p;)V` `0x4b8f00`，clearFileCache 分支 `0x4b9066`；JADX | 分支取得 Glide 实例并调用内部清理/容量归零相关入口，随后派发后台任务；同分发列表另含 releaseMemoryCache | 插件具有缓存清理控制点，可见库内存资源清理及异步分派结构 | 未追完后台任务和所有混淆字段，**不把方法名当作已证明“所有磁盘缓存都清完”**；缓存路径、容量、LRU、TTL、账户隔离未知 |
| F-P5 / 分页 | `z3.h.d`：getAssetListPaged 分支 `0x4b877e`，读 page `0x4b87b6`、size `0x4b87d4`，调用资产枚举入口 `0x4b8832`；另有 getAssetListRange `0x4b86ec` | page/size 或 start/end 参与本地资产枚举调用 | **本地相册分页接口可见** | 不能外推远端 feed 的 cursor/page、去重、刷新合并、末页条件、预取阈值或 Paging 3 使用情况；没有伪称 SDK 字符串 hasMore 等就是业务协议 |
| F-P6 / Dart 图片与缓存组件 | §3.4 所列 AOT URI；Python 原始字节检索 | cached_network_image/flutter_cache_manager 的 URI 与多个缓存层命名被保留 | 可作为 Flutter 图片管线候选，有进一步追踪价值 | 未取得真实请求调用/配置，不确认默认 TTL、最大对象数、持久化数据库结构、具体淘汰策略或哪页用哪库 |
| F-P7 / 导航 | F-D1/F-D2 的启动链、Manifest 的 aliases，以及 §3.4 的 main shell/bottom nav/router URI | Android 启动跳转与宿主链有方法级证据，Dart 导航仅 URI 级证据 | 原生入口与 Flutter 内部导航应分层理解 | 不确认 bottom nav 的懒加载、保活、Navigator 嵌套、手势返回、深链恢复或页面切换动画 |
| F-P8 / 刷新率请求 | 原始 `W2.n.y()Landroid/view/Display$Mode;` `0x21b66c`；`n()V` `0x21bf9c`；`onCreate` `0x21d510`、`onConfigurationChanged` `0x21d4b8`；JADX | y 优先筛同分辨率模式并选较高 refresh rate；n 比较/设置 window 的 preferredRefreshRate 与 preferredDisplayModeId；创建/配置变化有调用点 | **存在向系统请求较高刷新率的静态意图**，不是从 UI 观感推断优化 | 系统是否采纳、渲染是否跟上、功耗代价、120 FPS 达成、与竞品性能优劣未知 |
| F-P9 / 内存响应 | `B5.e.onTrimMemory(I)` `0x12abd0`，FlutterJNI 相关调用 `0x12ac1e`–`0x12ac2e`；JADX | 有 Flutter 低内存通知相关逻辑 | 标准宿主生命周期内的内存压力响应点可见 | 不是应用专属优化、低内存问题已解决或内存占用更低的证据 |
| C-P1 / CoolApk 同类问题 | C-D1–C-D3 与 §3.4；JADX/DEX 表/URI | 业务 Java/Kotlin 类不可见；AOT 中有 Flutter 通用 image_cache 等 URI | 当前只能描述打包组件和模块候选 | **首页缩略图/图片库/缓存参数/分页/底栏/性能优化均未定位到业务方法**；不能用通用 Flutter 库存在就宣称自有业务策略 |

### 明确不从静态材料推断的内容

- 不从 `libImSDK` 名称认定私信实现/供应商实际使用；不从 ai/onnx 文件名认定推荐、排序、分割等算法已执行。
- 不从 `.so` 名称判断“联合加固”、自研、Rust、JNI 真实调用次数或 native 比 Dart/Kotlin 更快。
- 不从资源文件名乱码断言某具体资源混淆器，亦不认定“资源表完全无价值”。本轮 aapt 能读取 Manifest，JADX 对 CoolApk 的资源表出现兼容/解析错误；二者可以同时成立。
- 不从硬件加速、largeHeap、缓存组件、低优先级请求或高刷偏好，推出滑动无掉帧/启动更快/能耗更低。
- 截图可以用于约束**可见颜色/几何/状态**，不能单靠像素证明使用了运行时 backdrop blur、具体动画引擎、缓存或预加载算法。本轮没有重新进行截图采样。

## 5. 对既有结论的逐项修正

被复核文件保持原样：`/home/ZYongX/Projects/projects/ABDL-Space-V4/docs/v4-re-conclusions.md`；读取时 SHA-256 为 `22532283e4c44215d59c7a297dcfd2ed9e90a4c5be6d9912f730b69b88caf711`。

| 旧文位置/表述 | 复核后的表述 | 依据 |
|---|---|---|
| 第 10 行：“主要 UI 跑在 Dart”“Java 壳仅 1 个 dex” | 携带 Flutter AOT/engine，metadata 声明 module；可见 dex 主要是 wrapper/JNI 装载层。**主 UI 占比、首页及我的技术实现未知** | C-M2/M3、C-D1/D2/D3、C-F1 |
| 第 10、60 行：“307 Activity”“均为 view 壳” | 此源 APK 为 267 Activity + 1 alias，153 个 view 前缀。组件声明不是可确认渲染页面或壳的计数 | C-M4 |
| 第 10 行：按 so 清单认定 IM、视频链、推送和联合加固实际实现 | 改为“存在这些命名的包成员”；有方法/符号证据时再上升到接口集成；无运行时证据不认定实际功能链 | C-N1、C-D2 |
| 第 11 行：“自研安全 so”“ONNX Runtime（AI 能力）” | Flutter 宿主已证实；两命名库的 JNI/原生接口已证实，但作者、Rust、算法、实际 AI 场景与效果未证实 | F-D2、F-N1–N5 |
| 第 14 行：“两个现代化参考都是 Flutter” | furryfurry 是有原生启动和插件层的 Flutter 宿主应用；CoolApk 是携带 Flutter module 的 Android 包，不能由当前证据确定全部主页面栈 | C/F 宿主证据 |
| 第 15 行：“AndResGuard”“无提取价值” | 资源名/解析异常只能作为资源层边界，不足以识别工具品牌或否定全部语义价值；UI 视觉指标和实现机制分开取证 | 解析限制、C-M1–M4 |
| 第 16 行：实现栈不构成任何选型输入 | 可作为**边界设计**输入：宿主、UI、媒体、网络/缓存、平台能力应可拆分；但不是强迫 V4 复制框架/SDK 的依据，也不支持跨框架性能排名 | §4 与 §6 |
| 第 61 行：若存疑优先安装运行 | 本轮明确禁止运行目标/联网 prod，因此不能以该建议补齐本轮未知；本报告保留未知，不暗中安装 | 用户授权边界 |

## 6. V4 可执行建议：证据、工程决策与验证分开

以下是**V4 自有实现建议**，不是竞品源码复刻或本轮已经验证的事实。维持既定 Kotlin + Compose 合理：当前证据没有显示同款视觉必须依赖 Flutter，也没有显示 Compose 天然比 Flutter 更快。选择应基于团队维护成本、V4 现有架构、平台集成和自有测量。

| 建议 | 证据如何支持 | V4 具体落点 | 验证/验收方式（只针对 V4，后续执行） |
|---|---|---|---|
| P0：清除选型前提中的错误归因 | C 的页面归属未知，F 有跨层宿主/插件；不是单框架包办 | 后续规格/任务引用本报告的 O/I/U 分层；不要写“酷安首页 Flutter，所以必须……”或“我们 Native 所以肯定更快” | 每项性能/体验结论必须能指向 V4 的测试数据或明确标为待验证 |
| P0：组件层独立于页面框架 | §4 可见的能力按本地媒体/宿主拆分 | Compose 自有 `AppShell`、底栏、`FeedCard`、`AuthorRow`、`MediaGrid`、`Avatar`、`ActionRow`、状态面板和图片查看器；视觉 token 只来自已授权规格/自有设计，数据通过不可变 UI model 输入 | 组件预览/截图回归覆盖亮暗色、长文本、多图、加载/空/失败；不导入竞品字体/图片/布局/反编译实现 |
| P0：单一、自有图片管线 | F-P2/P3 支持“缩略图与原图分职责”，而不是同款 SDK 必需 | 沿用 V4 已有成熟图片库；若尚无统一方案，可选 Coil 3 等 Compose 适配库，封装为统一 MediaImage 接口。列表按容器像素需求解码，原图在查看器按需请求；头像/列表/原图使用可区分的缓存 key | 固定自有图片集测重复请求、解码尺寸、峰值内存、取消离屏请求和失败占位；不宣称库名本身带来性能优势 |
| P0：缓存策略必须有自有契约 | F-P4/P6 没有给出可复制的 TTL/容量 | Repository 统一内存/磁盘/HTTP 缓存职责；区分公开/私有媒体、账户与尺寸版本；明确退出清理、过期、离线与重验证规则。TTL/上限来自 V4 数据与隐私要求，不从竞品路径猜 | 用 mock 服务验证缓存命中/失效、账户切换不串图、撤销媒体与退出清理；记录字节预算而非盲目加大 heap |
| P0：远端 feed 分页单独设计 | F-P5 仅为本地相册分页 | 网络层明确 cursor 或 page 协议、重复 ID 去重、刷新/追加互斥、重试、末页、取消与顺序稳定。复杂流可用 Paging 3；简单流用受控状态机，不因竞品字符串就强行加库 | 固定 fixtures 测空页、重复、乱序、刷新与追加竞争、错误重试、恢复滚动；列表数据去重与刷新语义要先于预加载优化 |
| P1：导航状态显式管理 | F 原生入口可见，但 Dart Tab 保活未知；C 首页未知 | Compose NavHost/类型化 route 由 V4 自己定义；各 Tab 的筛选、列表位置与进程恢复状态放到合适的 ViewModel/SavedState；Lazy 列表使用稳定 key/contentType | 测反复切 Tab、详情返回、深链、配置变化和进程恢复；不按 Manifest Activity 数量一对一建 V4 页面 |
| P1：约束列表与预取工作 | F-P3 有低优先级本地缩略图请求，不能提供远端最佳窗口 | LazyColumn/LazyVerticalGrid 以稳定数据驱动，避免主线程解码/IO；有限且可取消地预取临近可见内容，不同时预载所有页；限制同时请求与解码，避免大面积实时 blur/过度绘制 | 用相同 fixtures 和快速滚动场景比较开/关预取；同时看流量、内存、帧耗时，不能只看缓存命中 |
| P1：平台/native 能力按需接入 | F 有 JNI、扫码/媒体插件，未证明 Rust 必需 | 相机、相册、视频等放平台适配层；只有 V4 自有热点测量或现有库需求足以支撑时再引入 JNI/Rust/FFI，不复制竞品二进制或安全逻辑 | 评估新增 ABI、包体、崩溃/调试、维护和审计成本；“Rust/so 更快”不能作为唯一理由 |
| P1：用自有性能实验决定优化顺序 | F-P8 仅证明高刷请求；C/F 均无实测帧数据 | 后续在 V4 release/profileable 配置上，用 Macrobenchmark/Perfetto/JankStats 等选择合适工具，测冷/暖启动、首屏可用时间、长列表、多图与图片查看器。必要时评估 V4 Baseline Profile | 固定设备/系统/刷新率、数据与缓存冷暖条件，重复采样报告 p50/p95、卡顿、内存/流量；60Hz/120Hz 的显示帧预算约 16.67ms/8.33ms，并非自动达标保证 |

优先顺序建议：**先定义数据/图片/分页/导航契约和异常状态 → 再实现可复用 Compose 组件 → 再用固定数据量化瓶颈 → 最后决定预取、缓存预算、Profile 或 native 优化。** 这比追求与竞品一致的工具栈更可控，但“更可控”不是“必然更快”。本轮没有运行这些测试，也没有运行 Gradle。

## 7. 证据文件索引与复现

### 7.1 本轮新增的仓库外小型 JSON

统一目录：`/home/ZYongX/Projects/projects/.tools-v4/apk-evidence-review-20261026/`。下列文件均在 V4 仓库之外；没有提取整个 dex/so/资源目录，也没有生成商业源码工程。

| 文件 | 内容/对应证据 |
|---|---|
| `coolapk-inventory.json`、`furryfurry-inventory.json` | 完整源哈希、badging 摘要、AXML 关键节点/行号/计数、所有 so 的成员路径/大小/SHA、Flutter assets 清单、公开 DEX 表及有限 URI 种子 |
| `coolapk-rea-flutter.json`、`furryfurry-rea-flutter.json` | 两次 REA 完整 Evidence、成员摘要、snapshot hash/build ID、原始 limitations |
| `furryfurry-native-elf.json` | 两选定 so 的源绑定、section/动态区、GNU hash 枚举、选定符号和有限 Rust 指纹检索边界 |
| `single-class-index.json` | 14 次定点选择、源 hash、返回码、12 份成功生成输出的文件名/大小/SHA；两个 CoolApk 业务类未找到 |
| `coolapk-jadx-com.netease.nis.wrapper.MyApplication.json`、`...MyJni.json` | C-D2 的 wrapper/JNI 证据，不含已解包业务代码 |
| `furryfurry-jadx-cn.furryfurry.app.FurSplashActivity.json`、`...MainActivity.json`、`furryfurry-jadx-W2.n.json`、`furryfurry-jadx-B5.e.json` | F 的启动链、宿主链、刷新率请求与生命周期定位 |
| `furryfurry-jadx-cn.furryfurry.app.FurApiSecurityNative.json`、`...FurRuntimeNative.json` | F-N2/F-N3 的加载与 JNI 声明 |
| `furryfurry-jadx-io.flutter.plugins.GeneratedPluginRegistrant.json`、`furryfurry-jadx-x3.b.json`、`furryfurry-jadx-z3.h.json` | 插件 → channel → 相册分发链 |
| `furryfurry-jadx-com.bumptech.glide.GeneratedAppGlideModule.json` | 限定检查结果：抽象模块声明自身没有业务方法，不能仅凭它推断全应用配置 |
| 各 `*-run.json`、`review-provenance.json` | 精确参数、错误/返回码、工具版本与限制；JAR 摘要等溯源信息 |

这些 JADX JSON 是**定点反编译证据**，不是原始源码；不应复制进 V4 实现。报告只引用接口标识、路径和观察摘要。

### 7.2 可复现的只读命令

身份与 Manifest，可直接针对原 APK 重做，不需要安装目标：

```sh
sha256sum /home/ZYongX/Downloads/base.apk /home/ZYongX/Downloads/CoolApk-16.6.5-2610162-coolapk-arm64-sign.apk
aapt dump badging /home/ZYongX/Downloads/base.apk
aapt dump xmltree /home/ZYongX/Downloads/base.apk AndroidManifest.xml
aapt dump xmltree /home/ZYongX/Downloads/CoolApk-16.6.5-2610162-coolapk-arm64-sign.apk AndroidManifest.xml
```

REA 的实际公开用法如下；使用现成入口，不用 npx、不联网映射 SDK 版本：

```sh
NODE_OPTIONS='--max-old-space-size=384 --v8-pool-size=1' UV_THREADPOOL_SIZE=1 \
/home/ZYongX/.npm/_npx/9b9809298fe40d2c/node_modules/.bin/rea \
identify-flutter-build /home/ZYongX/Downloads/base.apk --format json
```

定点 JADX 的复核示例；如要保留新证据，须另选**尚不存在**的 JSON 路径，不覆盖本轮文件：

```sh
JAVA_OPTS='-Xms64m -Xmx384m -XX:ActiveProcessorCount=1 -XX:+UseSerialGC' \
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=1' \
/home/ZYongX/Projects/projects/.tools-v4/bin/jadx \
-r -j 1 --single-class cn.furryfurry.app.FurApiSecurityNative \
--output-format json \
--single-class-output /home/ZYongX/Projects/projects/.tools-v4/apk-evidence-review-20261026/recheck-FurApiSecurityNative.json \
--no-inline-methods --no-inline-anonymous --no-inline-kotlin-lambda \
--mappings-mode ignore --deobf-cfg-file-mode ignore \
--use-source-name-as-class-name-alias never --log-level error \
/home/ZYongX/Downloads/base.apk
```

ZIP 目录可用 Python `zipfile.ZipFile(...).infolist()` 复核，不解压：成员名以 `lib/` 开头且以 `.so` 结尾即纳入库清单；以 `assets/flutter_assets/` 开头即纳入本报告对应前缀计数。DEX 的 class_defs 计数来自头部偏移 `0x60`（size）和 `0x64`（offset），不是 `strings` 命中个数；data_size/data_off 来自 `0x68`/`0x6c`。Rust 指纹/URI 正则和 ELF 定位细节保留在 JSON 中，避免把无界字符串搜寻误称反编译。

## 8. 本轮限制与未解决清单

- **没有执行目标**：没有安装、启动、模拟器、动态脱壳、hook、抓包、重放请求、访问 prod，也没有读取账号、token、Keystore/PrivateKey 实例或其他凭据。方法签名出现 PrivateKey 不代表读取了密钥。
- **没有运行 Gradle 或 git**，没有更改旧仓；只写本报告及上述仓库外新 JSON。工具变量均是局部进程级，不调整父 Gradle/daemon/系统工具配置。
- 只检查用户指定的两份 APK；未触及第三份 13-dex 目标。公开 DEX 字符串/类型/类表有索引读取，但**没有全量方法反编译**；JADX `--single-class` 仍会建立可见类索引和处理必要依赖，不应被描述为完全不解析其他类。
- JADX 对 C 的 `resources.arsc` 报 `value size, expected: 0x8, actual: 0x3, offset: 0x1513e2`。wrapper 输出仍生成；两个业务 Activity 明确未找到。该错误本身不能识别资源混淆器，也不证明资源无价值。
- F 的 `B5.e` 和 `z3.h` 输出已生成，但返回码为 1：日志分别指出依赖方法 `B5.a.a(...)`、`a4.w.n(...)`，以及 `P0.g.E(...)` 失败。本文只使用所选类中可见的局部分支，并保留错误，不宣称反编译完整正确。未在本轮追加无界重试或全量恢复。
- 首次尝试把单类输出送到 `/dev/stdout` 被 JADX 按目录处理而失败；之后改用授权目录下的 JSON 路径。失败已记录，不计作取得 Application 证据的成功步骤。
- 没有反编译 Dart AOT 函数、恢复 protected DEX、解包/执行 `.so` 或建立完整引用图。有限负检索只说明所选模式未命中。
- **未解决**：CoolApk 首页/我的真实渲染栈、Flutter module 的实际启动路由；两者远端 feed 分页协议、缩略图 CDN 参数、缓存默认值和导航状态策略；furryfurry 原生库的完整语言组成/算法/实际调用；所有启动、帧率、内存、耗电和线上行为。

这些未知不影响 V4 采用 Compose 与清晰组件边界；它们限制的是对竞品实现/性能的归因。后续若仍坚持不运行目标，应继续保留未知，优先完成并测量 V4 自有实现，而不是用更多竞品文件名补齐想象中的架构。
