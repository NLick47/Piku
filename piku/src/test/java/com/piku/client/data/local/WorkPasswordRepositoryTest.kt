package com.piku.client.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test

class WorkPasswordRepositoryTest {

    private val dao = FakeWorkPasswordDao()
    private val repo = WorkPasswordRepository(dao, FakeCipher())

    @Test
    fun saveAndLoadRoundTrip() {
        runBlocking {
        repo.savePassword(13325281L, "yes")

        assertEquals("yes", repo.getPassword(13325281L))
        }
    }

    @Test
    fun saveOverwritesPreviousPassword() {
        runBlocking {
        repo.savePassword(13325281L, "old")
        repo.savePassword(13325281L, "new")

        assertEquals("new", repo.getPassword(13325281L))
        // 落盘的是密文而非明文
        assertEquals("ENC:new", dao.rows[13325281L])
        }
    }

    @Test
    fun saveIgnoresBlankPassword() {
        runBlocking {
        repo.savePassword(13325281L, "   ")

        assertNull(repo.getPassword(13325281L))
        }
    }

    @Test
    fun deleteRemovesPassword() {
        runBlocking {
        repo.savePassword(13325281L, "yes")
        repo.deletePassword(13325281L)

        assertNull(repo.getPassword(13325281L))
        }
    }

    @Test
    fun getReturnsNullWhenNothingSaved() {
        runBlocking {
        assertNull(repo.getPassword(42L))
        }
    }

    @Test
    fun getReturnsNullOnCorruptedCipherText() {
        runBlocking {
        dao.rows[13325281L] = "garbage-not-encrypted"

        assertNull(repo.getPassword(13325281L))
        }
    }

    @Test
    fun passwordsArePerWork() {
        runBlocking {
        repo.savePassword(1L, "aaa")
        repo.savePassword(2L, "bbb")

        assertEquals("aaa", repo.getPassword(1L))
        assertEquals("bbb", repo.getPassword(2L))
        repo.deletePassword(1L)
        assertNull(repo.getPassword(1L))
        assertEquals("bbb", repo.getPassword(2L))
        }
    }

    private class FakeCipher : CredentialCipher {
        override fun encrypt(plain: String): String = "ENC:$plain"
        override fun decrypt(cipherText: String): String {
            require(cipherText.startsWith("ENC:")) { "corrupted" }
            return cipherText.removePrefix("ENC:")
        }
    }

    private class FakeWorkPasswordDao : WorkPasswordDao {
        val rows = HashMap<Long, String>()

        override suspend fun getPassword(workId: Long): String? = rows[workId]

        override suspend fun upsert(entity: WorkPasswordEntity) {
            rows[entity.workId] = entity.password
        }

        override suspend fun delete(workId: Long) {
            rows.remove(workId)
        }
    }
}
