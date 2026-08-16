package com.piku.client.data.local

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 作品密码存取：密文落盘（Android Keystore AES-GCM），读取时解密失败（密钥失效/
 * 数据损坏，如卸载重装）静默视为无记录。只提供 get/save/delete，不提供枚举与
 * 明文读取的 UI 通道，保证密码不可被用户查看或修改。
 */
@Singleton
class WorkPasswordRepository @Inject constructor(
    private val dao: WorkPasswordDao,
    private val cipher: CredentialCipher,
) {

    suspend fun getPassword(workId: Long): String? {
        val encrypted = dao.getPassword(workId)
        Log.d(TAG, "wpGet work=$workId row=${encrypted != null}")
        val decrypted = encrypted?.let {
            runCatching { cipher.decrypt(it) }.getOrNull()
        }
        Log.d(TAG, "wpGet work=$workId decrypted=${decrypted != null}")
        return decrypted
    }

    /** 仅应在服务端验证解锁成功后调用 */
    suspend fun savePassword(workId: Long, password: String) {
        if (password.isBlank()) return
        val encrypted = runCatching { cipher.encrypt(password) }.getOrNull()
        Log.d(TAG, "wpSave work=$workId encrypted=${encrypted != null}")
        if (encrypted != null) {
            dao.upsert(
                WorkPasswordEntity(
                    workId = workId,
                    password = encrypted,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** 自动进入失败（作者改密码）时清除失效记录 */
    suspend fun deletePassword(workId: Long) {
        Log.d(TAG, "wpDelete work=$workId")
        dao.delete(workId)
    }

    private companion object {
        const val TAG = "PikuDiag"
    }
}
