package com.nhom21.english_learning_app.data.remote.dto

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.core.network.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRoundSerializationTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun currentRoundUsesContractObject() {
        val round = """{"id":"05868c6c-eb17-532c-8b2e-20f381902cdc","roundNumber":1,"type":"learn"}"""
        assertEquals(HomeRoundDto("05868c6c-eb17-532c-8b2e-20f381902cdc", 1, "learn"), decode(round))
    }

    @Test
    fun currentTopicCanHaveNoRound() {
        assertNull(decode("null"))
    }

    private fun decode(roundJson: String): HomeRoundDto? {
        val response = json.decodeFromString<ApiResponse<HomeDataDto>>(
            """{"status":true,"message":"OK","data":{"currentTopic":{"id":"topic","round":$roundJson}},"error":null}"""
        )
        return requireNotNull(requireNotNull(response.data).currentTopic).round
    }
}
