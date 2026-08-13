package com.molanko.avatargenerator.online

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.molanko.avatargenerator.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * Online-flavor UI extras: FAB + dialog to pull a skin from Mojang by name/UUID.
 * Offline flavor provides empty stubs with the same API surface.
 */
object OnlineSkinExtras {

    private const val TAG = "MojangSkin"

    @Composable
    fun FetchSkinFab(
        enabled: Boolean = true,
        onSkinLoaded: (Bitmap) -> Unit
    ) {
        var showDialog by remember { mutableStateOf(false) }

        FloatingActionButton(
            onClick = { if (enabled) showDialog = true },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_outline_person_search),
                contentDescription = stringResource(R.string.fetch_skin)
            )
        }

        if (showDialog) {
            FetchSkinDialog(
                onDismiss = { showDialog = false },
                onLoaded = { bmp ->
                    showDialog = false
                    onSkinLoaded(bmp)
                }
            )
        }
    }

    @Composable
    private fun FetchSkinDialog(
        onDismiss: () -> Unit,
        onLoaded: (Bitmap) -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        var errorText by remember { mutableStateOf<String?>(null) }
        
        var fetchJob by remember { mutableStateOf<Job?>(null) }

        fun cancelAndDismiss() {
            fetchJob?.cancel()
            onDismiss()
        }

        fun doFetch() {
            if (loading) return
            val q = query.trim()
            if (q.isEmpty()) {
                errorText = context.getString(R.string.fetch_empty)
                return
            }
            loading = true
            errorText = null

            fetchJob = scope.launch {
                try {
                    val result = MojangSkinFetcher.fetch(q)
                    onLoaded(result.bitmap)
                    val label = result.resolvedName ?: result.uuid
                    Toast.makeText(
                        context,
                        context.getString(R.string.fetch_ok, label),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "Fetch cancelled by user")
                } catch (e: MojangSkinFetcher.FetchError) {
                    val base = when (e) {
                        is MojangSkinFetcher.FetchError.InvalidInput ->
                            context.getString(R.string.fetch_invalid)
                        is MojangSkinFetcher.FetchError.PlayerNotFound ->
                            context.getString(R.string.fetch_not_found)
                        is MojangSkinFetcher.FetchError.NoSkin ->
                            context.getString(R.string.fetch_no_skin)
                        is MojangSkinFetcher.FetchError.Network ->
                            context.getString(R.string.fetch_network)
                    }
                    val detail = e.message?.takeIf { it.isNotBlank() && it != base }
                    errorText = if (detail != null) "$base\n$detail" else base
                    Log.e(TAG, "fetch failed: $base / ${e.message}", e)
                } catch (e: Exception) {
                    errorText = context.getString(R.string.fetch_network) +
                        "\n" + (e.message ?: e.javaClass.simpleName)
                    Log.e(TAG, "fetch unexpected", e)
                } finally {
                    loading = false
                }
            }
        }

        AlertDialog(
            onDismissRequest = { if (!loading) onDismiss() },
            icon = { Icon(
                painter = painterResource(id = R.drawable.ic_outline_cloud_download_24),
                contentDescription = null
                ) 
            },
            title = { Text(stringResource(R.string.fetch_skin_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.fetch_skin_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            errorText = null
                        },
                        label = { Text(stringResource(R.string.fetch_skin_label)) },
                        singleLine = true,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorText != null
                    )
                    if (errorText != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            errorText!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (loading) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.fetching))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { doFetch() }, enabled = !loading) {
                    Text(stringResource(R.string.fetch_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelAndDismiss() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
