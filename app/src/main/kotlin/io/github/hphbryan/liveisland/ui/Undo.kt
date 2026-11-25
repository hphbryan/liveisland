package io.github.hphbryan.liveisland.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import io.github.hphbryan.liveisland.R
import kotlinx.coroutines.launch

/** The app's one snackbar, so any screen can confirm what a tap did. */
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }

/**
 * Says what was just removed, with Undo. Deleting with one tap is quick, so getting it back is one tap too.
 * Call the result with the message and what Undo restores.
 */
@Composable
fun rememberUndo(): (message: String, restore: () -> Unit) -> Unit {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val undo = stringResource(R.string.undo)
    return { message, restore ->
        if (snackbar != null) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) restore()
            }
        }
    }
}
