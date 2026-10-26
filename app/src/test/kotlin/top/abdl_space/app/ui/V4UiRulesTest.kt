package top.abdl_space.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure rules only; these are not substitutes for NavHost/device tests. */
class V4UiRulesTest {
    @Test
    fun publishingIsNotATopLevelDestination() {
        assertEquals(V4TopLevelDestination.Home, V4TopLevelDestination.fromPillIndex(0))
        assertEquals(V4TopLevelDestination.Discover, V4TopLevelDestination.fromPillIndex(1))
        assertNull(V4TopLevelDestination.fromPillIndex(2))
        assertEquals(V4TopLevelDestination.Messages, V4TopLevelDestination.fromPillIndex(3))
        assertEquals(V4TopLevelDestination.Me, V4TopLevelDestination.fromPillIndex(4))
        assertNull(V4TopLevelDestination.fromPillIndex(-1))
        assertNull(V4TopLevelDestination.fromPillIndex(5))
    }

    @Test
    fun featureSearchHandlesWhitespaceCaseAndMultipleTerms() {
        assertTrue(V4FeatureCatalog.search(" \n\t ").isEmpty())
        assertEquals(listOf(V4Feature.Album), V4FeatureCatalog.search("  相册  "))
        assertTrue(V4FeatureCatalog.search(" nBw ").contains(V4Feature.Nbw))
        assertEquals(listOf(V4Feature.Map), V4FeatureCatalog.search("地图  附近"))
        assertTrue(V4FeatureCatalog.search("一个尚不存在的功能").isEmpty())
    }

    @Test
    fun featureLookupRejectsUnknownRoutes() {
        assertEquals(V4Feature.Scan, V4FeatureCatalog.fromId("scan"))
        assertNull(V4FeatureCatalog.fromId(null))
        assertNull(V4FeatureCatalog.fromId("not-an-existing-feature"))
    }

    @Test
    fun draftValidationRejectsWhitespaceAndEnforcesLocalStateLimit() {
        assertEquals(V4DraftValidation.Empty, V4DraftRules.validate(""))
        assertEquals(V4DraftValidation.Empty, V4DraftRules.validate(" \n\t\u3000"))
        assertEquals(V4DraftValidation.Valid, V4DraftRules.validate("第一份本地草稿"))
        assertEquals(V4DraftValidation.Valid, V4DraftRules.validate("字".repeat(V4DraftRules.MaxCharacters)))
        assertEquals(V4DraftValidation.TooLong, V4DraftRules.validate("字".repeat(V4DraftRules.MaxCharacters + 1)))
    }

    @Test
    fun exitConfirmationProtectsEditsAndDeletionWithoutTrimmingContent() {
        assertFalse(V4DraftRules.needsExitConfirmation("", ""))
        assertFalse(V4DraftRules.needsExitConfirmation(" 已保存 ", " 已保存 "))
        assertTrue(V4DraftRules.needsExitConfirmation("未保存", ""))
        assertTrue(V4DraftRules.needsExitConfirmation("", "已保存"))
        assertTrue(V4DraftRules.needsExitConfirmation("已保存 ", "已保存"))
    }
}
