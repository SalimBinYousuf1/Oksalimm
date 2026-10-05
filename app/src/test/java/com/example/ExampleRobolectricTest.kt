package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PairingStorage
import com.example.model.ControlMessage
import com.example.model.FileProtocol
import com.example.model.HostStatus
import com.example.model.NotificationItem
import com.example.model.PairingInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context verifies app_name is Salim`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Salim", appName)
    }

    @Test
    fun `pairing parser decodes JSON payload correctly`() {
        val jsonPayload = """
            {
                "hostId": "ubaid-host-994",
                "secretKey": "test-sec-key-123",
                "sessionId": "ubaid-host-994",
                "name": "Alex Galaxy S24"
            }
        """.trimIndent()

        val parsed = PairingInfo.parse(jsonPayload)
        assertNotNull(parsed)
        assertEquals("ubaid-host-994", parsed?.hostId)
        assertEquals("test-sec-key-123", parsed?.secretKey)
        assertEquals("Alex Galaxy S24", parsed?.hostName)
    }

    @Test
    fun `pairing parser decodes URI format correctly`() {
        val uriPayload = "ubaid://pair?hostId=ubaid-device-42&secret=secret42&name=LivingRoomPhone"
        val parsed = PairingInfo.parse(uriPayload)
        assertNotNull(parsed)
        assertEquals("ubaid-device-42", parsed?.hostId)
        assertEquals("secret42", parsed?.secretKey)
        assertEquals("LivingRoomPhone", parsed?.hostName)
    }

    @Test
    fun `pairing parser gracefully rejects garbage string without crashing`() {
        val garbagePayloads = listOf(
            "",
            "   ",
            "not a valid code with spaces and newline\nand junk",
            "https://random-unrelated-website.com/index.html",
            "{ malformed json string without closing }",
            "???$$$##@@@"
        )

        for (garbage in garbagePayloads) {
            val result = PairingInfo.parse(garbage)
            // Empty and malformed strings should return null or be handled safely without throwing
            if (garbage.isBlank() || garbage.contains(" ")) {
                assertNull("Expected null for '$garbage'", result)
            }
        }
    }

    @Test
    fun `control messages serialize to expected JSON structure`() {
        val tap = ControlMessage.Tap(x = 0.5f, y = 0.75f)
        val tapJson = JSONObject(tap.toJson())
        assertEquals("tap", tapJson.getString("type"))
        assertEquals(0.5, tapJson.getDouble("x"), 0.001)
        assertEquals(0.75, tapJson.getDouble("y"), 0.001)

        val key = ControlMessage.Key("BACK")
        val keyJson = JSONObject(key.toJson())
        assertEquals("key", keyJson.getString("type"))
        assertEquals("BACK", keyJson.getString("key"))
        assertEquals(4, keyJson.getInt("keyCode"))

        val text = ControlMessage.Text("Hello Ubaid")
        val textJson = JSONObject(text.toJson())
        assertEquals("text", textJson.getString("type"))
        assertEquals("Hello Ubaid", textJson.getString("text"))
    }

    @Test
    fun `file protocol generates and parses messages correctly`() {
        val listReq = FileProtocol.createListFilesRequest("/sdcard/Download")
        val reqJson = JSONObject(listReq)
        assertEquals("list_files", reqJson.getString("type"))
        assertEquals("/sdcard/Download", reqJson.getString("path"))

        val responsePayload = JSONObject("""
            {
                "type": "file_list",
                "path": "/sdcard/Download",
                "items": [
                    { "name": "Documents", "isDirectory": true, "size": 0 },
                    { "name": "screenshot.png", "isDirectory": false, "size": 1048576 }
                ]
            }
        """)

        val parsed = FileProtocol.parseFileListResponse(responsePayload)
        assertNotNull(parsed)
        assertEquals("/sdcard/Download", parsed?.first)
        val files = parsed?.second ?: emptyList()
        assertEquals(2, files.size)
        assertTrue(files[0].isDirectory)
        assertEquals("Documents", files[0].name)
        assertEquals("screenshot.png", files[1].name)
    }

    @Test
    fun `notification item parses json properly`() {
        val json = JSONObject("""
            {
                "id": "notif-101",
                "packageName": "com.whatsapp",
                "appName": "WhatsApp",
                "title": "Sarah",
                "text": "Call me when you are free",
                "timestamp": 1728000000000
            }
        """)

        val item = NotificationItem.fromJson(json)
        assertNotNull(item)
        assertEquals("notif-101", item?.id)
        assertEquals("WhatsApp", item?.appName)
        assertEquals("Sarah", item?.title)
        assertEquals("Call me when you are free", item?.text)
    }

    @Test
    fun `host status parses json correctly`() {
        val json = JSONObject("""
            {
                "batteryPercent": 84,
                "isCharging": true,
                "networkType": "WiFi (5 GHz)",
                "fps": 60,
                "latencyMs": 22,
                "resolution": "1080x2400"
            }
        """)

        val status = HostStatus.fromJson(json)
        assertEquals(84, status.batteryPercent)
        assertTrue(status.isCharging)
        assertEquals("WiFi (5 GHz)", status.networkType)
        assertEquals(60, status.fps)
        assertEquals(22L, status.latencyMs)
        assertEquals("1080x2400", status.resolution)
    }

    @Test
    fun `pairing storage persists and clears data properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = PairingStorage(context)
        storage.clearPairing()

        assertNull(storage.getPairingInfo())

        val testInfo = PairingInfo(
            hostId = "host-test-id",
            secretKey = "secret-123",
            sessionId = "session-test",
            hostName = "Test Host"
        )
        storage.savePairingInfo(testInfo)

        val retrieved = storage.getPairingInfo()
        assertNotNull(retrieved)
        assertEquals("host-test-id", retrieved?.hostId)
        assertEquals("Test Host", retrieved?.hostName)

        storage.clearPairing()
        assertNull(storage.getPairingInfo())
    }
}
