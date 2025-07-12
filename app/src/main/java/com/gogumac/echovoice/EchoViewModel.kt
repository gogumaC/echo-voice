// EchoViewModel.kt
package com.gogumac.echovoice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class EchoViewModel(
    initialDelay: Float = 500f
) : ViewModel() {
    var isServiceRunning by mutableStateOf(false)
        private set

    var echoDelay by mutableFloatStateOf(initialDelay)
        private set

    fun updateEchoDelay(value: Float) {
        echoDelay = value
    }

    fun updateServiceRunning(running: Boolean) {
        isServiceRunning = running
    }
}