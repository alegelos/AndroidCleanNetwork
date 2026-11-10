package com.alegelos.androidcleannetwork

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URL
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class OkHttpNetworkSessionTest {
    @Test
    fun dataFor_executesTheConfiguredRequestAndReturnsResponseData() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse(code = 200, body = "ok"))
            server.start()
            val session = OkHttpNetworkSession(OkHttpClient())

            // When
            val actual = session.dataFor(TestSetup(URL(server.url("/").toString())))

            // Then
            assertEquals(200, actual.statusCode)
            assertEquals("ok", actual.data.decodeToString())
            assertEquals("/users/1", server.takeRequest().target)
        } finally {
            server.close()
        }
    }

    @Test
    fun dataFor_sendsMethodHeadersBodyAndQueryAndReturnsMetadata() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder().code(201).body("created")
                    .addHeader("X-Item", "one").addHeader("X-Item", "two").build(),
            )
            server.start()
            val setup = TestSetup(
                baseUrl = URL(server.url("/v1/").toString()),
                path = "users",
                method = HttpMethod.POST,
                headers = mapOf("Authorization" to "Bearer token", "Content-Type" to "application/json"),
                body = "{\"name\":\"Ada\"}".encodeToByteArray(),
                queryParameters = listOf(QueryParameter("details", "user items")),
            )

            val actual = OkHttpNetworkSession(OkHttpClient()).dataFor(setup)

            val request = assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("POST", request.method)
            assertEquals("/v1/users?details=user+items", request.target)
            assertEquals("Bearer token", request.headers["Authorization"])
            assertEquals("application/json", request.headers["Content-Type"])
            assertEquals("{\"name\":\"Ada\"}", request.body?.utf8())
            assertEquals(201, actual.statusCode)
            assertEquals("created", actual.data.decodeToString())
            assertEquals(listOf("one", "two"), actual.headers["X-Item"])
            assertEquals(setup.request.url.toExternalForm(), actual.url?.toExternalForm())
        }
    }

    @Test
    fun dataFor_mapsHttpErrorsToApiErrors() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse(code = 401))
            server.start()
            val session = OkHttpNetworkSession(OkHttpClient())

            // When
            val error = runCatching { session.dataFor(TestSetup(URL(server.url("/").toString()))) }.exceptionOrNull()

            // Then
            assertIs<ApiErrors.Unauthorized>(error)
        } finally {
            server.close()
        }
    }

    @Test
    fun dataFor_mapsCallTimeoutToTimeOut() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())
            server.start()
            val client = OkHttpClient.Builder().callTimeout(200, TimeUnit.MILLISECONDS).build()

            val error = runCatching {
                OkHttpNetworkSession(client).dataFor(TestSetup(URL(server.url("/").toString())))
            }.exceptionOrNull()

            assertIs<ApiErrors.TimeOut>(error)
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun dataFor_mapsSocketReadTimeoutToTimeOut() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())
            server.start()
            val client = OkHttpClient.Builder()
                .readTimeout(200, TimeUnit.MILLISECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build()

            val error = runCatching {
                OkHttpNetworkSession(client).dataFor(TestSetup(URL(server.url("/").toString())))
            }.exceptionOrNull()

            assertIs<ApiErrors.TimeOut>(error)
        }
    }

    @Test
    fun dataFor_preservesNonTimeoutIoFailureCauses() = runTest {
        val failures = listOf(IOException("connection failed"), InterruptedIOException("executor rejected"))
        for (failure in failures) {
            val client = OkHttpClient.Builder().addInterceptor { throw failure }.build()

            val actual = assertFailsWith<ApiErrors.NetworkFailure> {
                OkHttpNetworkSession(client).dataFor(TestSetup(URL("https://example.com")))
            }

            assertCauseChainContains(failure, actual)
        }
    }

    @Test
    fun dataFor_requiresAnExplicitBodyForPostPutAndPatchIncludingAnEmptyBody() = runTest {
        for (method in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) {
            val session = OkHttpNetworkSession(clientReturning(TrackingBody()))
            val failure = assertFailsWith<IllegalArgumentException> {
                session.dataFor(TestSetup(URL("https://example.com"), method = method))
            }
            assertEquals("method ${method.value} must have a request body.", failure.message)

            val actual = session.dataFor(
                TestSetup(URL("https://example.com"), method = method, body = byteArrayOf()),
            )
            assertEquals(200, actual.statusCode)
        }
    }

    @Test
    fun dataFor_rejectsABodyOnGet() = runTest {
        assertFailsWith<IllegalArgumentException> {
            OkHttpNetworkSession(OkHttpClient()).dataFor(
                TestSetup(URL("https://example.com"), body = byteArrayOf()),
            )
        }
    }

    @Test
    fun dataFor_closesResponseBodiesOnSuccessAndHttpFailure() = runTest {
        for (status in listOf(200, 401)) {
            val body = TrackingBody()
            val actual = runCatching {
                OkHttpNetworkSession(clientReturning(body, status))
                    .dataFor(TestSetup(URL("https://example.com")))
            }

            if (status == 200) assertEquals("user", actual.getOrThrow().data.decodeToString())
            else assertIs<ApiErrors.Unauthorized>(actual.exceptionOrNull())
            assertEquals(1, body.closeCount.get())
        }
    }

    @Test
    fun dataFor_closesResponseBodyWhenReadingFails() = runTest {
        val failure = IOException("body interrupted")
        val body = TrackingBody(readFailure = failure)

        val actual = assertFailsWith<ApiErrors.NetworkFailure> {
            OkHttpNetworkSession(clientReturning(body)).dataFor(TestSetup(URL("https://example.com")))
        }

        assertCauseChainContains(failure, actual)
        assertEquals(1, body.closeCount.get())
    }

    @Test
    fun dataFor_preservesCancellationWhenTheCompletedBodyIsBeingClosed() = runBlocking<Unit> {
        val requestJob = Job()
        val body = TrackingBody(onClose = { requestJob.cancel() })
        try {
            assertFailsWith<CancellationException> {
                withContext(requestJob) {
                    OkHttpNetworkSession(clientReturning(body)).dataFor(TestSetup(URL("https://example.com")))
                }
            }
            assertEquals(1, body.closeCount.get())
        } finally {
            requestJob.cancel()
        }
    }

    @Test
    fun dataFor_cancelsTheCallBeforeResponseHeaders() = runBlocking<Unit> {
        assertCallCancellation(duringBody = false)
    }

    @Test
    fun dataFor_cancelsTheCallDuringResponseBodyRead() = runBlocking<Unit> {
        assertCallCancellation(duringBody = true)
    }

    @Test
    fun dataWithUnauthorizedRefreshRetry_refreshesOnceAfterUnauthorized() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse(code = 401))
            server.enqueue(MockResponse(code = 200, body = "success"))
            server.start()
            val seenPolicies = mutableListOf<AccessTokenPolicy>()
            val seenTokens = mutableListOf<String>()
            val session = OkHttpNetworkSession(OkHttpClient())
            val gateway = object : ApisGatewayProtocol {
                override suspend fun apisAccessToken(policy: AccessTokenPolicy): String {
                    seenPolicies += policy
                    return if (policy == AccessTokenPolicy.ForceRefresh) "fresh" else "cached"
                }
            }

            // When
            val actual = session.dataWithUnauthorizedRefreshRetry(gateway) { token ->
                seenTokens += token
                TestSetup(URL(server.url("/").toString()), headers = mapOf("Authorization" to "Bearer $token"))
            }

            // Then
            assertEquals("success", actual.data.decodeToString())
            assertEquals(listOf(AccessTokenPolicy.Default, AccessTokenPolicy.ForceRefresh), seenPolicies)
            assertEquals(listOf("cached", "fresh"), seenTokens)
            assertEquals("Bearer cached", server.takeRequest().headers["Authorization"])
            assertEquals("Bearer fresh", server.takeRequest().headers["Authorization"])
            assertEquals(2, server.requestCount)
        } finally {
            server.close()
        }
    }

    @Test
    fun dataWithUnauthorizedRefreshRetry_propagatesASecondUnauthorizedResponse() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 401))
            server.enqueue(MockResponse(code = 401))
            server.start()
            val gateway = RecordingGateway()

            assertFailsWith<ApiErrors.Unauthorized> {
                OkHttpNetworkSession(OkHttpClient()).dataWithUnauthorizedRefreshRetry(gateway) { token ->
                    TestSetup(URL(server.url("/").toString()), headers = mapOf("Authorization" to "Bearer $token"))
                }
            }

            assertEquals(listOf(AccessTokenPolicy.Default, AccessTokenPolicy.ForceRefresh), gateway.policies)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun dataWithUnauthorizedRefreshRetry_doesNotRefreshAfterSuccessOrAnotherHttpError() = runTest {
        for (status in listOf(200, 403, 500)) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse(code = status, body = "user"))
                server.start()
                val gateway = RecordingGateway()

                val actual = runCatching {
                    OkHttpNetworkSession(OkHttpClient()).dataWithUnauthorizedRefreshRetry(gateway) {
                        TestSetup(URL(server.url("/").toString()))
                    }
                }

                when (status) {
                    200 -> assertEquals("user", actual.getOrThrow().data.decodeToString())
                    403 -> assertIs<ApiErrors.Forbidden>(actual.exceptionOrNull())
                    500 -> assertIs<ApiErrors.ServerError>(actual.exceptionOrNull())
                }
                assertEquals(listOf(AccessTokenPolicy.Default), gateway.policies)
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun dataWithUnauthorizedRefreshRetry_propagatesRefreshFailureWithoutAnotherRequest() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 401))
            server.start()
            val gateway = RecordingGateway(refreshFailure = ApiErrors.InvalidToken)
            var setupsBuilt = 0

            assertFailsWith<ApiErrors.InvalidToken> {
                OkHttpNetworkSession(OkHttpClient()).dataWithUnauthorizedRefreshRetry(gateway) {
                    setupsBuilt++
                    TestSetup(URL(server.url("/").toString()))
                }
            }

            assertEquals(listOf(AccessTokenPolicy.Default, AccessTokenPolicy.ForceRefresh), gateway.policies)
            assertEquals(1, setupsBuilt)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun dataWithUnauthorizedRefreshRetry_propagatesTokenCancellationBeforeBuildingARequest() = runTest {
        val cancellation = CancellationException("token lookup cancelled")
        val gateway = object : ApisGatewayProtocol {
            override suspend fun apisAccessToken(policy: AccessTokenPolicy): String = throw cancellation
        }
        var setupsBuilt = 0

        val actual = assertFailsWith<CancellationException> {
            OkHttpNetworkSession(OkHttpClient()).dataWithUnauthorizedRefreshRetry(gateway) {
                setupsBuilt++
                TestSetup(URL("https://example.com"))
            }
        }

        assertSame(cancellation, actual)
        assertEquals(0, setupsBuilt)
    }

    private suspend fun assertCallCancellation(duringBody: Boolean) = supervisorScope<Unit> {
        MockWebServer().use { server ->
            val response = MockResponse.Builder().body("user")
            if (duringBody) response.onResponseBody(SocketEffect.Stall)
            else response.onResponseStart(SocketEffect.Stall)
            server.enqueue(response.build())
            server.start()

            val startedCall = CompletableDeferred<Call>()
            val bodyStarted = CompletableDeferred<Unit>()
            val responseBody = AtomicReference<TrackingBody>()
            val client = OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .addNetworkInterceptor { chain ->
                    val actual = chain.proceed(chain.request())
                    val body = TrackingBody(actual.body)
                    responseBody.set(body)
                    actual.newBuilder().body(body).build()
                }
                .eventListener(object : EventListener() {
                    override fun callStart(call: Call) {
                        startedCall.complete(call)
                    }

                    override fun responseBodyStart(call: Call) {
                        bodyStarted.complete(Unit)
                    }
                })
                .build()
            val result = async {
                OkHttpNetworkSession(client).dataFor(TestSetup(URL(server.url("/").toString())))
            }

            var call: Call? = null
            try {
                call = withTimeout(5_000) { startedCall.await() }
                assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                if (duringBody) withTimeout(5_000) { bodyStarted.await() }

                result.cancel()

                assertTrue(call.isCanceled(), "Coroutine cancellation must cancel the active HTTP call")
                withTimeout(5_000) { result.join() }
                assertFailsWith<CancellationException> { result.await() }
                if (duringBody) assertEquals(1, assertNotNull(responseBody.get()).closeCount.get())
            } finally {
                call?.cancel()
                client.dispatcher.cancelAll()
                result.cancelAndJoin()
            }
        }
    }

    private fun assertCauseChainContains(expected: Throwable, actual: Throwable) {
        // Coroutine stacktrace recovery may insert copies that retain the original as a cause.
        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var cause: Throwable? = actual
        while (cause != null && visited.add(cause)) {
            if (cause === expected) return
            cause = cause.cause
        }
        fail("The original ${expected.javaClass.simpleName} was not retained in the cause chain")
    }

    private fun clientReturning(body: ResponseBody, status: Int = 200): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("test response").body(body).build()
        }.build()

    private class TrackingBody(
        private val delegate: ResponseBody = "user".toResponseBody(),
        private val readFailure: IOException? = null,
        private val onClose: () -> Unit = {},
    ) : ResponseBody() {
        val closeCount = AtomicInteger()
        private val trackedSource = object : ForwardingSource(delegate.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                readFailure?.let { throw it }
                return super.read(sink, byteCount)
            }

            override fun close() {
                closeCount.incrementAndGet()
                super.close()
                onClose()
            }
        }.buffer()

        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()
        override fun source() = trackedSource
    }

    private class RecordingGateway(private val refreshFailure: Throwable? = null) : ApisGatewayProtocol {
        val policies = mutableListOf<AccessTokenPolicy>()

        override suspend fun apisAccessToken(policy: AccessTokenPolicy): String {
            policies += policy
            if (policy == AccessTokenPolicy.ForceRefresh) refreshFailure?.let { throw it }
            return if (policy == AccessTokenPolicy.ForceRefresh) "fresh" else "cached"
        }
    }

    private class TestSetup(
        baseUrl: URL,
        override val path: String = "users/1",
        override val method: HttpMethod = HttpMethod.GET,
        override val headers: Map<String, String> = emptyMap(),
        override val body: ByteArray? = null,
        override val queryParameters: List<QueryParameter> = emptyList(),
    ) : ApiSetupProtocol {
        override val request = ApiRequestBuilder.build(baseUrl, path, method, headers, body, queryParameters)
    }
}
