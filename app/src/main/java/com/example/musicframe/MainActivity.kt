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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicframe.domain.model.FrameControlAction
import com.example.musicframe.image.PhotoMetadata
import com.example.musicframe.image.PhotoMetadataReader
import com.example.musicframe.media.MusicMetadataBroadcaster
import com.example.musicframe.media.NowPlayingListenerService
import com.example.musicframe.ui.screen.exportFormatSelector
import com.example.musicframe.ui.screen.frameControls
import com.example.musicframe.ui.theme.FrameColors
import com.example.musicframe.ui.theme.SaltColors
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
    
    // 图片选择器
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> viewModel.onImageSelected(uri) }
    )
    val pickImageLauncherLegacy = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> viewModel.onImageSelected(uri) }
    )
    var showLegacyPickerDialog by remember { mutableStateOf(false) }
    
    // 字体选择器
    val pickFontLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        viewModel.onFontSelected(uri)
    }
    
    // 通知权限
    var showNotificationDialog by remember { mutableStateOf(false) }
    
    // 日志
    var showLogDialog by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf<String?>(null) }
    
    // 颜色状态
    var frameColorHex by remember { mutableStateOf("") }
    var tempCustomColorHex by remember { mutableStateOf("") }
    var headphoneColorHex by remember { mutableStateOf("") }
    
    // 同步颜色
    LaunchedEffect(state.customFrameColorHex) {
        if (tempCustomColorHex.isEmpty() && state.customFrameColorHex.isNotEmpty()) {
            tempCustomColorHex = state.customFrameColorHex
            frameColorHex = state.customFrameColorHex
        } else if (state.customFrameColorHex.isEmpty() && tempCustomColorHex.isNotEmpty()) {
            tempCustomColorHex = ""
            frameColorHex = ""
        }
    }
    
    // 通知权限状态
    var permissionCheckResult by remember { mutableStateOf(NotificationPermissionManager.PermissionCheckResult.NOT_GRANTED) }
    var lastHadMetadata by remember { mutableStateOf(false) }
    
    // 生命周期监听
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val result = NotificationPermissionManager.isPermissionActuallyGranted(context)
                permissionCheckResult = result
                lastHadMetadata = MusicMetadataBroadcaster.hasRecentMetadata()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 初始检查
    LaunchedEffect(Unit) {
        val result = NotificationPermissionManager.isPermissionActuallyGranted(context)
        permissionCheckResult = result
        lastHadMetadata = MusicMetadataBroadcaster.hasRecentMetadata()
    }
    
    // 定期检查
    LaunchedEffect(state.musicMetadata) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            val currentHasMetadata = MusicMetadataBroadcaster.hasRecentMetadata()
            if (lastHadMetadata && !currentHasMetadata) {
                val result = NotificationPermissionManager.isPermissionActuallyGranted(context)
                permissionCheckResult = result
            }
            lastHadMetadata = currentHasMetadata
            val result = NotificationPermissionManager.isPermissionActuallyGranted(context)
            if (result != permissionCheckResult) {
                permissionCheckResult = result
            }
        }
    }
    
    // 分享请求
    val scrollState = rememberScrollState()
    val shareRequest = state.pendingShareRequest
    if (shareRequest != null) {
        LaunchedEffect(shareRequest) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = shareRequest.mimeType
                putExtra(Intent.EXTRA_STREAM, shareRequest.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                val chooser = Intent.createChooser(shareIntent, "分享图片")
                if (context !is Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }.onFailure {
                viewModel.onShareFailed(it.localizedMessage)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部标题栏
        topBar()
        
        // 图片预览区
        imagePreviewSection(
            bitmap = state.framedBitmap,
            selectedImageUri = state.selectedImageUri,
            onPickImage = { showLegacyPickerDialog = true }
        )
        
        // 快捷操作区
        quickActionsRow(
            permissionGranted = permissionsGranted,
            permissionCheckResult = permissionCheckResult,
            onPermissionClick = {
                handleNotificationPermissionClick(
                    context = context,
                    currentResult = permissionCheckResult,
                    onShowDialog = { showNotificationDialog = true },
                    onRebindService = { NotificationPermissionManager.requestServiceRebind(context) },
                    onUpdateResult = { result -> permissionCheckResult = result }
                )
            },
            onFontClick = {
                pickFontLauncher.launch(arrayOf("font/ttf", "application/x-font-ttf", "application/octet-stream"))
            },
            hasCustomFont = state.customFontName != null
        )
        
        // 相框控制面板
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
        
        // 导出格式选择
        exportFormatSelector(
            selected = state.exportFormat,
            onSelected = viewModel::onExportFormatSelected
        )
        
        // 导出按钮组
        exportButtonsRow(
            framedBitmap = state.framedBitmap,
            isSaving = state.isSaving,
            isExporting = state.isExporting,
            onSave = { viewModel.saveFramedImage() },
            onExport = { viewModel.shareFramedImage() }
        )
        
        // 底部工具按钮
        bottomToolsRow(
            onLogClick = {
                logContent = PhotoMetadataReader.readLogContent(context)
                showLogDialog = true
            },
            onMotionPhotoExport = state.photoMetadata?.isMotionPhoto == true,
            onExportMotionPhoto = { viewModel.exportMotionPhoto() }
        )
    }
    
    // 对话框
    if (showNotificationDialog) {
        notificationPermissionDialog(
            onDismiss = { showNotificationDialog = false },
            onConfirm = {
                showNotificationDialog = false
                context.startActivity(NotificationPermissionManager.getSettingsIntent(context))
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
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
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
    
    if (showLegacyPickerDialog) {
        imageSourceDialog(
            onDismiss = { showLegacyPickerDialog = false },
            onSelectLegacy = {
                showLegacyPickerDialog = false
                pickImageLauncherLegacy.launch("image/*")
            },
            onSelectNew = {
                showLegacyPickerDialog = false
                pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }
}

/**
 * 顶部标题栏
 */
@Composable
private fun topBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = SaltColors.Primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AMuFrame",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 图片预览区
 */
@Composable
private fun imagePreviewSection(
    bitmap: Bitmap?,
    selectedImageUri: Uri?,
    onPickImage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onPickImage),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedImageUri == null) "点击选择图片" else "点击更换图片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 快捷操作行
 */
@Composable
private fun quickActionsRow(
    permissionGranted: Boolean,
    permissionCheckResult: NotificationPermissionManager.PermissionCheckResult,
    onPermissionClick: () -> Unit,
    onFontClick: () -> Unit,
    hasCustomFont: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 权限按钮
        ActionChip(
            icon = when (permissionCheckResult) {
                NotificationPermissionManager.PermissionCheckResult.GRANTED -> Icons.Default.Check
                NotificationPermissionManager.PermissionCheckResult.SERVICE_NOT_RESPONDING -> Icons.Default.Refresh
                else -> Icons.Default.MusicNote
            },
            label = when (permissionCheckResult) {
                NotificationPermissionManager.PermissionCheckResult.GRANTED -> "已授权"
                NotificationPermissionManager.PermissionCheckResult.SERVICE_NOT_RESPONDING -> "重新连接"
                NotificationPermissionManager.PermissionCheckResult.COMPONENT_DISABLED -> "修复"
                NotificationPermissionManager.PermissionCheckResult.NOT_GRANTED -> "授权"
            },
            isActive = permissionCheckResult == NotificationPermissionManager.PermissionCheckResult.GRANTED,
            onClick = onPermissionClick,
            modifier = Modifier.weight(1f)
        )
        
        // 字体按钮
        ActionChip(
            icon = Icons.Default.Palette,
            label = if (hasCustomFont) "字体" else "字体",
            isActive = hasCustomFont,
            onClick = onFontClick,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 操作按钮芯片
 */
@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.95f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )
    
    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isActive) 
            SaltColors.Primary.copy(alpha = 0.15f) 
        else 
            MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) SaltColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) SaltColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 导出按钮行
 */
@Composable
private fun exportButtonsRow(
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
        FilledIconButton(
            onClick = onSave,
            enabled = framedBitmap != null && !isSaving,
            modifier = Modifier.weight(1f).height(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = SaltColors.Primary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSaving) "保存中..." else "保存",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        
        OutlinedIconButton(
            onClick = onExport,
            enabled = framedBitmap != null && !isExporting,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isExporting) "导出中..." else "分享",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * 底部工具行
 */
@Composable
private fun bottomToolsRow(
    onLogClick: () -> Unit,
    onMotionPhotoExport: Boolean,
    onExportMotionPhoto: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onLogClick) {
            Text("日志", style = MaterialTheme.typography.labelMedium)
        }
        
        if (onMotionPhotoExport) {
            TextButton(onClick = onExportMotionPhoto) {
                Text("实况相框", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * 图片来源选择对话框
 */
@Composable
private fun imageSourceDialog(
    onDismiss: () -> Unit,
    onSelectLegacy: () -> Unit,
    onSelectNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择图片来源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSelectLegacy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📷 相册（保留 GPS）")
                }
                OutlinedButton(
                    onClick = onSelectNew,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🖼️ 照片选择器")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 通知权限对话框
 */
@Composable
private fun notificationPermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("读取歌曲信息") },
        text = { Text("请在系统设置中授权「正在播放」权限") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 日志内容对话框
 */
@Composable
private fun logContentDialog(
    logContent: String?,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("日志") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = logContent ?: "无日志",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { logContent?.let { onCopy(it) } }) {
                Text("复制")
            }
        },
        dismissButton = {
            TextButton(onClick = { logContent?.let { onShare(it) } }) {
                Text("分享")
            }
        }
    )
}

/**
 * 处理通知权限点击
 */
private fun handleNotificationPermissionClick(
    context: Context,
    currentResult: NotificationPermissionManager.PermissionCheckResult,
    onShowDialog: () -> Unit,
    onRebindService: () -> Unit,
    onUpdateResult: (NotificationPermissionManager.PermissionCheckResult) -> Unit
) {
    val freshResult = NotificationPermissionManager.isPermissionActuallyGranted(context)
    
    when (freshResult) {
        NotificationPermissionManager.PermissionCheckResult.GRANTED -> {
            if (MusicMetadataBroadcaster.hasRecentMetadata()) {
                Toast.makeText(context, "已授权 ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "重新连接...", Toast.LENGTH_SHORT).show()
                onRebindService()
                Handler(Looper.getMainLooper()).postDelayed({
                    val newResult = NotificationPermissionManager.isPermissionActuallyGranted(context)
                    onUpdateResult(newResult)
                }, 2000)
            }
        }
        
        NotificationPermissionManager.PermissionCheckResult.SERVICE_NOT_RESPONDING -> {
            Toast.makeText(context, "重新连接...", Toast.LENGTH_SHORT).show()
            onRebindService()
            Handler(Looper.getMainLooper()).postDelayed({
                val newResult = NotificationPermissionManager.isPermissionActuallyGranted(context)
                onUpdateResult(newResult)
            }, 2000)
        }
        
        NotificationPermissionManager.PermissionCheckResult.COMPONENT_DISABLED -> {
            Toast.makeText(context, "修复中...", Toast.LENGTH_SHORT).show()
            onRebindService()
            Handler(Looper.getMainLooper()).postDelayed({
                val newResult = NotificationPermissionManager.isPermissionActuallyGranted(context)
                onUpdateResult(newResult)
            }, 2000)
        }
        
        NotificationPermissionManager.PermissionCheckResult.NOT_GRANTED -> {
            onShowDialog()
        }
    }
}

private fun FontFamily.Companion.Monospace: androidx.compose.ui.text.font.FontFamily
    get() = androidx.compose.ui.text.font.FontFamily.Monospace
