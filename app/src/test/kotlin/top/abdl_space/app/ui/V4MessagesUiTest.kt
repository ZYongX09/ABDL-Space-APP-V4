package top.abdl_space.app.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import top.abdl_space.app.core.network.dto.ChatConversation
import top.abdl_space.app.core.network.dto.Notification
import top.abdl_space.app.core.network.dto.NotificationKind
import top.abdl_space.app.core.model.Account
import top.abdl_space.app.core.model.Status
import top.abdl_space.app.core.model.StatusVisibility

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class V4MessagesUiTest {
    @get:Rule val compose = createComposeRule()

    private fun messagesTab() {
        compose.onNode(hasText("消息") and hasAnyAncestor(hasTestTag(V4TestTags.BottomNavigation))).performClick()
    }

    @Test
    fun guestNeverRequestsAndSignedInConversationNavigatesWithoutPretendingToSend() {
        val session = mutableStateOf<V4SessionUiState>(V4SessionUiState.Guest)
        val selected = mutableListOf<V4MessageTab>()
        val state = V4MessagesUiState(
            conversations = V4ConversationsUiState(
                conversations = listOf(
                    ChatConversation(userId = "offline-peer", username = "离线测试联系人", lastMessage = "离线会话摘要")
                ),
                hasLoaded = true,
            ),
        )
        compose.setContent {
            V4App(
                session = session.value,
                reduceMotion = true,
                reduceTransparency = true,
                messagesState = state,
                onMessagesTabSelected = { selected += it },
            )
        }
        messagesTab()
        compose.runOnIdle { assertEquals(emptyList<V4MessageTab>(), selected) }
        compose.onAllNodesWithText("离线测试联系人").assertCountEquals(0)
        compose.runOnIdle { session.value = V4SessionUiState.SignedIn("离线本人") }
        compose.onNodeWithText("离线测试联系人").assertIsDisplayed().performClick()
        compose.onNodeWithText("会话详情迁移中", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("离线测试联系人").assertIsDisplayed()
    }

    @Test
    fun notificationsDoNotLeakCwBodyInTheirSummary() {
        val account = Account(id = "offline-author", username = "离线用户")
        val status = Status(
            id = "offline-cw-post", uri = "urn:offline:cw", createdAt = "2026-10-27T00:00:00Z",
            account = account, visibility = StatusVisibility.PUBLIC, sensitive = true,
            content = "<p>不能出现在通知摘要中的正文</p>",
        )
        compose.setContent {
            V4App(
                session = V4SessionUiState.SignedIn("离线本人"),
                reduceMotion = true,
                reduceTransparency = true,
                messagesState = V4MessagesUiState(
                    notifications = V4NotificationsUiState(
                        notifications = listOf(Notification("offline-notification", NotificationKind.Mention,
                            "mention", "2026-10-27T00:00:00Z", account, status)),
                        hasLoaded = true,
                    ),
                ),
            )
        }
        messagesTab()
        compose.onNodeWithText("通知", substring = false).performClick()
        compose.onNodeWithTag(V4MessagesTags.Notifications).performScrollToNode(hasText("敏感内容已折叠"))
        compose.onNodeWithText("敏感内容已折叠").assertIsDisplayed()
        compose.onAllNodesWithText("不能出现在通知摘要中的正文", substring = true).assertCountEquals(0)
    }

    @Test
    fun restoringSelectedNotificationsNotifiesNewEmptyViewModelInsteadOfStickingAtLoading() {
        val selected = mutableListOf<V4MessageTab>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            V4App(
                session = V4SessionUiState.SignedIn("离线本人"),
                reduceMotion = true,
                reduceTransparency = true,
                messagesState = V4MessagesUiState(),
                onMessagesTabSelected = { selected += it },
            )
        }
        messagesTab()
        compose.onNodeWithText("通知", substring = false).performClick()
        compose.waitForIdle()
        compose.runOnIdle { selected.clear() }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(V4MessageTab.Notifications), selected) }
    }
}
