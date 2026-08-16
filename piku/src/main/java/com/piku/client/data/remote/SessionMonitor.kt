package com.piku.client.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionMonitor @Inject constructor() {

    private val _sessionCleared = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val sessionCleared: SharedFlow<Unit> = _sessionCleared.asSharedFlow()

    fun notifySessionCleared() {
        _sessionCleared.tryEmit(Unit)
    }
}