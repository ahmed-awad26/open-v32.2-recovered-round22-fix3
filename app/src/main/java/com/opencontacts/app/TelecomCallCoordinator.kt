package com.opencontacts.app

import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Central telecom-backed call state holder used by InCallService, notifications,
 * floating incoming UI, and the full active-call controls screen.
 */
object TelecomCallCoordinator {
    private var inCallService: InCallService? = null
    private var currentCall: Call? = null

    private val _incomingCall = MutableStateFlow<IncomingCallUiState?>(null)
    val incomingCall: StateFlow<IncomingCallUiState?> = _incomingCall

    private val _activeCall = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCall: StateFlow<ActiveCallUiState?> = _activeCall

    private val _telecomState = MutableStateFlow(TelecomCallUiState())
    val telecomState: StateFlow<TelecomCallUiState> = _telecomState

    fun attachService(service: InCallService) {
        inCallService = service
        updateAudioState(service.callAudioState)
    }

    fun detachService(service: InCallService) {
        if (inCallService === service) {
            inCallService = null
        }
    }

    fun onIncoming(call: Call, ui: IncomingCallUiState) {
        currentCall = call
        _incomingCall.value = ui
        _activeCall.value = ui.toActiveCallUiState()
        _telecomState.value = _telecomState.value.copy(
            state = Call.STATE_RINGING,
            canHold = call.details?.can(Call.Details.CAPABILITY_HOLD) == true,
            canMute = true,
            canSpeaker = true,
        )
    }

    fun onCallActive(call: Call, ui: ActiveCallUiState) {
        currentCall = call
        _incomingCall.value = null
        _activeCall.value = ui
        _telecomState.value = _telecomState.value.copy(
            state = call.state,
            canHold = call.details?.can(Call.Details.CAPABILITY_HOLD) == true,
            canMute = true,
            canSpeaker = true,
        )
    }

    fun onCallStateChanged(call: Call) {
        if (currentCall != call) currentCall = call
        _telecomState.value = _telecomState.value.copy(
            state = call.state,
            isOnHold = call.state == Call.STATE_HOLDING,
            canHold = call.details?.can(Call.Details.CAPABILITY_HOLD) == true,
        )
    }

    fun updateAudioState(state: CallAudioState?) {
        if (state == null) return
        _telecomState.value = _telecomState.value.copy(
            isMuted = state.isMuted,
            isSpeakerOn = state.route and CallAudioState.ROUTE_SPEAKER == CallAudioState.ROUTE_SPEAKER,
        )
    }

    fun clearIncoming() {
        _incomingCall.value = null
    }

    fun clearAll() {
        currentCall = null
        _incomingCall.value = null
        _activeCall.value = null
        _telecomState.value = TelecomCallUiState()
    }

    fun answer() {
        currentCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    }

    fun decline() {
        val call = currentCall ?: return
        runCatching { call.disconnect() }.onFailure {
            runCatching { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) call.reject(false, null) }
        }
    }

    fun disconnect() {
        currentCall?.disconnect()
    }

    fun toggleHold() {
        val call = currentCall ?: return
        when (call.state) {
            Call.STATE_HOLDING -> call.unhold()
            Call.STATE_ACTIVE -> if (call.details?.can(Call.Details.CAPABILITY_HOLD) == true) call.hold()
        }
    }

    fun setMuted(enabled: Boolean) {
        inCallService?.setMuted(enabled)
        _telecomState.value = _telecomState.value.copy(isMuted = enabled)
    }

    fun setSpeaker(enabled: Boolean) {
        inCallService?.setAudioRoute(if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
        _telecomState.value = _telecomState.value.copy(isSpeakerOn = enabled)
    }

    fun showSystemDialpad() {
        val service = inCallService ?: return
        runCatching {
            val telecomManager = service.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.showInCallScreen(true)
        }.onFailure {
            runCatching {
                val intent = Intent(service, ActiveCallControlsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                service.startActivity(intent)
            }
        }
    }
}

data class TelecomCallUiState(
    val state: Int = Call.STATE_DISCONNECTED,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val canHold: Boolean = false,
    val canMute: Boolean = false,
    val canSpeaker: Boolean = false,
)
