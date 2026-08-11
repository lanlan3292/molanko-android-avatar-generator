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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molanko.avatargenerator.processing.TextureProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Options
    var outlineMode by remember { mutableIntStateOf(0) }
    var outlinePreset by remember { mutableStateOf("auto_dark") }
    var bgPreset by remember { mutableStateOf("auto_light") }
    var upscale48 by remember { mutableStateOf(false) }
    var fillBackground by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(4f) }
    var showOptions by remember { mutableStateOf(true) }

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
                        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
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
                Toast.makeText(context, "无法加载图片", Toast.LENGTH_SHORT).show()
            }
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
                Toast.makeText(context, "处理失败：图片尺寸不足或格式错误", Toast.LENGTH_LONG).show()
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
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Molanko")
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
                if (success) "已保存到 Pictures/Molanko" else "保存失败",
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
                            "Molanko Avatar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Minecraft 皮肤 → 像素头像",
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
                        Icon(Icons.Default.Settings, contentDescription = "选项")
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
                        Icon(Icons.Default.Download, contentDescription = "保存")
                    }
                }
                LargeFloatingActionButton(
                    onClick = { imagePicker.launch("image/*") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "选择皮肤",
                        modifier = Modifier.size(32.dp)
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
            // Preview card
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
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isProcessing -> {
                            Text(
                                "处理中…",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        resultBitmap != null -> {
                            Image(
                                bitmap = resultBitmap!!.asImageBitmap(),
                                contentDescription = "生成结果",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "选择 Minecraft 皮肤开始",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "支持 64×64 / 64×32 标准皮肤",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
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
                            "生成选项",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(16.dp))

                        // Outline mode
                        Text("轮廓半径", style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(0, 1, 2, 3).forEachIndexed { index, value ->
                                SegmentedButton(
                                    selected = outlineMode == value,
                                    onClick = {
                                        outlineMode = value
                                        sourceBitmap?.let { src ->
                                            processImage(
                                                src, value, outlinePreset, bgPreset,
                                                upscale48, fillBackground, scale
                                            ) { resultBitmap = it }
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = 4
                                    )
                                ) {
                                    Text(if (value == 0) "无" else "$value")
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Outline color preset
                        Text("轮廓颜色", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "auto_dark" to "自动深",
                                "auto_darker" to "更深",
                                "auto_medium_dark" to "中深",
                                "#000000" to "纯黑"
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = outlinePreset == value,
                                    onClick = {
                                        outlinePreset = value
                                        sourceBitmap?.let { src ->
                                            processImage(
                                                src, outlineMode, value, bgPreset,
                                                upscale48, fillBackground, scale
                                            ) { resultBitmap = it }
                                        }
                                    },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Background preset
                        Text("背景颜色", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "auto_light" to "自动浅",
                                "auto_lighter" to "更浅",
                                "auto_medium_light" to "中浅",
                                "#ffffff" to "纯白"
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = bgPreset == value,
                                    onClick = {
                                        bgPreset = value
                                        sourceBitmap?.let { src ->
                                            processImage(
                                                src, outlineMode, outlinePreset, value,
                                                upscale48, fillBackground, scale
                                            ) { resultBitmap = it }
                                        }
                                    },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Toggles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("放大到 48×48", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = upscale48,
                                onCheckedChange = {
                                    upscale48 = it
                                    sourceBitmap?.let { src ->
                                        processImage(
                                            src, outlineMode, outlinePreset, bgPreset,
                                            it, fillBackground, scale
                                        ) { resultBitmap = it }
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("填充背景", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = fillBackground,
                                onCheckedChange = {
                                    fillBackground = it
                                    sourceBitmap?.let { src ->
                                        processImage(
                                            src, outlineMode, outlinePreset, bgPreset,
                                            upscale48, it, scale
                                        ) { resultBitmap = it }
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Scale slider
                        Text(
                            "最终缩放 ×${scale.toInt()}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 1f..16f,
                            steps = 14,
                            onValueChangeFinished = {
                                sourceBitmap?.let { src ->
                                    processImage(
                                        src, outlineMode, outlinePreset, bgPreset,
                                        upscale48, fillBackground, scale
                                    ) { resultBitmap = it }
                                }
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        FilledTonalButton(
                            onClick = {
                                sourceBitmap?.let { src ->
                                    processImage(
                                        src, outlineMode, outlinePreset, bgPreset,
                                        upscale48, fillBackground, scale
                                    ) { resultBitmap = it }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = sourceBitmap != null && !isProcessing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("重新生成")
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp)) // space for FAB
        }
    }
}
