package com.piku.client.data.local

import com.piku.client.domain.model.UserProfile
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

    @Test
    fun profileCacheRoundTrip() {
        val store = CredentialStore(InMemoryStorage(), FakeCipher())
        val profile = UserProfile(
            uid = "12345",
            avatarUrl = "https://poipiku.com/img/12345_120.jpg",
            profileUrl = "https://poipiku.com/12345/",
            name = "测试昵称",
        )

        store.saveProfile(profile)

        assertEquals(profile, store.loadProfile())
    }

    @Test
    fun profileCacheReturnsNullWhenNothingSaved() {
        val store = CredentialStore(InMemoryStorage(), FakeCipher())

        assertNull(store.loadProfile())
    }

    @Test
    fun profileCacheIgnoresBlankUid() {
        val store = CredentialStore(InMemoryStorage(), FakeCipher())

        store.saveProfile(UserProfile(uid = null, avatarUrl = null, profileUrl = null, name = "x"))

        assertNull(store.loadProfile())
    }

    @Test
    fun clearRemovesCachedProfile() {
        val store = CredentialStore(InMemoryStorage(), FakeCipher())
        store.saveProfile(
            UserProfile(uid = "1", avatarUrl = null, profileUrl = null, name = "n"),
        )

        store.clear()

        assertNull(store.loadProfile())
        assertNull(store.load())
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