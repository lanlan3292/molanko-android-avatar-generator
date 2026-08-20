package com.molanko.avatargenerator.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Holds UI + image state so data survives configuration changes. */
class AvatarViewModel : ViewModel() {
    var sourceBitmap by mutableStateOf<Bitmap?>(null)
    var resultBitmap by mutableStateOf<Bitmap?>(null)
    var isProcessing by mutableStateOf(false)

    var outlineMode by mutableIntStateOf(2)
    var outlinePreset by mutableStateOf("auto")
    var bgPreset by mutableStateOf("auto")
    var upscale48 by mutableStateOf(true)
    var fillBackground by mutableStateOf(true)
    var scale by mutableFloatStateOf(5f)
    var showOptions by mutableStateOf(true)

    var showOutlineCustom by mutableStateOf(false)
    var showBgCustom by mutableStateOf(false)
    var outlineCustomHex by mutableStateOf("#000000")
    var bgCustomHex by mutableStateOf("#FFFFFF")

    /** When true, average colour is derived from the skin; otherwise use averageColorHex. */
    var autoAverage by mutableStateOf(true)
    var averageColorHex by mutableStateOf("#808080")
}
