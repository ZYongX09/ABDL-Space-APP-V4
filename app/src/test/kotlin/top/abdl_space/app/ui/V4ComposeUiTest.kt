package top.abdl_space.app.ui

import android.app.Application
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import top.abdl_space.app.V4ComposeUiState
import top.abdl_space.app.composeFailureMessage
import top.abdl_space.app.core.network.NetworkFailureKind

/**
 * Real V4App/V4EditorScreen posting wiring on the API 28 opaque path. The send backend is
 * scripted at V4App's parameter seam (the same boundary MainActivity binds V4ComposeViewModel
 * to): the production repository behind that seam is exercised over real HTTP in
 * :core:data ComposeRepositoryTest. No network and no fabricated success claims here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class V4ComposeUiTest {
    @get:Rule val compose = createComposeRule()

    private fun assertInput(tag: String, value: String) {
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)))
    }

    @Test
    fun publishSendsTheEditorTextAndAFailureKeepsTheDraftForRetry() {
        val composeState = mutableStateOf(V4ComposeUiState())
        var submitted: String? = null
        compose.setContent {
            V4App(
                session = V4SessionUiState.SignedIn("测试用户"),
                reduceMotion = true,
                reduceTransparency = true,
                compose = composeState.value,
                onComposePublish = { text ->
                    submitted = text
                    composeState.value = V4ComposeUiState(sending = true)
                },
            )
        }
        compose.onNodeWithContentDescription("发布", substring = false).performClick()
        compose.onNodeWithTag(V4TestTags.Editor).assertIsDisplayed()
        compose.onNodeWithTag(V4TestTags.EditorInput).performScrollTo().performTextInput("准备真实发送的文字")
        compose.onNodeWithTag(V4TestTags.EditorPublish).performClick()
        assertEquals("准备真实发送的文字", submitted)
        compose.onNodeWithTag(V4TestTags.EditorSending).assertIsDisplayed()
        compose.onNodeWithTag(V4TestTags.EditorPublish).assertIsNotEnabled()

        composeState.value = V4ComposeUiState(error = composeFailureMessage(NetworkFailureKind.SERVER))
        compose.onNodeWithTag(V4TestTags.EditorError).assertIsDisplayed()
        compose.onNodeWithText(composeFailureMessage(NetworkFailureKind.SERVER)).assertIsDisplayed()
        // The draft survives the failure untouched and the retry button comes back.
        assertInput(V4TestTags.EditorInput, "准备真实发送的文字")
        compose.onNodeWithTag(V4TestTags.EditorPublish).assertIsEnabled()
    }

    @Test
    fun aConfirmedPublishClosesTheEditorClearsTheDraftAndFeedsBackOnHome() {
        val composeState = mutableStateOf(V4ComposeUiState())
        compose.setContent {
            V4App(
                session = V4SessionUiState.SignedIn("测试用户"),
                reduceMotion = true,
                reduceTransparency = true,
                compose = composeState.value,
                onComposePublish = { composeState.value = V4ComposeUiState(sending = true) },
                onPublishedPostConsumed = { id ->
                    assertEquals("offline-published-post", id)
                    composeState.value = composeState.value.copy(publishedId = null)
                },
            )
        }
        compose.onNodeWithContentDescription("发布", substring = false).performClick()
        compose.onNodeWithTag(V4TestTags.Editor).assertIsDisplayed()
        compose.onNodeWithTag(V4TestTags.EditorInput).performScrollTo().performTextInput("会被真实发布的帖子")
        compose.onNodeWithText("保存本地草稿").performScrollTo().performClick()
        compose.onNodeWithTag(V4TestTags.EditorPublish).performClick()

        composeState.value = V4ComposeUiState(
            feedback = "发帖成功。首页时间线刷新后可以看到这条帖子。",
            publishedId = "offline-published-post",
        )
        compose.onNodeWithTag(V4TestTags.Home).assertIsDisplayed()
        compose.onNodeWithTag(V4TestTags.PublishFeedback).assertIsDisplayed()
        compose.onNodeWithText("发帖成功").assertIsDisplayed()

        // The saved draft is gone after success, so the post cannot be accidentally duplicated.
        compose.onNodeWithContentDescription("发布", substring = false).performClick()
        compose.onNodeWithTag(V4TestTags.Editor).assertIsDisplayed()
        assertInput(V4TestTags.EditorInput, "")
    }

    @Test
    fun staleHomeFeedbackDoesNotCloseANewEditorAfterStateRestoration() {
        val state = mutableStateOf(V4ComposeUiState(feedback = "前一次发布已完成"))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            V4App(
                session = V4SessionUiState.SignedIn("测试用户"),
                reduceMotion = true,
                reduceTransparency = true,
                compose = state.value,
            )
        }
        compose.onNodeWithContentDescription("发布", substring = false).performClick()
        compose.onNodeWithTag(V4TestTags.EditorInput).performScrollTo().performTextInput("不能被旧提示清除的新草稿")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(V4TestTags.Editor).assertIsDisplayed()
        assertInput(V4TestTags.EditorInput, "不能被旧提示清除的新草稿")
    }

    @Test
    fun systemBackWhileSendingDoesNotPopOrLoseEditorText() {
        val composeState = mutableStateOf(V4ComposeUiState())
        var backOwner: OnBackPressedDispatcherOwner? = null
        compose.setContent {
            backOwner = LocalContext.current as OnBackPressedDispatcherOwner
            V4App(
                session = V4SessionUiState.SignedIn("测试用户"),
                reduceMotion = true,
                reduceTransparency = true,
                compose = composeState.value,
                onComposePublish = { composeState.value = V4ComposeUiState(sending = true) },
            )
        }
        compose.onNodeWithContentDescription("发布", substring = false).performClick()
        compose.onNodeWithTag(V4TestTags.EditorInput).performScrollTo().performTextInput("发送中保留的正文")
        compose.onNodeWithTag(V4TestTags.EditorPublish).performClick()
        compose.onNodeWithTag(V4TestTags.EditorPublish).assertIsNotEnabled()
        compose.runOnIdle { checkNotNull(backOwner).onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag(V4TestTags.Editor).assertIsDisplayed()
        assertInput(V4TestTags.EditorInput, "发送中保留的正文")
    }

    @Test
    fun feedbackDismissalIsExplicit() {
        val composeState = mutableStateOf(V4ComposeUiState(feedback = "发帖成功。首页时间线刷新后可以看到这条帖子。"))
        var consumed = false
        compose.setContent {
            V4App(
                session = V4SessionUiState.SignedIn("测试用户"),
                reduceMotion = true,
                reduceTransparency = true,
                compose = composeState.value,
                onComposeFeedbackConsumed = {
                    consumed = true
                    composeState.value = V4ComposeUiState()
                },
            )
        }
        compose.onNodeWithTag(V4TestTags.PublishFeedback).assertIsDisplayed()
        compose.onNodeWithText("知道了", substring = false).performClick()
        assertEquals(true, consumed)
        compose.onAllNodesWithText("发帖成功", substring = true).assertCountEquals(0)
    }
}
