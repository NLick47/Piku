package com.piku.client.data.remote.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoHelperTest {

    private val hexKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private fun encrypt(plain: String): CryptoHelper.Envelope {
        val iv = ByteArray(12) { it.toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(hexToBytes(hexKey), "AES"),
            GCMParameterSpec(128, iv),
        )
        return CryptoHelper.Envelope(
            alg = "AES-256-GCM",
            iv = Base64.getEncoder().encodeToString(iv),
            data = Base64.getEncoder().encodeToString(cipher.doFinal(plain.toByteArray())),
        )
    }

    @Test
    fun `roundtrip decrypts envelope`() {
        val plain =
            """{"version":2,"models":[{"id":"a","label":"A","baseUrl":"https://x/v1","model":"m","apiKey":"sk-test"}]}"""
        assertEquals(plain, CryptoHelper.decrypt(encrypt(plain), hexKey))
    }

    @Test
    fun `tampered data fails`() {
        val tampered = encrypt("hello").copy(data = Base64.getEncoder().encodeToString(ByteArray(28)))
        assertThrows(Exception::class.java) { CryptoHelper.decrypt(tampered, hexKey) }
    }

    @Test
    fun `wrong alg rejected`() {
        val wrongAlg = encrypt("hello").copy(alg = "AES-CBC")
        assertThrows(IllegalArgumentException::class.java) { CryptoHelper.decrypt(wrongAlg, hexKey) }
    }

    @Test
    fun `wrong key length rejected`() {
        assertThrows(IllegalArgumentException::class.java) { CryptoHelper.decrypt(encrypt("hi"), "abcd") }
    }

    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
    }
}
