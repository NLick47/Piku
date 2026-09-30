package com.piku.client.data.remote.ech

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeEchFrameTest {

    @Test
    fun `reads the frame the native side writes`() {
        val frame = frameOf(
            status = 200,
            headers = listOf("content-type" to "application/json", "cf-ray" to "abc-123"),
            body = """{"error":false}""".toByteArray(),
        )
        val parsed = parseEchFrame(frame)
        assertEquals(200, parsed.status)
        assertEquals(
            listOf("content-type" to "application/json", "cf-ray" to "abc-123"),
            parsed.headers,
        )
        assertArrayEquals("""{"error":false}""".toByteArray(), parsed.body)
    }

    @Test
    fun `rejects an unknown frame version instead of misreading it`() {
        val frame = frameOf(status = 200, headers = emptyList(), body = ByteArray(0))
        frame[0] = 2
        assertThrows(IOException::class.java) { parseEchFrame(frame) }
    }

    @Test
    fun `keeps a body that contains the header separator bytes`() {
        val body = byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x1F)
        val parsed = parseEchFrame(frameOf(status = 400, headers = listOf("a" to "b"), body = body))
        assertEquals(400, parsed.status)
        assertArrayEquals(body, parsed.body)
    }

    private fun frameOf(
        status: Int,
        headers: List<Pair<String, String>>,
        body: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ECH_FRAME_VERSION)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(status).array())
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(headers.size).array())
        headers.forEach { (name, value) ->
            out.write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(name.length.toShort()).array())
            out.write(name.toByteArray())
            out.write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.length.toShort()).array())
            out.write(value.toByteArray())
        }
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(body.size).array())
        out.write(body)
        return out.toByteArray()
    }
}
