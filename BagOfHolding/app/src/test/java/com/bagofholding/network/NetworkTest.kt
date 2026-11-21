package com.bagofholding.network

import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ShareCodecTest {

    private val character = PlayerCharacter(name = "Lia", characterClass = "Rogue", level = 3, purseCp = 250)
    private val items = listOf(
        InventoryItem(characterId = 1, name = "Dagger", category = ItemCategory.WEAPON, quantity = 2, weight = 1.0),
    )

    @Test
    fun `encode then decode keeps all data`() {
        val json = ShareCodec.encode(ShareCodec.fromDomain(character, items))
        val payload = ShareCodec.decode(json)
        assertEquals("Lia", payload.character.name)
        assertEquals(250L, payload.character.purseCp)
        val item = payload.items.single().toDomain(characterId = 42)
        assertEquals(42L, item.characterId)
        assertEquals(ItemCategory.WEAPON, item.category)
        assertEquals(2, item.quantity)
    }

    @Test
    fun `unknown json fields are ignored and unknown categories become OTHER`() {
        val json = """{"app":"bag-of-holding","version":1,"extra":true,
            "character":{"name":"Bo"},"items":[{"name":"Thing","category":"SPACESHIP"}]}"""
        val payload = ShareCodec.decode(json)
        assertEquals(ItemCategory.OTHER, payload.items.single().toDomain(1).category)
    }

    @Test(expected = ShareFormatException::class)
    fun `garbage is rejected`() {
        ShareCodec.decode("<html>not json</html>")
    }

    @Test(expected = ShareFormatException::class)
    fun `other apps are rejected`() {
        ShareCodec.decode("""{"app":"something-else","character":{"name":"x"},"items":[]}""")
    }

    @Test
    fun `received character values are clamped`() {
        val character = SharedCharacter(name = "  ", level = 99, strength = -4, purseCp = -10).toDomain()
        assertEquals("Unnamed", character.name)
        assertEquals(PlayerCharacter.MAX_LEVEL, character.level)
        assertEquals(PlayerCharacter.MIN_STRENGTH, character.strength)
        assertEquals(0L, character.purseCp)
    }
}

class AddressParserTest {

    @Test
    fun `parses host with and without port`() {
        assertEquals(HostAddress("192.168.1.5", 8080), AddressParser.parse("192.168.1.5:8080"))
        assertEquals(HostAddress("192.168.1.5", LanShareServer.DEFAULT_PORT), AddressParser.parse(" 192.168.1.5 "))
        assertEquals(HostAddress("10.0.2.2", 47321), AddressParser.parse("http://10.0.2.2:47321/"))
    }

    @Test
    fun `rejects invalid addresses`() {
        assertNull(AddressParser.parse(""))
        assertNull(AddressParser.parse("192.168.1.5:abc"))
        assertNull(AddressParser.parse("192.168.1.5:70000"))
        assertNull(AddressParser.parse("my phone:80"))
    }
}

class HttpProtocolTest {

    @Test
    fun `reads method and path and ignores query and headers`() {
        val raw = "GET /inventory?x=1 HTTP/1.1\r\nHost: a\r\nAccept: */*\r\n\r\n"
        val request = HttpProtocol.readRequest(ByteArrayInputStream(raw.toByteArray()))
        assertEquals(HttpRequest("GET", "/inventory"), request)
    }

    @Test
    fun `malformed request line returns null`() {
        assertNull(HttpProtocol.readRequest(ByteArrayInputStream("hello\r\n\r\n".toByteArray())))
        assertNull(HttpProtocol.readRequest(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun `response bytes contain status line and content length`() {
        val text = String(HttpResponse.json("{}").toBytes())
        assertTrue(text.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(text.contains("Content-Length: 2\r\n"))
        assertTrue(text.endsWith("\r\n\r\n{}"))
    }

    @Test
    fun `router answers each path`() = runTest {
        val payload: suspend () -> String = { "{}" }
        assertEquals(HttpResponse.OK, LanShareServer.route(HttpRequest("GET", "/inventory"), payload).status)
        assertEquals(HttpResponse.OK, LanShareServer.route(HttpRequest("GET", "/ping"), payload).status)
        assertEquals(HttpResponse.NOT_FOUND, LanShareServer.route(HttpRequest("GET", "/x"), payload).status)
        assertEquals(HttpResponse.METHOD_NOT_ALLOWED, LanShareServer.route(HttpRequest("POST", "/x"), payload).status)
        assertEquals(HttpResponse.BAD_REQUEST, LanShareServer.route(null, payload).status)
        val failing: suspend () -> String = { error("db closed") }
        assertEquals(HttpResponse.SERVER_ERROR, LanShareServer.route(HttpRequest("GET", "/inventory"), failing).status)
    }
}

/** Starts the real server on localhost and downloads from it with the real client. */
class LanShareRoundTripTest {

    @Test
    fun `client downloads what the server serves`() = runBlocking {
        val payload = ShareCodec.fromDomain(
            PlayerCharacter(name = "Mira"),
            listOf(InventoryItem(characterId = 1, name = "Lute", category = ItemCategory.TOOL)),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = LanShareServer(payloadProvider = { ShareCodec.encode(payload) })
        try {
            val port = server.start(scope, preferredPort = 0)
            assertTrue(server.isRunning)
            val received = LanShareClient().fetch(HostAddress("127.0.0.1", port))
            assertEquals(payload, received)
        } finally {
            server.stop()
            scope.cancel()
        }
        assertTrue(!server.isRunning)
    }

    @Test
    fun `client reports a connection error when nobody is listening`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = LanShareServer(payloadProvider = { "{}" })
        val port = server.start(scope, preferredPort = 0)
        server.stop() // free the port again
        scope.cancel()
        try {
            LanShareClient().fetch(HostAddress("127.0.0.1", port))
            fail("Expected IOException")
        } catch (expected: IOException) {
            // ok
        }
    }
}
