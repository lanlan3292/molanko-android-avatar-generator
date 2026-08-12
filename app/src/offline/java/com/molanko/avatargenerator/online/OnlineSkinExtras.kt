package com.molanko.avatargenerator.online

import android.graphics.Bitmap
import androidx.compose.runtime.Composable

/**
 * Offline flavor: no Mojang / network skin fetch UI.
 */
object OnlineSkinExtras {
    @Composable
    fun FetchSkinFab(
        enabled: Boolean = true,
        onSkinLoaded: (Bitmap) -> Unit
    ) {
        // No-op in offline APK
    }
}
