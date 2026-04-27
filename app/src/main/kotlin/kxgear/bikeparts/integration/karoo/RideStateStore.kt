package kxgear.bikeparts.integration.karoo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface RideStateSink {
    fun update(state: RideRecordingState)
}

object RideStateStore : RideStateSink {
    private val _state = MutableStateFlow(RideRecordingState.IDLE)
    val state: StateFlow<RideRecordingState> = _state.asStateFlow()

    override fun update(state: RideRecordingState) {
        _state.value = state
    }
}
