package top.abdl_space.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device layout check at 200% type. This does not claim full TalkBack or screenshot coverage. */
@RunWith(AndroidJUnit4::class)
class V4LargeTypeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun entryAndEditorRemainReachableAtDoubleFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                V4App(reduceMotion = true, reduceTransparency = true)
            }
        }
        compose.onNode(hasText("发现") and hasAnyAncestor(hasTestTag(V4TestTags.BottomNavigation))).performClick()
        compose.onNodeWithText("交友地图", substring = false).assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag(V4TestTags.Feature).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("发布", substring = false).assertHasClickAction().performClick()
        compose.onNodeWithTag(V4TestTags.EditorInput).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("保存本地草稿", substring = false).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }
}
