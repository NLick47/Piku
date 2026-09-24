package com.piku.client.data.repository

import com.piku.client.domain.model.LoginError

internal class ReloginPolicy(
    private val minIntervalMs: Long = 30_000L,
    private val maxBackoffMs: Long = 240_000L,
    private val maxDefinitiveFailures: Int = 3,
) {

    private var lastAttemptAt: Long? = null
    private var cooldownMs = minIntervalMs
    private var definitiveFailures = 0

    /** 记录本次尝试：距上次过近时返回 false，调用方应跳过 */
    fun allowAttempt(now: Long): Boolean {
        val last = lastAttemptAt
        if (last != null && now - last < cooldownMs) return false
        lastAttemptAt = now
        return true
    }

    /** 返回 true 表示应清除凭据并登出 */
    fun onFailure(error: Throwable): Boolean = when (error) {
        is LoginError.InvalidCredentials, is LoginError.Locked -> {
            definitiveFailures++
            definitiveFailures >= maxDefinitiveFailures
        }
        else -> {
            cooldownMs = (cooldownMs * 2).coerceAtMost(maxBackoffMs)
            false
        }
    }

    fun onSessionEstablished() {
        definitiveFailures = 0
        cooldownMs = minIntervalMs
    }
}
