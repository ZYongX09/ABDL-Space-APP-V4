package top.abdl_space.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.abdl_space.app.core.network.NetworkFailureKind

/**
 * The error-copy contract: composeFailureMessage takes only the classified kind — raw server
 * text has no path into user-visible posting errors. Every branch states that the draft is kept.
 */
class V4ComposeMessagesTest {
    @Test
    fun everyFailureKindTellsTheUserTheDraftIsKept() {
        for (kind in NetworkFailureKind.entries) {
            val message = composeFailureMessage(kind)
            assertTrue("kind=$kind must keep the draft for retry", message.contains("草稿已保留"))
        }
    }

    @Test
    fun transportAndServerFailuresDoNotClaimThatTheWriteNeverHappened() {
        for (kind in listOf(NetworkFailureKind.NETWORK, NetworkFailureKind.TIMEOUT, NetworkFailureKind.SERVER, NetworkFailureKind.PROTOCOL)) {
            assertTrue("kind=$kind cannot establish whether the server committed", composeFailureMessage(kind).contains("结果未知"))
        }
    }

    @Test
    fun classifiedKindsGetSpecificHonestCopy() {
        assertEquals(
            "登录状态无法验证，帖子未发出。请重新登录后重试；草稿已保留。",
            composeFailureMessage(NetworkFailureKind.UNAUTHORIZED),
        )
        assertEquals(
            "服务器没有接受这条内容（校验未通过）。请调整内容后重试；草稿已保留。",
            composeFailureMessage(NetworkFailureKind.VALIDATION),
        )
        assertEquals(
            "发布过于频繁，请稍等片刻再重试；草稿已保留。",
            composeFailureMessage(NetworkFailureKind.RATE_LIMITED),
        )
        assertEquals(
            "服务器返回了无法确认的响应，发布结果未知。请稍后在首页时间线核实，避免重复发布；草稿已保留。",
            composeFailureMessage(NetworkFailureKind.PROTOCOL),
        )
        assertEquals(
            "连接超时，发布结果未知。请稍后在首页时间线核实，避免重复发布；草稿已保留。",
            composeFailureMessage(NetworkFailureKind.TIMEOUT),
        )
    }
}
