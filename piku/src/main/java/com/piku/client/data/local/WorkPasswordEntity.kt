package com.piku.client.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 作品密码（自动进入用）：password 为 AES-GCM 密文（Base64），永不明文落盘。
 * 写入途径唯一：服务端验证解锁成功时由 WorkPasswordRepository 保存/覆盖；
 * 无任何 UI 可查看或修改，作者改密码导致自动进入失败（-2）时自动清除。
 */
@Entity(tableName = "work_passwords")
data class WorkPasswordEntity(
    @PrimaryKey val workId: Long,
    val password: String,
    val updatedAt: Long,
)
