package top.abdl_space.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import top.abdl_space.app.core.data.nbw.NbwRepository
import top.abdl_space.app.core.data.nbw.NbwSnapshot

/**
 * NBW discovery entry state. The repository owns session observation and all network discipline
 * (signed-in prefetch, TTL, single-flight, lease generation); this ViewModel only forwards the
 * snapshot and the two entry-side controls. There is no consent or posting path here.
 */
@HiltViewModel
class V4NbwViewModel @Inject constructor(
    private val nbw: NbwRepository,
) : ViewModel() {
    val snapshot: StateFlow<NbwSnapshot> = nbw.snapshot

    /** Entry becomes visible: re-verify only when the 5-minute TTL elapsed; guests never send. */
    fun refreshIfExpired() {
        viewModelScope.launch { nbw.refreshIfExpired() }
    }

    /** Explicit user action (re-check button): bypasses the TTL, still single-flight/fail-closed. */
    fun refresh() {
        viewModelScope.launch { nbw.refreshNow() }
    }
}
