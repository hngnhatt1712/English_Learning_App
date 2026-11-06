package com.nhom21.english_learning_app.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiErrorParserTest {

    private lateinit var parser: ApiErrorParser

    @Before
    fun setUp() {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            coerceInputValues = true
        }
        parser = ApiErrorParser(json)
    }

    @Test
    fun parse_fullEnvelopeError_returnsMappedFailure() {
        val body = """
            {
              "status": false,
              "message": "Validation Error",
              "data": null,
              "error": {
                "type": "email-taken",
                "detail": "Email already registered",
                "traceId": "trace-123",
                "errors": null
              }
            }
        """.trimIndent()

        val failure = parser.parse(body, 409)

        assertEquals(ApiErrorType.EMAIL_TAKEN, failure.type)
        assertEquals("Email already registered", failure.message)
        assertTrue(failure.fieldErrors.isEmpty())
        assertEquals(409, failure.httpStatus)
    }

    @Test
    fun parse_422ValidationErrorWithErrorsArray_returnsFieldErrors() {
        val body = """
            {
              "status": false,
              "message": "Validation Error",
              "data": null,
              "error": {
                "type": "validation-error",
                "detail": "One or more fields failed validation",
                "traceId": "trace-456",
                "errors": [
                  { "field": "password", "message": "Must be at least 8 characters", "code": "MIN_LENGTH" },
                  { "field": "email", "message": "Invalid email address", "code": "INVALID_FORMAT" }
                ]
              }
            }
        """.trimIndent()

        val failure = parser.parse(body, 422)

        assertEquals(ApiErrorType.VALIDATION_ERROR, failure.type)
        assertEquals("One or more fields failed validation", failure.message)
        assertEquals(2, failure.fieldErrors.size)
        assertEquals("password", failure.fieldErrors[0].field)
        assertEquals("Must be at least 8 characters", failure.fieldErrors[0].message)
        assertEquals("email", failure.fieldErrors[1].field)
        assertEquals("Invalid email address", failure.fieldErrors[1].message)
        assertEquals(422, failure.httpStatus)
    }

    @Test
    fun parse_unknownWireErrorType_fallsBackToUnknownType() {
        val body = """
            {
              "status": false,
              "message": "Custom Error",
              "data": null,
              "error": {
                "type": "future-unsupported-error-type",
                "detail": "Something futuristic happened",
                "traceId": "trace-789",
                "errors": null
              }
            }
        """.trimIndent()

        val failure = parser.parse(body, 400)

        assertEquals(ApiErrorType.UNKNOWN, failure.type)
        assertEquals("Something futuristic happened", failure.message)
        assertEquals(400, failure.httpStatus)
    }

    @Test
    fun parse_fastApiDefaultDetailJson_fallsBackToUnknownWithDetailMessage() {
        val body = """{"detail": "Internal server crash"}"""

        val failure = parser.parse(body, 500)

        assertEquals(ApiErrorType.UNKNOWN, failure.type)
        assertEquals("Internal server crash", failure.message)
        assertEquals(500, failure.httpStatus)
    }

    @Test
    fun parse_htmlErrorBody_fallsBackToUnknownWithoutCrashing() {
        val body = "<html><body>502 Bad Gateway</body></html>"

        val failure = parser.parse(body, 502)

        assertEquals(ApiErrorType.UNKNOWN, failure.type)
        assertEquals(502, failure.httpStatus)
    }

    @Test
    fun parse_emptyBody_returnsUnknown() {
        val failure = parser.parse("", 500)

        assertEquals(ApiErrorType.UNKNOWN, failure.type)
        assertEquals(500, failure.httpStatus)
    }

    @Test
    fun parse_nullBody_returnsUnknown() {
        val failure = parser.parse(null, 500)

        assertEquals(ApiErrorType.UNKNOWN, failure.type)
        assertEquals(500, failure.httpStatus)
    }
}
