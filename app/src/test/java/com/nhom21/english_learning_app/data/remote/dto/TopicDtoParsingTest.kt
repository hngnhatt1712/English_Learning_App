package com.nhom21.english_learning_app.data.remote.dto

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.core.network.ListData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TopicDtoParsingTest {

    private lateinit var json: Json

    @Before
    fun setUp() {
        json = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = false
            coerceInputValues = true
            classDiscriminator = "type"
        }
    }

    @Test
    fun `parse GET topics response with envelope and unknown keys succeeds`() {
        val rawJson = """
            {
              "status": true,
              "message": "OK",
              "data": {
                "items": [
                  {
                    "id": "11111111-2222-3333-4444-555555555555",
                    "levelId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                    "name": "Greetings",
                    "description": "Say hello and goodbye",
                    "orderIndex": 1,
                    "status": "unlocked",
                    "stars": 7,
                    "maxStars": 15,
                    "roundsCompleted": 2,
                    "roundsTotal": 5,
                    "progressPercent": 40.0,
                    "themeKey": "food",
                    "extraFutureFieldFromServer": "ignored_value"
                  }
                ],
                "extraField": 123
              },
              "error": null
            }
        """.trimIndent()

        val response = json.decodeFromString<ApiResponse<ListData<TopicDto>>>(rawJson)

        assertTrue(response.status)
        assertEquals("OK", response.message)
        assertNull(response.error)
        assertNotNull(response.data)

        val items = response.data!!.items
        assertEquals(1, items.size)
        val topic = items[0]
        assertEquals("11111111-2222-3333-4444-555555555555", topic.id)
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", topic.levelId)
        assertEquals("Greetings", topic.name)
        assertEquals("food", topic.themeKey)
        assertEquals("unlocked", topic.status)
        assertEquals(7, topic.stars)
        assertEquals(15, topic.maxStars)
        assertEquals(2, topic.roundsCompleted)
        assertEquals(5, topic.roundsTotal)
        assertEquals(40.0, topic.progressPercent, 0.001)
    }

    @Test
    fun `parse GET topic detail response with lessons succeeds`() {
        val rawJson = """
            {
              "status": true,
              "message": "OK",
              "data": {
                "id": "11111111-2222-3333-4444-555555555555",
                "levelId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "name": "Greetings",
                "description": null,
                "orderIndex": 1,
                "status": "unlocked",
                "stars": 0,
                "maxStars": 15,
                "roundsCompleted": 0,
                "roundsTotal": 5,
                "progressPercent": 0.0,
                "themeKey": "default",
                "lessons": [
                  {
                    "id": "22222222-3333-4444-5555-666666666666",
                    "topicId": "11111111-2222-3333-4444-555555555555",
                    "title": "Lesson 1: Hello",
                    "description": null,
                    "orderIndex": 1,
                    "futureMeta": {}
                  }
                ]
              },
              "error": null
            }
        """.trimIndent()

        val response = json.decodeFromString<ApiResponse<TopicDetailDto>>(rawJson)

        assertTrue(response.status)
        val data = response.data
        assertNotNull(data)
        assertEquals("11111111-2222-3333-4444-555555555555", data!!.id)
        assertNull(data.description)
        assertEquals(1, data.lessons.size)
        assertEquals("22222222-3333-4444-5555-666666666666", data.lessons[0].id)
        assertEquals("Lesson 1: Hello", data.lessons[0].title)
    }

    @Test
    fun `parse GET lesson response with empty rounds (Sprint 4 shape) succeeds`() {
        val rawJson = """
            {
              "status": true,
              "message": "OK",
              "data": {
                "id": "22222222-3333-4444-5555-666666666666",
                "topicId": "11111111-2222-3333-4444-555555555555",
                "title": "Lesson 1: Hello",
                "description": "First steps",
                "orderIndex": 1,
                "rounds": []
              },
              "error": null
            }
        """.trimIndent()

        val response = json.decodeFromString<ApiResponse<LessonDetailDto>>(rawJson)

        assertTrue(response.status)
        val data = response.data
        assertNotNull(data)
        assertEquals("22222222-3333-4444-5555-666666666666", data!!.id)
        assertTrue(data.rounds.isEmpty())
    }

    @Test
    fun `parse GET lesson response with 5 rounds succeeds`() {
        val rawJson = """
            {
              "status": true,
              "message": "OK",
              "data": {
                "id": "22222222-3333-4444-5555-666666666666",
                "topicId": "11111111-2222-3333-4444-555555555555",
                "title": "Lesson 1: Hello",
                "description": null,
                "orderIndex": 1,
                "rounds": [
                  {
                    "id": "r-1",
                    "lessonId": "22222222-3333-4444-5555-666666666666",
                    "roundNumber": 1,
                    "type": "learn",
                    "title": "Flashcards",
                    "status": "completed",
                    "stars": 3,
                    "bestScorePercent": 100.0,
                    "attempts": 1,
                    "totalQuestions": 8
                  },
                  {
                    "id": "r-2",
                    "roundNumber": 2,
                    "type": "recognition",
                    "status": "unlocked",
                    "stars": 0,
                    "bestScorePercent": null,
                    "attempts": 0,
                    "totalQuestions": 10
                  }
                ]
              },
              "error": null
            }
        """.trimIndent()

        val response = json.decodeFromString<ApiResponse<LessonDetailDto>>(rawJson)

        val data = response.data
        assertNotNull(data)
        assertEquals(2, data!!.rounds.size)
        assertEquals("r-1", data.rounds[0].id)
        assertEquals(100.0, data.rounds[0].bestScorePercent!!, 0.001)
        assertNull(data.rounds[1].bestScorePercent)
        assertEquals("r-2", data.rounds[1].id)
    }
}

