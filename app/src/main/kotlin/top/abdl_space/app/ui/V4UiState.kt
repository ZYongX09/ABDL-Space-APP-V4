package top.abdl_space.app.ui

import top.abdl_space.app.core.model.Status

/** Only these three Mastodon timelines are wired; NBW is deliberately not a kind. */
enum class V4TimelineKind { Recommended, Following, Popular }

/**
 * Caller-owned, session-isolated read-only data. An absent map entry means not connected.
 * [hasLoaded] distinguishes an actual empty result from a first request not yet started.
 * Refresh and append failures are separate so a failed append never hides existing statuses.
 * Error strings must be safe user-facing summaries, not raw HTTP bodies or credentials.
 */
data class V4TimelineUiState(
    val statuses: List<Status> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val hasLoaded: Boolean = false,
)

/** Pass a non-null preference to control the theme from a persistent settings owner. */
enum class V4ThemePreference(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

/** UI-only session projection. This shell never creates or persists authentication tokens. */
sealed interface V4SessionUiState {
    data object Guest : V4SessionUiState
    data object Restoring : V4SessionUiState
    data class SignedIn(val displayName: String) : V4SessionUiState
}

internal enum class V4TopLevelDestination(val route: String, val label: String, val pillIndex: Int) {
    Home("home", "首页", 0),
    Discover("discover", "发现", 1),
    Messages("messages", "消息", 3),
    Me("me", "我的", 4);

    val startRoute: String get() = "$route/start"
    val searchRoute: String get() = "$route/search"
    val accountRoute: String get() = "$route/account"
    val featurePattern: String get() = "$route/feature/{feature}"
    fun featureRoute(feature: V4Feature): String = "$route/feature/${feature.id}"

    companion object {
        // Index 2 is an action, never a selectable destination.
        fun fromPillIndex(index: Int): V4TopLevelDestination? = entries.firstOrNull { it.pillIndex == index }
    }
}

internal enum class V4Feed(val label: String, val explanation: String) {
    Recommended("推荐", "登录后按需读取推荐时间线。这里不会展示示例帖子或虚构热度。"),
    Following("关注", "关注时间线需要登录，只展示应用层返回的真实社区动态。"),
    Popular("热门", "登录后按需读取热门时间线，不生成未经服务确认的热度。"),
    Nbw("NBW", "NBW 内容与分类服务尚未接入，不会在本轮读取或跨站发布内容。");

    val timelineKind: V4TimelineKind?
        get() = when (this) {
            Recommended -> V4TimelineKind.Recommended
            Following -> V4TimelineKind.Following
            Popular -> V4TimelineKind.Popular
            Nbw -> null
        }
}

enum class V4MessageTab(val label: String) {
    Direct("私信"), Notifications("通知"),
}

/** Honest local information architecture, not a substitute for server feature capabilities. */
internal enum class V4Feature(
    val id: String,
    val title: String,
    val description: String,
    val boundary: String,
    val requiresAccount: Boolean = false,
    val keywords: String = "",
) {
    Map("map", "交友地图", "在地图上探索社区成员与交友入口。", "地图 SDK、定位与权限流程尚未接入；本页不会读取或分享你的位置。", keywords = "地图 交友 附近"),
    FriendCard("friend-card", "扩列卡", "用扩列卡介绍自己，发现共同兴趣。", "广场、卡片详情与编辑服务尚未接入；没有虚构的成员卡片。", keywords = "扩列 交友 卡片"),
    Diaper("diaper", "纸尿裤", "浏览纸尿裤资料、评价与评分。", "商品资料、评价与榜单尚未接入；本页不提供未经核实的评分。", keywords = "纸尿裤 评价 评分"),
    Novel("novel", "小说", "社区书库、阅读与创作的入口。", "书库、阅读器与创作同步尚未接入；没有下载或展示任何作品。", keywords = "小说 阅读 书库 创作"),
    Nbw("nbw", "NBW", "NBW 能力状态与板块目录的入口。", "入口已读取真实 NBW 能力与板块目录（登录后）；内容浏览、发帖与 receipt 协议尚未接入，不会触发跨站发布。", keywords = "nbw 广场"),
    Scan("scan", "扫一扫", "扫描社区二维码与登录码的入口。", "相机、扫码解析与登录确认尚未接入；不会申请相机权限或自动打开链接。", keywords = "扫一扫 扫码 二维码"),
    City("city", "同城", "按地区探索社区时间线。", "地区选择与省份时间线尚未接入；不会推断你的城市或读取位置。", keywords = "同城 省份 城市 地区"),
    Topics("topics", "话题", "探索社区话题与讨论。", "话题广场与详情尚未接入；没有虚构的热门话题、人数或帖子。", keywords = "话题 标签 讨论"),
    Favorites("favorites", "我的收藏", "查看收藏的社区内容。", "收藏服务尚未接入，不能读取或修改线上收藏。", true),
    History("history", "浏览历史", "管理自己的浏览记录。", "浏览记录尚未接入，本轮没有采集帖子浏览历史。", true),
    Album("album", "我的相册", "社区相册与媒体管理入口。", "相册、上传与可见范围尚未接入；不会访问你的照片或上传文件。", true, "相册 照片 图片"),
    Verification("verification", "宝宝认证", "查看认证流程与结果。", "认证采集、提交与验真尚未接入；不会收集人脸或身份资料。", true, "宝宝 认证"),
    Sponsor("sponsor", "赞助中心", "社区赞助方案与记录入口。", "方案、订单与支付尚未接入；本轮没有购买或支付行为。", true, "赞助 支持"),
    MyNbw("my-nbw", "我的 NBW", "管理自己的 NBW 内容。", "账户关联、内容与发布记录尚未接入，不能代表真实的线上状态。", true),
    FriendRequest("friend-request", "交友申请", "交友申请与管理入口。", "申请发送与管理尚未接入；本页不会向任何成员发送请求。", true),
    Privacy("privacy", "隐私与安全", "账户隐私与安全设置入口。", "线上隐私设置尚未接入。本轮只有主题、减弱动画与透明效果是可修改的本地偏好。", true),
    NotificationSettings("notification-settings", "通知设置", "管理社区通知偏好。", "推送注册与通知设置尚未接入；本轮不会向服务端注册设备。", true),
    Announcement("announcement", "社区公告", "查看正式社区公告。", "公告服务尚未接入；这里没有编造的公告或未读数量。"),
    About("about", "关于 V4", "ABDL Space V4 · Compose 主壳开发中。", "当前可用的是导航、本地功能搜索、登录后的推荐 / 关注 / 热门只读时间线，以及登录后的真实文字发帖（公开可见范围）。密码登录、会话与偏好持久化由应用层接入；媒体发帖、互动、NBW 与 QQ / 邮箱登录界面仍未接入。"),
}

internal object V4FeatureCatalog {
    val discovery = listOf(V4Feature.Map, V4Feature.FriendCard, V4Feature.Diaper, V4Feature.Novel, V4Feature.Nbw, V4Feature.Scan, V4Feature.City, V4Feature.Topics)
    val basics = listOf(V4Feature.Favorites, V4Feature.History, V4Feature.Album, V4Feature.Verification)
    val community = listOf(V4Feature.Sponsor, V4Feature.FriendCard, V4Feature.MyNbw, V4Feature.Topics)

    fun fromId(id: String?): V4Feature? = V4Feature.entries.firstOrNull { it.id == id }

    fun search(query: String): List<V4Feature> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return V4Feature.entries.filter { feature ->
            val searchable = "${feature.title} ${feature.keywords}"
            terms.all { searchable.contains(it, ignoreCase = true) }
        }
    }
}

