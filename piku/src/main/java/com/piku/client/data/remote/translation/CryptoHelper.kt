package com.piku.client.data.remote.translation

import kotlinx.serialization.Serializable
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密模型目录的解密端，与 piku-models/scripts/encrypt-catalog.mjs 一一对应：
 * AES-256-GCM，密钥为 64 位 hex（32 字节），iv 12 字节随机，
 * data = 密文 + 16 字节 auth tag，信封字段均为 base64。
 *
 * 解密 key 随 App 分发，属混淆层而非真正保密——防的是明文 key 被
 * 爬虫直接扫走；免费模型额度有限，可接受这一威胁模型。
 */
object CryptoHelper {

    /** 信封格式：{"alg":"AES-256-GCM","iv":<b64>,"data":<b64>} */
    @Serializable
    data class Envelope(
        val alg: String = "",
        val iv: String = "",
        val data: String = "",
    )

    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val HEX_KEY_LENGTH = 64
    private const val EXPECTED_ALG = "AES-256-GCM"

    /** 密钥错、格式坏或数据被篡改时抛异常，调用方静默回退内置列表 */
    fun decrypt(envelope: Envelope, hexKey: String): String {
        require(envelope.alg == EXPECTED_ALG) { "unsupported alg: ${envelope.alg}" }
        require(hexKey.length == HEX_KEY_LENGTH) { "catalog key 必须是 $HEX_KEY_LENGTH 位 hex" }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(hexToBytes(hexKey), "AES"),
            GCMParameterSpec(TAG_LENGTH_BITS, Base64.getDecoder().decode(envelope.iv)),
        )
        return cipher.doFinal(Base64.getDecoder().decode(envelope.data)).toString(Charsets.UTF_8)
    }

    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
    }
}
