package top.abdl_space.app.ui

import android.app.Application
import android.text.Html
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import top.abdl_space.app.core.model.Account
import top.abdl_space.app.core.model.MediaAttachment
import top.abdl_space.app.core.model.MediaType
import top.abdl_space.app.core.model.Status
import top.abdl_space.app.core.model.StatusVisibility

/** Explicit offline UI fixtures, never supplied by production V4App or a real repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class V4TimelineUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun offlineFixtureRendersPlainTextAndCwAndKeepsExistingRowsOnAppendFailure() {
        val fixture = statusFixture()
        val expectedBody = "离线正文 & 纯文本"
        assertEquals("The fixture HTML must decode to the exact displayed plain text", expectedBody,
            Html.fromHtml(fixture.content, Html.FROM_HTML_MODE_COMPACT).toString().trim())
        var timeline by mutableStateOf(V4TimelineUiState(statuses = listOf(fixture), hasLoaded = true, hasMore = true, loadMoreError = "离线测试：下一页失败"))
        var appendRequests = 0
        var refreshRequests = 0
        compose.setContent {
            V4App(
                session = V4SessionUiState.SignedIn("离线测试账号"), reduceMotion = true, reduceTransparency = true,
                timelineState = mapOf(V4TimelineKind.Recommended to timeline),
                onLoadMore = { appendRequests++ }, onTimelineRefresh = { refreshRequests++ },
            )
        }
        val list = compose.onNodeWithTag("v4.feed.Recommended")
        list.performScrollToNode(hasText("离线测试作者"))
        compose.onNodeWithTag("v4.status.${fixture.id}").assertExists()
        compose.onNodeWithText("离线测试作者").assertIsDisplayed()
        compose.onAllNodesWithText(expectedBody, substring = true).assertCountEquals(0)
        bringTimelineTextIntoView("展开正文")
        compose.onNodeWithText("展开正文").assertIsDisplayed().performClick()
        compose.onNodeWithText("收起正文").assertExists()
        bringTimelineTextIntoView(expectedBody)
        compose.onNodeWithText(expectedBody).assertIsDisplayed()
        bringTimelineTextIntoView("转发 2 · 评论 3 · 点赞 7（只读）")
        compose.onNodeWithText("转发 2 · 评论 3 · 点赞 7（只读）").assertIsDisplayed()
        compose.onNodeWithText("1 个媒体附件", substring = true).assertExists()
        bringTimelineTextIntoView("重试加载更多")
        compose.onNodeWithText("重试加载更多").performClick()
        compose.onNodeWithText("重试加载更多").performClick()
        compose.runOnIdle { assertEquals(1, appendRequests) }

        compose.runOnIdle { timeline = timeline.copy(isLoadingMore = true, loadMoreError = null) }
        bringTimelineTextIntoView("正在加载更多")
        compose.onNodeWithText("正在加载更多").assertIsDisplayed()
        compose.runOnIdle { timeline = timeline.copy(isLoadingMore = false, hasMore = false) }
        bringTimelineTextIntoView("刷新时间线")
        compose.onNodeWithText("刷新时间线").performClick()
        compose.onNodeWithText("刷新时间线").performClick()
        compose.runOnIdle { assertEquals(1, refreshRequests) }
    }

    @Test
    fun callbacksGateGuestsAndReissueSelectionAfterRestoringAnEmptyCache() {
        var session by mutableStateOf<V4SessionUiState>(V4SessionUiState.Guest)
        var unrelated by mutableIntStateOf(0)
        var timelines by mutableStateOf(V4TimelineKind.entries.associateWith { V4TimelineUiState(hasLoaded = true) })
        val selected = mutableListOf<V4TimelineKind>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            V4App(
                session = session,
                reduceMotion = true, reduceTransparency = true,
                themePreference = if (unrelated % 2 == 0) V4ThemePreference.Dark else V4ThemePreference.Light,
                timelineState = timelines,
                onTimelineSelected = { selected += it },
            )
        }
        compose.runOnIdle { assertEquals(emptyList<V4TimelineKind>(), selected); session = V4SessionUiState.SignedIn("离线测试账号") }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(V4TimelineKind.Recommended), selected); unrelated++ }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(V4TimelineKind.Recommended), selected) }
        compose.onNodeWithText("关注", substring = false).performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf(V4TimelineKind.Recommended, V4TimelineKind.Following), selected)
            // Model a new ViewModel after process death: Pager restores, repository cache does not.
            timelines = V4TimelineKind.entries.associateWith { V4TimelineUiState(hasLoaded = false) }
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(V4TimelineKind.Recommended, V4TimelineKind.Following, V4TimelineKind.Following), selected) }
        compose.onNodeWithText("NBW", substring = false).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, selected.size) }
        compose.onNodeWithText("关注", substring = false).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(V4TimelineKind.Following, selected.last()); assertEquals(4, selected.size) }
        selectRoot("发现")
        selectRoot("首页")
        compose.waitForIdle()
        // Re-entering Home may notify again; the caller owns hasLoaded/in-flight idempotency.
        compose.runOnIdle { assertEquals(V4TimelineKind.Following, selected.last()); assertEquals(5, selected.size) }
        compose.onNodeWithText("热门", substring = false).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(V4TimelineKind.Popular, selected.last()); assertEquals(6, selected.size) }
    }

    private fun bringTimelineTextIntoView(text: String) {
        val list = compose.onNodeWithTag("v4.feed.Recommended")
        list.performScrollToNode(hasText(text))
        // The card may still be partially under the floating bar. Keep nudging until the target
        // text sits fully inside the unobscured viewport; each scroll is a real Lazy scroll.
        val listBounds = list.fetchSemanticsNode().boundsInRoot
        val bottomBarTop = compose.onNodeWithTag(V4TestTags.BottomNavigation).fetchSemanticsNode().boundsInRoot.top
        val visibleBottom = minOf(listBounds.bottom, bottomBarTop)
        repeat(10) {
            val target = compose.onNodeWithText(text).fetchSemanticsNode()
            val targetCenterY = target.positionInRoot.y + target.size.height / 2f
            val middle = (listBounds.top + visibleBottom) / 2f
            if (target.positionInRoot.y >= listBounds.top && target.positionInRoot.y + target.size.height <= visibleBottom &&
                kotlin.math.abs(targetCenterY - middle) <= 1f
            ) return
            // A real drag on the list itself, expressed without gesture-helper imports.
            list.performTouchInput {
                val delta = if (targetCenterY > middle) Offset(0f, -80f) else Offset(0f, 80f)
                down(center)
                repeat(8) { moveBy(delta / 8f) }
                up()
            }
            compose.waitForIdle()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun selectRoot(label: String) {
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(V4TestTags.BottomNavigation))).performClick()
    }

    private fun statusFixture() = Status(
        id = "offline-fixture-status-1", uri = "urn:offline-fixture:status:1", createdAt = "2026-10-26T09:30:00Z",
        account = Account(id = "offline-fixture-author-1", username = "offline_fixture", displayName = "离线测试作者"),
        visibility = StatusVisibility.PUBLIC,
        content = "<p>离线正文 &amp; <strong>纯文本</strong></p>",
        sensitive = true, spoilerText = "<p>离线测试内容提示</p>",
        mediaAttachments = listOf(MediaAttachment(id = "offline-fixture-media-1", type = MediaType.IMAGE)),
        reblogsCount = 2, repliesCount = 3, favouritesCount = 7,
    )
}
