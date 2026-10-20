package pro.dasoft.ownsonic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import pro.dasoft.ownsonic.data.LibraryStore

@Composable
internal fun LibraryRecoveryScreen(store: LibraryStore, actions: LibraryActions, syncing: Boolean) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.library_unavailable))
    Button(enabled = !actions.busy && !syncing, onClick = {
        actions.run { store.onIo { retryLoadingLibrary() } }
    }) { Text(stringResource(R.string.retry_library)) }
    OutlinedButton(enabled = !actions.busy && !syncing, onClick = { confirmReset = true }) {
        Text(stringResource(R.string.reset_library))
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.reset_library)) },
        text = { Text(stringResource(R.string.reset_library_confirmation)) },
        confirmButton = {
            TextButton(enabled = !actions.busy && !syncing, onClick = {
                confirmReset = false
                actions.run { store.onIo { resetDamagedLibrary() } }
            }) { Text(stringResource(R.string.reset_library)) }
        },
        dismissButton = {
            TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
