package com.molanko.avatargenerator.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molanko.avatargenerator.R
import com.molanko.avatargenerator.processing.TextureProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Options — default upscale48 = true
    var outlineMode by remember { mutableIntStateOf(0) }
    var outlinePreset by remember { mutableStateOf("auto_dark") }
    var bgPreset by remember { mutableStateOf("auto_light") }
    var upscale48 by remember { mutableStateOf(true) }
    var fillBackground by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(4f) }
    var showOptions by remember { mutableStateOf(true) }

    // Custom color state
    var showOutlineCustom by remember { mutableStateOf(false) }
    var showBgCustom by remember { mutableStateOf(false) }
    var outlineCustomHex by remember { mutableStateOf("#000000") }
    var bgCustomHex by remember { mutableStateOf("#FFFFFF") }

    fun processImage(
        src: Bitmap,
        outline: Int,
        outlineColor: String,
        bgColor: String,
        up48: Boolean,
        fillBg: Boolean,
        sc: Float,
        onResult: (Bitmap) -> Unit
    ) {
        isProcessing = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    TextureProcessor.processTexture(
                        src,
                        TextureProcessor.ProcessOptions(
                            outlineMode = outline,
                            outlineColor = outlineColor,
                            bgColor = bgColor,
                            upscale48 = up48,
                            fillBackground = fillBg,
                            scale = sc
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            isProcessing = false
            if (result != null) {
                onResult(result)
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.process_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun triggerProcess() {
        sourceBitmap?.let { src ->
            processImage(
                src, outlineMode, outlinePreset, bgPreset,
                upscale48, fillBackground, scale
            ) { resultBitmap = it }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (bmp != null) {
                sourceBitmap = bmp
                processImage(
                    bmp,
                    outlineMode, outlinePreset, bgPreset,
                    upscale48, fillBackground, scale
                ) { result ->
                    resultBitmap = result
                }
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.load_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun saveResult(bmp: Bitmap) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val filename = "molanko_avatar_${System.currentTimeMillis()}.png"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_PICTURES + "/Molanko"
                            )
                        }
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { out ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        true
                    } ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            Toast.makeText(
                context,
                context.getString(if (success) R.string.saved_ok else R.string.save_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                actions = {
                    IconButton(onClick = { showOptions = !showOptions }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.options))
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (resultBitmap != null) {
                    FloatingActionButton(
                        onClick = { resultBitmap?.let { saveResult(it) } },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.save))
                    }
                }
                FloatingActionButton(
                    onClick = { imagePicker.launch("image/*") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = stringResource(R.string.pick_image)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview card — image fills the square for proper rounded corners
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Keep previous result while processing to avoid flicker
                    if (resultBitmap != null) {
                        Image(
                            bitmap = resultBitmap!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.result_preview),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            // Nearest-neighbor: no bilinear blur at low scale
                            filterQuality = FilterQuality.None
                        )
                    } else if (!isProcessing) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.select_skin),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.select_skin_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // Expressive Loading Indicator overlay while processing
                    if (isProcessing) {
                        //Box(
                        //    modifier = Modifier
                        //        .matchParentSize()
                        //        .background(Color.Black.copy(alpha = 0.35f)
                        //        )
                        //)
                        ContainedLoadingIndicator(
                            modifier = Modifier.size(64.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Options panel
            AnimatedVisibility(
                visible = showOptions,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.options),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(16.dp))

                        // Outline mode
                        Text(stringResource(R.string.outline_radius), style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(0, 1, 2).forEachIndexed { index, value ->
                                SegmentedButton(
                                    selected = outlineMode == value,
                                    onClick = {
                                        outlineMode = value
                                        triggerProcess()
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = 3
                                    )
                                ) {
                                    Text(if (value == 0) stringResource(R.string.none) else "$value")
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Outline color
                        Text(stringResource(R.string.outline_color), style = MaterialTheme.typography.labelLarge)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                "auto_dark" to R.string.auto_dark,
                                "auto_darker" to R.string.auto_darker,
                                "auto_medium_dark" to R.string.auto_medium_dark,
                            ).forEach { (value, labelRes) ->
                                FilterChip(
                                    selected = outlinePreset == value && !showOutlineCustom,
                                    onClick = {
                                        outlinePreset = value
                                        showOutlineCustom = false
                                        triggerProcess()
                                    },
                                    label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = showOutlineCustom,
                                onClick = { showOutlineCustom = true },
                                label = { Text(stringResource(R.string.custom), fontSize = 12.sp) }
                            )
                            // Color swatches for quick custom pick
                            listOf("#E53935", "#1E88E5", "#43A047", "#FB8C00", "#8E24AA", "#000000").forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(parseColorSafe(hex))
                                        .border(
                                            width = if (showOutlineCustom && outlineCustomHex.equals(hex, true)) 2.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            outlineCustomHex = hex
                                            outlinePreset = hex
                                            showOutlineCustom = true
                                            triggerProcess()
                                        }
                                )
                            }
                        }
                        AnimatedVisibility(visible = showOutlineCustom) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = outlineCustomHex,
                                    onValueChange = { outlineCustomHex = it },
                                    label = { Text(stringResource(R.string.hex_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (isValidHex(outlineCustomHex)) {
                                            outlinePreset = normalizeHex(outlineCustomHex)
                                            triggerProcess()
                                        }
                                    })
                                )
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(onClick = {
                                    if (isValidHex(outlineCustomHex)) {
                                        outlinePreset = normalizeHex(outlineCustomHex)
                                        triggerProcess()
                                    }
                                }) {
                                    Text(stringResource(R.string.apply_custom))
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Background color
                        Text(stringResource(R.string.bg_color), style = MaterialTheme.typography.labelLarge)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                "auto_light" to R.string.auto_light,
                                "auto_lighter" to R.string.auto_lighter,
                                "auto_medium_light" to R.string.auto_medium_light,
                            ).forEach { (value, labelRes) ->
                                FilterChip(
                                    selected = bgPreset == value && !showBgCustom,
                                    onClick = {
                                        bgPreset = value
                                        showBgCustom = false
                                        triggerProcess()
                                    },
                                    label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            FilterChip(
                                selected = showBgCustom,
                                onClick = { showBgCustom = true },
                                label = { Text(stringResource(R.string.custom), fontSize = 12.sp) }
                            )
                            listOf("#FFFFFF", "#F5F5F5", "#E3F2FD", "#E8F5E9", "#FFF3E0", "#FCE4EC").forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(parseColorSafe(hex))
                                        .border(
                                            width = if (showBgCustom && bgCustomHex.equals(hex, true)) 2.dp else 1.dp,
                                            color = if (showBgCustom && bgCustomHex.equals(hex, true))
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            bgCustomHex = hex
                                            bgPreset = hex
                                            showBgCustom = true
                                            triggerProcess()
                                        }
                                )
                            }
                        }
                        AnimatedVisibility(visible = showBgCustom) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = bgCustomHex,
                                    onValueChange = { bgCustomHex = it },
                                    label = { Text(stringResource(R.string.hex_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (isValidHex(bgCustomHex)) {
                                            bgPreset = normalizeHex(bgCustomHex)
                                            triggerProcess()
                                        }
                                    })
                                )
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(onClick = {
                                    if (isValidHex(bgCustomHex)) {
                                        bgPreset = normalizeHex(bgCustomHex)
                                        triggerProcess()
                                    }
                                }) {
                                    Text(stringResource(R.string.apply_custom))
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Toggles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.upscale_48), style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = upscale48,
                                onCheckedChange = {
                                    upscale48 = it
                                    triggerProcess()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.fill_background), style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = fillBackground,
                                onCheckedChange = {
                                    fillBackground = it
                                    triggerProcess()
                                }
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Scale slider
                        Text(
                            stringResource(R.string.final_scale, scale.toInt()),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 1f..50f,
                            steps = 48,
                            onValueChangeFinished = { triggerProcess() }
                        )

                        Spacer(Modifier.height(8.dp))

                        FilledTonalButton(
                            onClick = { triggerProcess() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = sourceBitmap != null && !isProcessing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.regenerate))
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

private fun isValidHex(hex: String): Boolean {
    val h = hex.trim().removePrefix("#")
    return h.length == 6 && h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

private fun normalizeHex(hex: String): String {
    val h = hex.trim().removePrefix("#")
    return "#$h"
}

private fun parseColorSafe(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(normalizeHex(hex)))
    } catch (_: Exception) {
        Color.Gray
    }
}
