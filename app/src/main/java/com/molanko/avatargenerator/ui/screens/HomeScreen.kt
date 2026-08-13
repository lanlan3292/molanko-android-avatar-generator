package com.molanko.avatargenerator.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.molanko.avatargenerator.R
import com.molanko.avatargenerator.online.OnlineSkinExtras
import com.molanko.avatargenerator.processing.TextureProcessor
import com.molanko.avatargenerator.ui.AvatarViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(vm: AvatarViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var processJob by remember { mutableStateOf<Job?>(null) }
    var scaleSliderPos by remember { mutableFloatStateOf(vm.scale) }
    var outlineSliderPos by remember { mutableFloatStateOf(vm.outlineMode.toFloat()) }

    LaunchedEffect(vm.scale) {
        if (kotlin.math.abs(scaleSliderPos - vm.scale) > 0.01f) {
            scaleSliderPos = vm.scale
        }
    }

    var sourceBitmap by vm::sourceBitmap
    var resultBitmap by vm::resultBitmap
    var isProcessing by vm::isProcessing

    var outlineMode by vm::outlineMode

    LaunchedEffect(outlineMode) {
        if (kotlin.math.abs(outlineSliderPos - outlineMode.toFloat()) > 0.01f) {
            outlineSliderPos = outlineMode.toFloat()
        }
    }

    var outlinePreset by vm::outlinePreset
    var bgPreset by vm::bgPreset
    var upscale48 by vm::upscale48
    var fillBackground by vm::fillBackground
    var scale by vm::scale
    var showOptions by vm::showOptions

    var showOutlineCustom by vm::showOutlineCustom
    var showBgCustom by vm::showBgCustom
    var outlineCustomHex by vm::outlineCustomHex
    var bgCustomHex by vm::bgCustomHex

    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var newBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(resultBitmap) {
        resultBitmap?.let { new ->
            if (displayBitmap == null) {
                displayBitmap = new
            } else {
                newBitmap = new
            }
            alpha.snapTo(0f)
            alpha.animateTo(1f, animationSpec = tween(durationMillis = 500))
            displayBitmap = new
            newBitmap = null
        }
    }

    LaunchedEffect(alpha.value) {
        if (alpha.value == 1f && newBitmap != null) {
            displayBitmap = newBitmap
            newBitmap = null
        }
    }

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
        processJob?.cancel()
        isProcessing = true
        processJob = scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
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
                }
                ensureActive()
                if (result != null) onResult(result)
                else Toast.makeText(context, context.getString(R.string.process_failed), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.process_failed), Toast.LENGTH_LONG).show()
            } finally {
                if (isActive) isProcessing = false
            }
        }
    }

    fun triggerProcess() {
        sourceBitmap?.let { src ->
            processImage(src, outlineMode, outlinePreset, bgPreset, upscale48, fillBackground, 1f) { resultBitmap = it }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
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
                } catch (e: Exception) { null }
            }
            if (bmp != null) {
                sourceBitmap = bmp
                processImage(bmp, outlineMode, outlinePreset, bgPreset, upscale48, fillBackground, 1f) { resultBitmap = it }
            } else {
                Toast.makeText(context, context.getString(R.string.load_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    var isSaving by remember { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bmp = resultBitmap ?: return@rememberLauncherForActivityResult
        isSaving = true
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val toWrite = if (scale <= 1f) {
                        bmp
                    } else {
                        TextureProcessor.applyScale(bmp, scale)
                    }
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        toWrite.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } ?: return@withContext false
                    if (toWrite !== bmp) {
                        toWrite.recycle()
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            isSaving = false
            Toast.makeText(
                context,
                context.getString(if (success) R.string.saved_ok else R.string.save_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun requestSave() {
        if (resultBitmap == null || isSaving) return
        createDocument.launch("molanko_avatar_${System.currentTimeMillis()}.png")
    }

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val transitionPx = with(density) { 80.dp.toPx() }
    val scrollRatio by remember {
        derivedStateOf {
            (scrollState.value / transitionPx).coerceIn(0f, 1f)
        }
    }
    val topBarBgColor = lerp(
        start = MaterialTheme.colorScheme.surface,
        stop = MaterialTheme.colorScheme.surfaceContainer,
        fraction = scrollRatio
    )
    val topBarElevation = (scrollRatio * 3).dp

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.app_subtitle), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarBgColor),
                modifier = Modifier
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.navigationBarsPadding()) {
                if (resultBitmap != null) {
                    FloatingActionButton(
                        onClick = { requestSave() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) { Icon(painter = painterResource(id = R.drawable.ic_outlined_file_download), contentDescription = stringResource(R.string.save)) }
                }
                OnlineSkinExtras.FetchSkinFab(enabled = !isProcessing) { bmp ->
                    sourceBitmap = bmp
                    processImage(
                        bmp, outlineMode, outlinePreset, bgPreset,
                        upscale48, fillBackground, 1f
                    ) { resultBitmap = it }
                }
                MediumFloatingActionButton(
                    onClick = { imagePicker.launch("image/*") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) { Icon(painter = painterResource(id = R.drawable.ic_outlined_image), contentDescription = stringResource(R.string.pick_image)) }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(bottom = 20.dp),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                val blurRadius by animateDpAsState(
                    targetValue = if (isProcessing) 16.dp else 0.dp,
                    animationSpec = tween(durationMillis = 600),
                    label = "BlurAnimation"
                )
                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.fillMaxSize().blur(radius = blurRadius), contentAlignment = Alignment.Center) {
                        if (displayBitmap != null) {
                            Image(
                                bitmap = displayBitmap!!.asImageBitmap(),
                                contentDescription = stringResource(R.string.result_preview),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.None
                            )
                        } else if (!isProcessing && newBitmap == null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_outlined_image),
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.select_skin), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.select_skin_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        if (newBitmap != null) {
                            Image(
                                bitmap = newBitmap!!.asImageBitmap(),
                                contentDescription = stringResource(R.string.result_preview),
                                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value },
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.None
                            )
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isProcessing,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f)
                    ) {
                        ContainedLoadingIndicator(
                            modifier = Modifier.size(64.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showOptions = !showOptions },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.options), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (showOptions) "\u25BE" else "\u25B8", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedVisibility(
                        visible = showOptions,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.outline_radius), style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = outlineSliderPos,
                                onValueChange = { outlineSliderPos = it },
                                valueRange = 0f..2f,
                                onValueChangeFinished = {
                                    val target = outlineSliderPos.roundToInt().toFloat().coerceIn(0f, 2f)
                                    scope.launch {
                                        val anim = Animatable(outlineSliderPos)
                                        anim.animateTo(
                                            targetValue = target,
                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                        ) { outlineSliderPos = value }
                                        outlineMode = target.toInt()
                                        outlineSliderPos = target
                                        triggerProcess()
                                    }
                                }
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.outline_color), style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                listOf("auto_dark" to R.string.auto, "auto_darker" to R.string.auto_darker, "auto_medium_dark" to R.string.auto_lighter).forEach { (value, labelRes) ->
                                    FilterChip(
                                        selected = outlinePreset == value && !showOutlineCustom,
                                        onClick = { outlinePreset = value; showOutlineCustom = false; triggerProcess() },
                                        label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                                FilterChip(selected = showOutlineCustom, onClick = { showOutlineCustom = true }, label = { Text(stringResource(R.string.custom), fontSize = 12.sp) })
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
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
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
                                    }) { Text(stringResource(R.string.apply_custom)) }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.bg_color), style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("auto_light" to R.string.auto, "auto_lighter" to R.string.auto_lighter, "auto_medium_light" to R.string.auto_darker).forEach { (value, labelRes) ->
                                    FilterChip(
                                        selected = bgPreset == value && !showBgCustom,
                                        onClick = { bgPreset = value; showBgCustom = false; triggerProcess() },
                                        label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                FilterChip(selected = showBgCustom, onClick = { showBgCustom = true }, label = { Text(stringResource(R.string.custom), fontSize = 12.sp) })
                                listOf("#E53935", "#1E88E5", "#43A047", "#FB8C00", "#8E24AA", "#000000").forEach { hex ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(parseColorSafe(hex))
                                            .border(
                                                width = if (showBgCustom && bgCustomHex.equals(hex, true)) 2.dp else 1.dp,
                                                color = if (showBgCustom && bgCustomHex.equals(hex, true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
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
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
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
                                    }) { Text(stringResource(R.string.apply_custom)) }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.upscale_48), style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = upscale48, onCheckedChange = { upscale48 = it; triggerProcess() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.fill_background), style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = fillBackground, onCheckedChange = { fillBackground = it; triggerProcess() })
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.final_scale, scaleSliderPos.roundToInt()), style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = scaleSliderPos,
                                onValueChange = { scaleSliderPos = it },
                                valueRange = 1f..50f,
                                onValueChangeFinished = {
                                    val target = scaleSliderPos.roundToInt().toFloat().coerceIn(1f, 50f)
                                    scope.launch {
                                        val anim = Animatable(scaleSliderPos)
                                        anim.animateTo(
                                            targetValue = target,
                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                        ) { scaleSliderPos = value }
                                        scale = target
                                        scaleSliderPos = target
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { triggerProcess() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = sourceBitmap != null && !isProcessing
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_default_refresh), contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.regenerate))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars.add(WindowInsets(bottom = 180.dp))))
        }
    }

    if (isSaving) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.saving)) },
            text = {
                Column {
                    Text(stringResource(R.string.saving_hint))
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {}
        )
    }
}

private fun isValidHex(hex: String): Boolean {
    val h = hex.trim().removePrefix("#")
    return h.length == 6 && h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

private fun normalizeHex(hex: String): String = "#" + hex.trim().removePrefix("#")

private fun parseColorSafe(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(normalizeHex(hex)))
} catch (_: Exception) {
    Color.Gray
}