internal enum class V4DraftValidation { Empty, TooLong, Valid }

internal object V4DraftRules {
    // Keep rememberSaveable payloads bounded; this is a local editor limit, not an API contract.
    const val MaxCharacters = 10_000
    fun validate(text: String): V4DraftValidation = when {
        text.isBlank() -> V4DraftValidation.Empty
        text.length > MaxCharacters -> V4DraftValidation.TooLong
        else -> V4DraftValidation.Valid
    }

    fun needsExitConfirmation(text: String, savedDraft: String): Boolean = text != savedDraft
}

internal object V4TestTags {
    const val Home = "v4.home"
    const val Discover = "v4.discover"
    const val Messages = "v4.messages"
    const val Me = "v4.me"
    const val Search = "v4.search"
    const val SearchInput = "v4.search.input"
    const val SearchResults = "v4.search.results"
    const val Feature = "v4.feature"
    const val Settings = "v4.settings"
    const val Editor = "v4.editor"
    const val EditorInput = "v4.editor.input"
    const val EditorPublish = "v4.editor.publish"
    const val EditorSending = "v4.editor.sending"
    const val EditorError = "v4.editor.error"
    const val EditorGateSignIn = "v4.editor.gate.sign-in"
    const val DraftFeedback = "v4.editor.feedback"
    const val PublishFeedback = "v4.home.publish-feedback"
    const val BottomNavigation = "v4.bottom-navigation"
    const val NbwStatus = "v4.nbw.status"
    const val NbwCatalog = "v4.nbw.catalog"
    const val NbwRefresh = "v4.nbw.refresh"
}
