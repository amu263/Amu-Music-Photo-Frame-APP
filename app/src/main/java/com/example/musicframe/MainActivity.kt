package com.example.musicframe

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicframe.domain.model.FrameControlAction
import com.example.musicframe.image.PhotoMetadata
import com.example.musicframe.image.PhotoMetadataReader
import com.example.musicframe.ui.screen.exportFormatSelector
import com.example.musicframe.ui.screen.frameControls
import com.example.musicframe.ui.theme.musicFrameTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            musicFrameTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val permissionList = remember {
                        buildList {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.READ_MEDIA_IMAGES)
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        }
                    }
                    val permissions = rememberMultiplePermissionsState(permissions = permissionList)
                    LaunchedEffect(Unit) {
                        permissions.launchMultiplePermissionRequest()
                    }
                    musicFrameScreen(
                        viewModel = viewModel(factory = MusicFrameViewModelFactory(application)),
                        permissionsGranted = permissions.allPermissionsGranted
                    )
                }
            }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun musicFrameScreen(
    viewModel: MusicFrameViewModel,
    permissionsGranted: Boolean
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> viewModel.onImageSelected(uri) }
    )
    // 旧版图库选择器，可以保留原始照片的 EXIF 数据（包括 GPS）
    val pickImageLauncherLegacy = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> viewModel.onImageSelected(uri) }
    )
    var showLegacyPickerDialog by remember { mutableStateOf(false) }
    val pickFontLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        viewModel.onFontSelected(uri)
    }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf<String?>(null) }

    var frameColorHex by remember { mutableStateOf("") }
    var tempCustomColorHex by remember { mutableStateOf("") }
    var headphoneColorHex by remember { mutableStateOf("") }

    // 同步临时颜色到状态（仅当状态变化且 temp 为空时同步，不影响用户输入和清除操作）
    LaunchedEffect(state.customFrameColorHex) {
        // 仅在 tempCustomColorHex 为空时才同步，避免覆盖用户输入或清除操作
        if (tempCustomColorHex.isEmpty() && state.customFrameColorHex.isNotEmpty()) {
            tempCustomColorHex = state.customFrameColorHex
            frameColorHex = state.customFrameColorHex
        } else if (state.customFrameColorHex.isEmpty() && tempCustomColorHex.isNotEmpty()) {
            // 状态已清除但 temp 还有值，同步清除
            tempCustomColorHex = ""
            frameColorHex = ""
        }
    }

    // 检查 NotificationListenerService 是否已启用
    var notificationListenerEnabled by remember { mutableStateOf(false) }

    // 权限状态变化时重新检查
    LaunchedEffect(Unit) {
        notificationListenerEnabled = isNotificationListenerEnabled(context)
    }

    // 监听权限变化，定期检查
    LaunchedEffect(state.musicMetadata) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            notificationListenerEnabled = isNotificationListenerEnabled(context)
        }
    }

    val scrollState = rememberScrollState()
    val shareRequest = state.pendingShareRequest
    if (shareRequest != null) {
        LaunchedEffect(shareRequest) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = shareRequest.mimeType
                putExtra(Intent.EXTRA_STREAM, shareRequest.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val result = runCatching {
                val chooser = Intent.createChooser(shareIntent, "分享图片")
                if (context !is Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            if (result.isFailure) {
                viewModel.onShareFailed(result.exceptionOrNull()?.localizedMessage)
            }
            viewModel.onShareRequestHandled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        titleSection()
        permissionWarning(permissionsGranted)
        imagePickerButton(
            selectedImageUri = state.selectedImageUri,
            onPickImage = {
                showLegacyPickerDialog = true
            }
        )
        notificationPermissionButton(
            isEnabled = notificationListenerEnabled,
            onClick = {
                // 点击时重新检查权限状态
                notificationListenerEnabled = isNotificationListenerEnabled(context)
                if (notificationListenerEnabled) {
                    // 如果已开启，显示提示
                    Toast.makeText(context, "正在播放权限已开启", Toast.LENGTH_SHORT).show()
                } else {
                    showNotificationDialog = true
                }
            }
        )
        fontPickerRow(
            customFontName = state.customFontName,
            onPickFont = {
                pickFontLauncher.launch(
                    arrayOf("font/ttf", "application/x-font-ttf", "application/octet-stream")
                )
            },
            onResetFont = { viewModel.useDefaultFont() }
        )
        framedImagePreview(
            bitmap = state.framedBitmap
        )

        frameControls(
            state = state,
            frameColorHex = frameColorHex,
            tempCustomColorHex = tempCustomColorHex,
            headphoneColorHex = headphoneColorHex,
            onFrameColorHexChange = { tempCustomColorHex = it },
            onApplyFrameHex = { 
                viewModel.setCustomFrameColor(tempCustomColorHex)
                frameColorHex = tempCustomColorHex
                viewModel.rebuildFrame()
            },
            onClearFrameHex = { 
                tempCustomColorHex = ""
                frameColorHex = ""
                viewModel.setCustomFrameColor("")
                viewModel.rebuildFrame()
            },
            onHeadphoneColorHexChange = { headphoneColorHex = it },
            onApplyHeadphoneHex = { },
            onAction = { action ->
                when (action) {
                    is FrameControlAction.SetFrameColorMode -> viewModel.setFrameColorMode(action.mode)
                    is FrameControlAction.SetCustomFrameColor -> viewModel.setCustomFrameColor(action.colorHex)
                    is FrameControlAction.SetDarkBackground -> viewModel.setDarkBackground(action.enabled)
                    is FrameControlAction.ToggleHeadphone -> viewModel.toggleHeadphoneInfo(action.enabled)
                    is FrameControlAction.SetMode -> viewModel.updateFrameMode(action.mode)
                }
            }
        )

        exportFormatSelector(
            selected = state.exportFormat,
            onSelected = viewModel::onExportFormatSelected
        )

        exportButtons(
            framedBitmap = state.framedBitmap,
            isSaving = state.isSaving,
            isExporting = state.isExporting,
            onSave = { viewModel.saveFramedImage() },
            onExport = { viewModel.shareFramedImage() }
        )

        exportLogButton()

        viewLogButton(
            onClick = {
                logContent = PhotoMetadataReader.readLogContent(context)
                showLogDialog = true
            }
        )

        motionPhotoButton(
            photoMetadata = state.photoMetadata,
            framedBitmap = state.framedBitmap,
            isExporting = state.isExporting,
            onExport = { viewModel.exportMotionPhoto() }
        )

        state.message?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showNotificationDialog) {
        notificationPermissionDialog(
            onDismiss = { showNotificationDialog = false },
            onConfirm = {
                showNotificationDialog = false
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )
    }

    if (showLogDialog) {
        logContentDialog(
            logContent = logContent,
            onDismiss = { showLogDialog = false },
            onCopy = { content ->
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Log Content", content))
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            },
            onShare = { content ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content)
                    putExtra(Intent.EXTRA_SUBJECT, "music-frame-debug.log")
                }
                val chooser = Intent.createChooser(shareIntent, "分享日志")
                if (context !is Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        )
    }

    // 选择图片方式对话框
    if (showLegacyPickerDialog) {
        AlertDialog(
            onDismissRequest = { showLegacyPickerDialog = false },
            title = { Text("选择图片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("请选择图片来源：")
                    Button(
                        onClick = {
                            showLegacyPickerDialog = false
                            pickImageLauncherLegacy.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📷 相册（推荐，可保留 GPS）")
                    }
                    Button(
                        onClick = {
                            showLegacyPickerDialog = false
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🖼️ 照片选择器（新版）")
                    }
                    Text(
                        text = "提示：相册选择可以保留照片的 GPS 位置信息，照片选择器会清除位置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLegacyPickerDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun titleSection() {
    Text(
        text = "音乐取色相框",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun permissionWarning(permissionsGranted: Boolean) {
    if (!permissionsGranted) {
        Text(text = "请授予媒体与通知权限以读取歌曲信息")
    }
}

@Composable
private fun imagePickerButton(
    selectedImageUri: Uri?,
    onPickImage: () -> Unit
) {
    Button(onClick = onPickImage) {
        Text(text = if (selectedImageUri == null) "选择图片" else "重新选择图片")
    }
}

@Composable
private fun notificationPermissionButton(
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Button(onClick = onClick) {
        Text(
            if (isEnabled) "正在播放权限已开启 ✓" else "开启正在播放权限"
        )
    }
}

@Composable
private fun fontPickerRow(
    customFontName: String?,
    onPickFont: () -> Unit,
    onResetFont: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onPickFont) {
            Text(text = customFontName?.let { "当前字体: $it (点此更换)" } ?: "导入TTF字体")
        }
        OutlinedButton(onClick = onResetFont) {
            Text("恢复默认字体")
        }
    }
}

@Composable
private fun framedImagePreview(
    bitmap: Bitmap?
) {
    bitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline)
        )
    } ?: run {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("等待选择图片…")
        }
    }
}

@Composable
private fun exportButtons(
    framedBitmap: Bitmap?,
    isSaving: Boolean,
    isExporting: Boolean,
    onSave: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = onSave,
            enabled = framedBitmap != null && !isSaving
        ) {
            Text(if (isSaving) "保存中…" else "保存到相册")
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onExport,
            enabled = framedBitmap != null && !isExporting
        ) {
            Text(if (isExporting) "导出中…" else "导出图片")
        }
    }
}

@Composable
private fun motionPhotoButton(
    photoMetadata: PhotoMetadata?,
    framedBitmap: Bitmap?,
    isExporting: Boolean,
    onExport: () -> Unit
) {
    if (photoMetadata?.isMotionPhoto == true && photoMetadata.motionVideoOffset != null) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onExport,
            enabled = framedBitmap != null && !isExporting
        ) {
            Text("导出实况相框")
        }
    }
}

@Composable
private fun notificationPermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("前往设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("读取歌曲信息") },
        text = { Text("请在系统设置中授权「正在播放」权限，以便读取音乐元数据和封面颜色。") }
    )
}

@Composable
private fun exportLogButton() {
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }

    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (isExporting) return@OutlinedButton
            isExporting = true
            try {
                val logFile = PhotoMetadataReader.copyLogToDownloads(context)
                if (logFile != null) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        logFile
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, "分享日志")
                    if (context !is Activity) {
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                    Toast.makeText(context, "日志已复制到 Downloads 目录", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "日志文件不存在", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isExporting = false
            }
        },
        enabled = !isExporting
    ) {
        Text(if (isExporting) "导出中…" else "导出日志")
    }
}

@Composable
private fun viewLogButton(onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Text("查看日志")
    }
}

@Composable
private fun logContentDialog(
    logContent: String?,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("日志内容") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (logContent.isNullOrEmpty()) {
                    Text("日志文件不存在或为空")
                } else {
                    Text(
                        text = logContent,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { logContent?.let { onCopy(it) } },
                enabled = !logContent.isNullOrEmpty()
            ) {
                Text("复制")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { logContent?.let { onShare(it) } },
                enabled = !logContent.isNullOrEmpty()
            ) {
                Text("分享")
            }
        }
    )
}

/**
 * 检查 NotificationListenerService 是否是否已启用
 */
private fun isNotificationListenerEnabled(context: Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false

    return flat.split(":").any {
        ComponentName.unflattenFromString(it)?.packageName == packageName
    }
}
