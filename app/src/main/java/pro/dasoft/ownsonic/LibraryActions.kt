package pro.dasoft.ownsonic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Serializes UI actions while their durable writes run off the main thread. */
internal class LibraryActions(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch(Dispatchers.Main.immediate) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }
}

internal val LocalLibraryActions = staticCompositionLocalOf<LibraryActions> {
    error("Library actions must be provided by the app root.")
}
