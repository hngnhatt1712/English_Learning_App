package com.nhom21.english_learning_app.features.lesson

import com.nhom21.english_learning_app.core.ui.components.PathNodeState
import com.nhom21.english_learning_app.domain.model.ContentStatus
import com.nhom21.english_learning_app.domain.model.RoundSummary
import com.nhom21.english_learning_app.domain.model.RoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildRoundNodesTest {
    @Test
    fun unlockedTopicWithoutRoundDataBuildsCurrentThenLockedPlaceholders() {
        val nodes = buildRoundNodes("topic", ContentStatus.UNLOCKED, emptyList(), 5)
        assertEquals(5, nodes.size)
        assertEquals(PathNodeState.Current, nodes.first().state)
        assertEquals(PathNodeState.Locked, nodes[1].state)
        assertNull(nodes.first().roundId)
        assertEquals(true, nodes.last().isChest)
    }

    @Test
    fun completedTopicWithoutRoundDataBuildsFiveCompletedNodes() {
        val nodes = buildRoundNodes("topic", ContentStatus.COMPLETED, emptyList(), 5)
        assertEquals(5, nodes.count { it.state == PathNodeState.Completed })
    }

    @Test
    fun serverRoundStateAndStarsAreMappedAndClamped() {
        val rounds = listOf(
            RoundSummary("r1", "lesson", 1, RoundType.LEARN, null, ContentStatus.COMPLETED, 9, 100.0, 1, 5),
            RoundSummary("r2", "lesson", 2, RoundType.RECOGNITION, null, ContentStatus.UNLOCKED, -1, null, 0, 5),
        )
        val nodes = buildRoundNodes("topic", ContentStatus.UNLOCKED, rounds, 5)
        assertEquals(PathNodeState.Completed, nodes[0].state)
        assertEquals(3, nodes[0].stars)
        assertEquals(PathNodeState.Current, nodes[1].state)
        assertEquals(0, nodes[1].stars)
        assertEquals(RoundKind.RECOGNITION, nodes[1].kind)
    }

    @Test
    fun unknownStatusIsLocked() {
        val nodes = buildRoundNodes("topic", ContentStatus.UNKNOWN, emptyList(), 5)
        assertEquals(5, nodes.count { it.state == PathNodeState.Locked })
        assertEquals(UnlockHint.PreviousTopic, nodes.first().unlockHint)
    }
}
