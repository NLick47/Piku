package com.piku.client.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialStoreTest {

    @Test
    fun saveAndLoadRoundTrip() {
        val store = CredentialStore(InMemoryStorage(), FakeCipher())

        store.save("user@example.com", "secret123")

        assertEquals(
            Credentials("user@example.com", "secret123"),
            store.load(),
        )
    }

    @Test
    fun saveIgnoresBlankInput() {
        val store = CredentialStore(InMemoryStorage(), FakeCipher())

        store.save("", "")
        store.save("  ", "")

        assertNull(store.load())
    }

    @Test
    fun loadReturnsNullWhenNothingSaved() {
        val store = CredentialStore(InMemoryStorage(), FakeCipher())

        assertNull(store.load())
    }

    @Test
    fun loadReturnsNullOnCorruptedCipherText() {
        val storage = InMemoryStorage().apply {
            put("email_enc", "garbage")
            put("password_enc", "garbage")
        }
        val store = CredentialStore(storage, FakeCipher())

        assertNull(store.load())
    }

    @Test
    fun loadReturnsNullWhenOnlyOneFieldSaved() {
        val storage = InMemoryStorage().apply { put("email_enc", "ENC:user@example.com") }
        val store = CredentialStore(storage, FakeCipher())

        assertNull(store.load())
    }

    @Test
    fun clearRemovesEverything() {
        val store = CredentialStore(InMemoryStorage(), FakeCipher())
        store.save("user@example.com", "secret123")

        store.clear()

        assertNull(store.load())
    }

    @Test
    fun saveDoesNotPropagateEncryptionFailure() {
        val store = CredentialStore(InMemoryStorage(), AlwaysFailCipher())

        store.save("user@example.com", "secret123")

        assertNull(store.load())
    }

    @Test
    fun failedSaveKeepsPreviousCredentialsIntact() {
        val storage = InMemoryStorage()
        val cipher = SwitchableCipher()
        val store = CredentialStore(storage, cipher)
        store.save("user@example.com", "secret123")

        cipher.delegate = AlwaysFailCipher()
        store.save("new@example.com", "new-secret")

        assertEquals("ENC:user@example.com", storage.get("email_enc"))
        assertEquals("ENC:secret123", storage.get("password_enc"))
    }

    private class AlwaysFailCipher : CredentialCipher {
        override fun encrypt(plain: String): String = throw IllegalStateException("keystore unavailable")
        override fun decrypt(cipherText: String): String = error("unreachable")
    }

    private class SwitchableCipher(initial: CredentialCipher = FakeCipher()) : CredentialCipher {
        var delegate: CredentialCipher = initial
        override fun encrypt(plain: String): String = delegate.encrypt(plain)
        override fun decrypt(cipherText: String): String = delegate.decrypt(cipherText)
    }

    private class FakeCipher : CredentialCipher {
        override fun encrypt(plain: String): String = "ENC:$plain"
        override fun decrypt(cipherText: String): String {
            require(cipherText.startsWith("ENC:")) { "corrupted" }
            return cipherText.removePrefix("ENC:")
        }
    }

    private class InMemoryStorage : CredentialStorage {
        private val map = HashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }
    }
}