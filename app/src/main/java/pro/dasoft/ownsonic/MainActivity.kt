package pro.dasoft.ownsonic

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.work.WorkManager
import pro.dasoft.ownsonic.data.LibraryStore

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var connectionError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { OwnsonicApp(LibraryStore.get(this), WorkManager.getInstance(this), controller, connectionError) }
    }

    override fun onStart() {
        super.onStart()
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture === future) {
                try { controller = future.get(); connectionError = null }
                catch (_: Exception) { connectionError = getString(R.string.player_connection_error) }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }
}
