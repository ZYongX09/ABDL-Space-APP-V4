package top.abdl_space.app.ui

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Real NavHost/Compose interactions on the opaque API28 path; no service or production calls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class V4AppNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun start() {
        compose.setContent { V4App(reduceMotion = true, reduceTransparency = true) }
    }

    private fun selectTab(label: String) {
        val inBar = hasAnyAncestor(hasTestTag(V4TestTags.BottomNavigation))
        compose.onNode((hasText(label) or hasContentDescription(label)) and inBar).performClick()
    }

    private fun assertInput(tag: String, value: String) {
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)))
    }

    @Test
    fun tabSwitchRestoresFeatureBackStackAndSearchQuery() {
        start()
        selectTab("发现")
        compose.onNodeWithText("搜索社区与功能").performClick()
        compose.onNodeWithTag(V4TestTags.SearchInput).performTextInput("纸尿裤")
        compose.onNode(hasText("纸尿裤", substring = false) and hasAnyAncestor(hasTestTag(V4TestTags.SearchResults))).performClick()
        compose.onNodeWithTag(V4TestTags.Feature).assertIsDisplayed()
        compose.onNodeWithText("当前范围").assertIsDisplayed()

        selectTab("消息")
        compose.onNodeWithText("通知", substring = false).performClick()
        compose.onNodeWithText("登录后查看通知").assertIsDisplayed()
        selectTab("发现")
        compose.onNodeWithTag(V4TestTags.Feature).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        assertInput(V4TestTags.SearchInput, "纸尿裤")
        compose.onNodeWithContentDescription("清空搜索").performClick()
        assertInput(V4TestTags.SearchInput, "")
        compose.onNodeWithTag(V4TestTags.SearchResults).assertIsDisplayed()

        selectTab("消息")
        compose.onNodeWithText("通知", substring = false).assertIsSelected()
        compose.onNodeWithText("登录后查看通知").assertIsDisplayed()
    }

    @Test
    fun realListScrollPositionSurvivesTabSwitch() {
        start()
        selectTab("我的")
        compose.onNodeWithTag(V4TestTags.Me).performScrollToNode(hasText("我的卡片尚未接入"))
        compose.onNodeWithText("我的卡片尚未接入").assertIsDisplayed()
        val before = compose.onNodeWithText("我的卡片尚未接入").fetchSemanticsNode().boundsInRoot.top
        selectTab("首页")
        selectTab("我的")
        compose.onNodeWithText("我的卡片尚未接入").assertIsDisplayed()
        val after = compose.onNodeWithText("我的卡片尚未接入").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Tab restore must retain the actual list scroll offset", kotlin.math.abs(before - after) <= 1f)
    }

    @Test
    fun publishingIsAnActionAndSavedDraftIsRestoredWithoutSuccessClaim() {
        start()
        selectTab("发现")
        compose.onNodeWithContentDescription("发布", substring = false).performClick()
        compose.onNodeWithTag(V4TestTags.Editor).assertIsDisplayed()
        compose.onNodeWithText("保存本地草稿").performScrollTo().performClick()
        compose.onNodeWithText("请先输入非空白的正文。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(V4TestTags.EditorInput).performScrollTo().performTextInput("只在本地编辑的草稿")
        compose.onNodeWithText("发布", substring = false).performClick()
        compose.onNodeWithText("发布服务尚未接入").assertIsDisplayed()
        compose.onNodeWithText("继续编辑", substring = false).performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("保存修改后再退出？").assertIsDisplayed()
        compose.onNodeWithText("保存草稿并退出").performClick()
        compose.onNodeWithTag(V4TestTags.Discover).assertIsDisplayed()
        compose.onNodeWithContentDescription("发布", substring = false).performClick()
        assertInput(V4TestTags.EditorInput, "只在本地编辑的草稿")
        compose.onAllNodesWithText("发布成功", substring = true).assertCountEquals(0)
    }

    @Test
    fun saveableRestorationRetainsThemeSelectedTabAndDraftText() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { V4App(reduceMotion = true, reduceTransparency = true) }
        selectTab("我的")
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("深色", substring = false).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(V4TestTags.Settings).assertIsDisplayed()
        compose.onNodeWithText("深色", substring = false).assertIsSelected()

        selectTab("首页")
        compose.onNodeWithText("热门", substring = false).performClick()
        selectTab("我的")
        selectTab("首页")
        compose.onNodeWithText("热门", substring = false).assertIsSelected()
        compose.onNodeWithContentDescription("发布", substring = false).performClick()
        compose.onNodeWithTag(V4TestTags.EditorInput).performScrollTo().performTextInput("恢复中的未保存文字")
        restoration.emulateSavedInstanceStateRestore()
        assertInput(V4TestTags.EditorInput, "恢复中的未保存文字")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("不保存并退出").performClick()
        compose.onNodeWithTag(V4TestTags.Home).assertIsDisplayed()
        compose.onNodeWithText("热门", substring = false).assertIsSelected()
    }
}
