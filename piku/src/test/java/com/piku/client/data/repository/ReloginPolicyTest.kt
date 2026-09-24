package com.piku.client.data.repository

import com.piku.client.domain.model.LoginError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReloginPolicyTest {

    @Test
    fun firstAttemptIsAlwaysAllowed() {
        val policy = ReloginPolicy()

        assertTrue(policy.allowAttempt(0))
    }

    @Test
    fun attemptWithinCooldownIsSkipped() {
        val policy = ReloginPolicy()
        policy.allowAttempt(0)

        assertFalse(policy.allowAttempt(29_999))
        assertTrue(policy.allowAttempt(30_000))
    }

    @Test
    fun networkFailureNeverTripsLogout() {
        val policy = ReloginPolicy()

        // 旧行为：三次网络失败就登出并删掉保存的密码
        repeat(10) { assertFalse(policy.onFailure(LoginError.Network)) }
    }

    @Test
    fun unknownFailureIsTreatedAsTransient() {
        val policy = ReloginPolicy()

        repeat(10) { assertFalse(policy.onFailure(LoginError.Unknown)) }
    }

    @Test
    fun transientFailureBacksOffAndCaps() {
        val policy = ReloginPolicy(minIntervalMs = 1_000L, maxBackoffMs = 4_000L)
        policy.allowAttempt(0)

        policy.onFailure(LoginError.Network)
        assertFalse("退避应翻倍到 2s", policy.allowAttempt(1_999))
        assertTrue(policy.allowAttempt(2_000))

        policy.onFailure(LoginError.Network)
        assertFalse("退避应到 4s", policy.allowAttempt(5_999))
        assertTrue(policy.allowAttempt(6_000))

        // 上限之后不再增长
        policy.onFailure(LoginError.Network)
        policy.onFailure(LoginError.Network)
        assertFalse(policy.allowAttempt(9_999))
        assertTrue(policy.allowAttempt(10_000))
    }

    @Test
    fun invalidCredentialsTripLogoutOnThirdFailure() {
        val policy = ReloginPolicy()

        assertFalse(policy.onFailure(LoginError.InvalidCredentials))
        assertFalse(policy.onFailure(LoginError.InvalidCredentials))
        assertTrue(policy.onFailure(LoginError.InvalidCredentials))
    }

    @Test
    fun lockedAccountCountsTowardSameLimit() {
        val policy = ReloginPolicy()

        assertFalse(policy.onFailure(LoginError.Locked))
        assertFalse(policy.onFailure(LoginError.InvalidCredentials))
        assertTrue(policy.onFailure(LoginError.Locked))
    }

    @Test
    fun transientFailuresDoNotConsumeDefinitiveBudget() {
        val policy = ReloginPolicy()
        repeat(5) { policy.onFailure(LoginError.Network) }

        assertFalse(policy.onFailure(LoginError.InvalidCredentials))
        assertFalse(policy.onFailure(LoginError.InvalidCredentials))
        assertTrue(policy.onFailure(LoginError.InvalidCredentials))
    }

    @Test
    fun sessionEstablishedResetsFailuresAndBackoff() {
        val policy = ReloginPolicy(minIntervalMs = 1_000L, maxBackoffMs = 4_000L)
        policy.allowAttempt(0)
        policy.onFailure(LoginError.InvalidCredentials)
        policy.onFailure(LoginError.InvalidCredentials)
        policy.onFailure(LoginError.Network)

        policy.onSessionEstablished()

        // 退避回到基准间隔，且计数归零后重新需要三次确定性失败
        assertTrue(policy.allowAttempt(1_000))
        assertFalse(policy.onFailure(LoginError.InvalidCredentials))
        assertFalse(policy.onFailure(LoginError.InvalidCredentials))
        assertTrue(policy.onFailure(LoginError.InvalidCredentials))
    }
}
