package top.abdl_space.app.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Theme

/** Host-side semantics/layout tests; these do not certify GPU rendering of the API33 backdrop. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], qualifiers = "zh-rCN-w360dp-h800dp")
@LooperMode(LooperMode.Mode.PAUSED)
class V4PillNavigationBarTest {
    @get:Rule val compose = createComposeRule()
    private val tabs = listOf("首页", "发现", "消息", "我的")
    private val icon = ImageVector.Builder(name = "TestNav", defaultWidth = 24.dp,
        defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).build()
    private val items = listOf(V4PillNavItem(icon, tabs[0]), V4PillNavItem(icon, tabs[1]), null,
        V4PillNavItem(icon, tabs[2]), V4PillNavItem(icon, tabs[3]))

    @Test
    fun `tabs are selectable single announcements and publish is a separate button`() {
        val selected = mutableIntStateOf(0)
        var selectionCalls = 0
        var publishCalls = 0
        var longCalls = 0
        compose.setContent {
            V4Theme(darkTheme = true) {
                V4PillNavigationBar(
                    items = items,
                    selectedIndex = selected.intValue,
                    onSelect = { selected.intValue = it; selectionCalls++ },
                    onPublishClick = { publishCalls++ },
                    onPublishLongClick = { longCalls++ },
                    modifier = Modifier.testTag("nav"),
                )
            }
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertCountEquals(4)
        tabs.forEach { label ->
            compose.onAllNodesWithText(label).assertCountEquals(1)
            // Icons must not repeat the same content description as the visible text.
            compose.onAllNodesWithContentDescription(label, useUnmergedTree = true).assertCountEquals(0)
            compose.onNodeWithText(label).assertHasClickAction()
                .assertWidthIsAtLeast(V4Dimens.minimumTouchTarget)
                .assertHeightIsAtLeast(V4Dimens.minimumTouchTarget)
        }
        compose.onNodeWithText("首页").assertIsSelected()
        compose.onNodeWithText("消息").performClick().assertIsSelected()
        compose.onNodeWithText("首页").assertIsNotSelected()
        compose.runOnIdle { assertEquals(3, selected.intValue); assertEquals(1, selectionCalls) }
        compose.onAllNodesWithContentDescription("发布").assertCountEquals(1)
        compose.onNodeWithContentDescription("发布")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertWidthIsAtLeast(V4Dimens.minimumTouchTarget)
            .assertHeightIsAtLeast(V4Dimens.minimumTouchTarget)
            .performClick()
            .performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, publishCalls)
            assertEquals(1, longCalls)
            assertEquals(1, selectionCalls)
        }
        // TalkBack's long-click action follows the same route, without triggering click.
        compose.onNodeWithContentDescription("发布").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.runOnIdle { assertEquals(2, longCalls); assertEquals(1, publishCalls) }
    }

    @Test
    fun `legacy invocation has no phantom long click and grows for 200 percent text`() {
        compose.setContent {
            V4Theme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    Box(Modifier.fillMaxWidth()) {
                        // Only original parameters: compatibility is compiled and exercised.
                        V4PillNavigationBar(items, 0, {}, Modifier.testTag("legacy"))
                    }
                }
            }
        }
        compose.onNodeWithTag("legacy").assertHeightIsAtLeast(V4Dimens.navBarHeight)
        tabs.forEach {
            compose.onNodeWithText(it).assertWidthIsAtLeast(V4Dimens.minimumTouchTarget)
                .assertHeightIsAtLeast(V4Dimens.minimumTouchTarget)
        }
        compose.onNodeWithContentDescription("发布").assert(
            SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick),
        )
    }
}
