package com.nhom21.english_learning_app.core.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathGeometryTest {
    @Test
    fun firstAndLastNodesStayCentered() {
        assertEquals(0.dp, PathGeometry.xOffset(1, 0, 100.dp))
        assertEquals(0.dp, PathGeometry.xOffset(5, 0, 100.dp))
    }

    @Test
    fun topicParityReversesWaveDirection() {
        val even = PathGeometry.xOffset(3, 0, 100.dp)
        val odd = PathGeometry.xOffset(3, 1, 100.dp)
        assertEquals(even, -odd)
        assertTrue(even > 0.dp)
    }

    @Test
    fun invalidRoundIndexIsSafeAndCentered() {
        assertEquals(0.dp, PathGeometry.xOffset(0, 0, 100.dp))
        assertEquals(0.dp, PathGeometry.xOffset(6, 0, 100.dp))
    }
}
