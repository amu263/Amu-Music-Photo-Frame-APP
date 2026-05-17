package com.example.musicframe

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicframe.domain.model.FrameControlAction
import com.example.musicframe.image.FrameMode
import com.example.musicframe.image.PhotoMetadata
import com.example.musicframe.image.PhotoMetadataReader
import com.example.musicframe.media.MusicMetadataBroadcaster
import com.example.musicframe.media.NowPlayingListenerService
import com.example.musicframe.ui.screen.exportFormatSelector
import com.example.musicframe.ui.screen.rhythmControlsPanel
import com.example.musicframe.ui.screen.frameControls
import com.example.musicframe.ui.theme.musicFrameTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

/**
 * 通知权限状态管理器
 * 负责检测和管理 NotificationListenerService 的权限状态
 */
object NotificationPermissionManager {
    private const val PREFS_NAME = "notification_permission_prefs"
    private const val KEY_PERMISSION_GRANTED = "permission_granted"
    private const val KEY_PERMISSION_TIMESTAMP = "permission_timestamp"
    private const val TAG = "NotificationPermission"

    /**
     * 检查通知监听权限是否真正授予并生效
     * 同时检查 Settings 中的权限和服务的实际连接状态
     */
    fun isPermissionActuallyGranted(context: Context): PermissionCheckResult {
        val packageName = context.packageName
        
        // 检查 1: Settings 中的 enabled_notification_listeners 是否包含我们的包名
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        
        val isInEnabledList = enabledListeners.split(":")
            .mapNotNull { 
                try { ComponentName.unflattenFromString(it)?.packageName } 
                catch (e: Exception) { null }
            }
            .contains(packageName)
        
        if (!isInEnabledList) {
            Log.d(TAG, "权限检查: 未在 enabled_notification_listeners 中找到本应用")
            return PermissionCheckResult.NOT_GRANTED
        }
        
        // 检查 2: 服务组件是否启用（未被禁用）
        val serviceComponent = ComponentName(packageName, "com.example.musicframe.media.NowPlayingListenerService")
        val componentEnabled = try {
            context.packageManager.getComponentEnabledSetting(serviceComponent) != 
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (e: Exception) {
            Log.e(TAG, "检查组件启用状态失败: ${e.message}")
            false
        }
        
        if (!componentEnabled) {
            Log.d(TAG, "权限检查: 服务组件已被禁用")
            return PermissionCheckResult.COMPONENT_DISABLED
        }
        
        // 检查 3: 尝试检测服务是否真正响应
        // 通过发送测试广播并等待响应来判断（这里简化处理）
        val serviceCanBeConnected = isServiceActivelyConnected(context)
        
        Log.d(TAG, "权限检查结果: enabled=$isInEnabledList, component=$componentEnabled, connected=$serviceCanBeConnected")
        
        return when {
            !isInEnabledList -> PermissionCheckResult.NOT_GRANTED
            !componentEnabled -> PermissionCheckResult.COMPONENT_DISABLED
            !serviceCanBeConnected -> PermissionCheckResult.SERVICE_NOT_RESPONDING
            else -> PermissionCheckResult.GRANTED
        }
    }
    
    /**
     * 检测服务是否真正处于连接状态
     */
    private fun isServiceActivelyConnected(context: Context): Boolean {
        // 检查是否有现成的音乐元数据（说明服务正在工作）
        val hasRecentMetadata = MusicMetadataBroadcaster.hasRecentMetadata()
        if (hasRecentMetadata) {
            Log.d(TAG, "服务连接检测: 存在最近的元数据，服务正在工作")
            return true
        }
        
        // 如果没有元数据，检查服务是否在 enabled_notification_listeners 中且组件已启用
        // 这是最可靠的检测方式
        val packageName = context.packageName
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        
        val isProperlyEnabled = enabledListeners.contains(packageName)
        Log.d(TAG, "服务连接检测: properlyEnabled=$isProperlyEnabled")
        
        return isProperlyEnabled
    }
    
    /**
     * 触发服务重新绑定
     */
    fun requestServiceRebind(context: Context) {
        Log.d(TAG, "请求重新绑定服务")
        val serviceComponent = ComponentName(context, "com.example.musicframe.media.NowPlayingListenerService")
        val packageManager = context.packageManager
        
        try {
            // 禁用再启用组件来触发系统重新绑定
            packageManager.setComponentEnabledSetting(
                serviceComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    packageManager.setComponentEnabledSetting(
                        serviceComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Log.d(TAG, "服务重新绑定已触发")
                } catch (e: Exception) {
                    Log.e(TAG, "重新启用组件失败: ${e.message}")
                }
            }, 500)
        } catch (e: Exception) {
            Log.e(TAG, "触发重新绑定失败: ${e.message}")
        }
    }
    
    /**
     * 获取用户需要前往的设置页面意图
     */
    fun getSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
    
    enum class PermissionCheckResult {
        GRANTED,                    // 权限完全正常
        NOT_GRANTED,               // 未授予权限
        COMPONENT_DISABLED,         // 服务组件被禁用
        SERVICE_NOT_RESPONDING      // 服务未响应
    }
}

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
        if (tempCustomColorHex.isEmpty() && state.customFrameColorHex.isNotEmpty()) {
            tempCustomColorHex = state.customFrameColorHex
            frameColorHex = state.customFrameColorHex
        } else if (state.customFrameColorHex.isEmpty() && tempCustomColorHex.isNotEmpty()) {
            tempCustomColorHex = ""
            frameColorHex = ""
        }
    }

    // 通知权限状态 - 使用枚举而不是布尔值
    var permissionCheckResult by remember { mutableStateOf(NotificationPermissionManager.PermissionCheckResult.NOT_GRANTED) }
    
    // 上次检查时是否有元数据（用于判断服务是否真正在工作）
    var lastHadMetadata by remember { mutableStateOf(false) }

    // 使用 LifecycleObserver 在 onResume 时全面检查权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 每次 onResume 时完整检查权限状态
                val result = NotificationPermissionManager.isPermissionActuallyGranted(context)
                permissionCheckResult = result
                
                // 检查是否有元数据（服务是否真正在工作）
                lastHadMetadata = MusicMetadataBroadcaster.hasRecentMetadata()
                
                Log.d("MainActivity", "onResume 权限检查: $result, hadMetadata=$lastHadMetadata")
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
        Log.d("MainActivity", "初始权限检查: $result")
    }

    // 定期检查服务状态（监听元数据变化）
    LaunchedEffect(state.musicMetadata) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            
            // 检查元数据是否还在更新
            val currentHasMetadata = MusicMetadataBroadcaster.hasRecentMetadata()
            
            // 如果之前有元数据现在没有了，说明服务可能断开了
            if (lastHadMetadata && !currentHasMetadata) {
                Log.d("MainActivity", "元数据停止更新，重新检查权限")
                val result = NotificationPermissionManager.isPermissionActuallyGranted(context)
                permissionCheckResult = result
            }
            
            lastHadMetadata = currentHasMetadata
            
            // 定期检查权限状态
            val result = NotificationPermissionManager.isPermissionActuallyGranted(context)
            if (result != permissionCheckResult) {
                permissionCheckResult = result
                Log.d("MainActivity", "权限状态变化: $result")
            }
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部标题
        topBar()
        
        // 图片预览卡片
        imagePreviewCard(
            bitmap = state.framedBitmap,
            selectedImageUri = state.selectedImageUri,
            onPickImage = { showLegacyPickerDialog = true }
        )
        
        // 快捷操作行
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
            onResetFontClick = { viewModel.useDefaultFont() },
            hasCustomFont = state.customFontName != null
        )

        // 相框控制
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

        // 音乐律动控制（仅在律动模式时显示）
        if (state.frameMode == FrameMode.MUSIC_RHYTHM) {
            rhythmControlsPanel(
                config = state.rhythmConfig,
                isExpanded = state.rhythmExpanded,
                onToggleExpand = { viewModel.toggleRhythmPanel() },
                onEffectSelect = { viewModel.selectRhythmEffect(it) },
                onIntensityChange = { viewModel.updateRhythmIntensity(it) }
            )
        }

        // 星座运势模式 - 生日输入（仅在选中星座模式时显示）
        if (state.frameMode == FrameMode.ZODIAC_HOROSCOPE) {
            birthdayInputCard(
                birthdayMonth = state.userBirthdayMonth,
                birthdayDay = state.userBirthdayDay,
                onBirthdayChange = { month, day -> viewModel.setUserBirthday(month, day) },
                onBirthdayConfirm = { viewModel.confirmUserBirthday() }
            )
        }

        // 导出格式选择
        exportFormatSelector(
            selected = state.exportFormat,
            onSelected = viewModel::onExportFormatSelected
        )

        // 导出按钮行
        exportButtonsRow(
            framedBitmap = state.framedBitmap,
            isSaving = state.isSaving,
            isExporting = state.isExporting,
            onSave = { viewModel.saveFramedImage() },
            onExport = { viewModel.shareFramedImage() }
        )
        
        // 底部工具
        bottomToolsRow(
            onLogClick = {
                logContent = PhotoMetadataReader.readLogContent(context)
                showLogDialog = true
            },
            onMotionPhotoExport = state.photoMetadata?.isMotionPhoto == true,
            onExportMotionPhoto = { viewModel.exportMotionPhoto() }
        )

        state.message?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
    }

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

/**
 * 处理通知权限按钮点击
 */
private fun handleNotificationPermissionClick(
    context: Context,
    currentResult: NotificationPermissionManager.PermissionCheckResult,
    onShowDialog: () -> Unit,
    onRebindService: () -> Unit,
    onUpdateResult: (NotificationPermissionManager.PermissionCheckResult) -> Unit
) {
    // 立即重新检查权限状态
    val freshResult = NotificationPermissionManager.isPermissionActuallyGranted(context)
    Log.d("MainActivity", "按钮点击，重新检查权限: $freshResult")
    
    when (freshResult) {
        NotificationPermissionManager.PermissionCheckResult.GRANTED -> {
            // 权限正常，检查服务是否真正在工作
            if (MusicMetadataBroadcaster.hasRecentMetadata()) {
                Toast.makeText(context, "正在播放权限已开启 ✓", Toast.LENGTH_SHORT).show()
            } else {
                // 有权限但服务没响应，尝试重新绑定
                Toast.makeText(context, "正在重新连接服务...", Toast.LENGTH_SHORT).show()
                onRebindService()
                // 延迟检查结果
                Handler(Looper.getMainLooper()).postDelayed({
                    val newResult = NotificationPermissionManager.isPermissionActuallyGranted(context)
                    onUpdateResult(newResult)
                    if (newResult == NotificationPermissionManager.PermissionCheckResult.GRANTED) {
                        Toast.makeText(context, "服务已重新连接 ✓", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "服务连接异常，请重新授权", Toast.LENGTH_LONG).show()
                    }
                }, 2000)
            }
        }
        
        NotificationPermissionManager.PermissionCheckResult.SERVICE_NOT_RESPONDING -> {
            // 服务未响应，尝试重新绑定
            Toast.makeText(context, "服务未响应，正在重新连接...", Toast.LENGTH_SHORT).show()
            onRebindService()
            Handler(Looper.getMainLooper()).postDelayed({
                val newResult = NotificationPermissionManager.isPermissionActuallyGranted(context)
                onUpdateResult(newResult)
                if (newResult == NotificationPermissionManager.PermissionCheckResult.GRANTED) {
                    Toast.makeText(context, "服务已重新连接 ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "重新连接失败，请重新授权", Toast.LENGTH_LONG).show()
                }
            }, 2000)
        }
        
        NotificationPermissionManager.PermissionCheckResult.COMPONENT_DISABLED -> {
            // 组件被禁用，重新启用
            Toast.makeText(context, "正在重新启用服务组件...", Toast.LENGTH_SHORT).show()
            onRebindService()
            Handler(Looper.getMainLooper()).postDelayed({
                val newResult = NotificationPermissionManager.isPermissionActuallyGranted(context)
                onUpdateResult(newResult)
            }, 2000)
        }
        
        NotificationPermissionManager.PermissionCheckResult.NOT_GRANTED -> {
            // 权限未授予，显示设置对话框
            onShowDialog()
        }
    }
}

@Composable
private fun topBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "AMuFrame",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun imagePreviewCard(
    bitmap: Bitmap?,
    selectedImageUri: Uri?,
    onPickImage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onPickImage
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
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
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedImageUri == null) "点击选择图片" else "点击更换",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun quickActionsRow(
    permissionGranted: Boolean,
    permissionCheckResult: NotificationPermissionManager.PermissionCheckResult,
    onPermissionClick: () -> Unit,
    onFontClick: () -> Unit,
    onResetFontClick: () -> Unit,
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
                else -> Icons.Default.Refresh
            },
            label = when (permissionCheckResult) {
                NotificationPermissionManager.PermissionCheckResult.GRANTED -> "已授权"
                NotificationPermissionManager.PermissionCheckResult.SERVICE_NOT_RESPONDING -> "重连"
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
            label = "字体",
            isActive = hasCustomFont,
            onClick = onFontClick,
            modifier = Modifier.weight(1f)
        )
        
        // 还原默认字体按钮（仅当有自定义字体时显示）
        if (hasCustomFont) {
            ActionChip(
                icon = Icons.Default.Refresh,
                label = "还原",
                isActive = false,
                onClick = onResetFontClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

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
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (isActive) 
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) 
        else 
            MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
            modifier = Modifier.weight(1f).height(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSaving) "..." else "保存",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        
        OutlinedIconButton(
            onClick = onExport,
            enabled = framedBitmap != null && !isExporting,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isExporting) "..." else "分享",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

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
            Text("日志", style = MaterialTheme.typography.labelSmall)
        }
        
        if (onMotionPhotoExport) {
            TextButton(onClick = onExportMotionPhoto) {
                Text("实况", style = MaterialTheme.typography.labelSmall)
            }
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

@Composable
private fun birthdayInputCard(
    birthdayMonth: Int,
    birthdayDay: Int,
    onBirthdayChange: (Int, Int) -> Unit,
    onBirthdayConfirm: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🎂 设置生日以获取专属星座运势",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 月份选择
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    var expandedMonth by remember { mutableStateOf(false) }
                    val months = (1..12).toList()
                    val monthNames = listOf("1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月")
                    
                    OutlinedButton(
                        onClick = { expandedMonth = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (birthdayMonth > 0) monthNames[birthdayMonth - 1] else "月份",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = expandedMonth,
                        onDismissRequest = { expandedMonth = false },
                        modifier = Modifier.fillMaxWidth(0.5f)
                    ) {
                        months.forEach { month ->
                            DropdownMenuItem(
                                text = { Text(monthNames[month - 1]) },
                                onClick = {
                                    onBirthdayChange(month, birthdayDay)
                                    expandedMonth = false
                                }
                            )
                        }
                    }
                }
                
                // 日期选择
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    var expandedDay by remember { mutableStateOf(false) }
                    val days = (1..31).toList()
                    
                    OutlinedButton(
                        onClick = { expandedDay = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (birthdayDay > 0) "${birthdayDay}日" else "日期",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = expandedDay,
                        onDismissRequest = { expandedDay = false },
                        modifier = Modifier.fillMaxWidth(0.5f)
                    ) {
                        days.forEach { day ->
                            DropdownMenuItem(
                                text = { Text("${day}日") },
                                onClick = {
                                    onBirthdayChange(birthdayMonth, day)
                                    expandedDay = false
                                }
                            )
                        }
                    }
                }
            }
            
            if (birthdayMonth > 0 && birthdayDay > 0) {
                val zodiacInfo = getZodiacInfo(birthdayMonth, birthdayDay)
                Text(
                    text = "星座: $zodiacInfo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Button(
                    onClick = onBirthdayConfirm,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("确认生日")
                }
            }
        }
    }
}

private fun getZodiacInfo(month: Int, day: Int): String {
    // Day-of-year calculation
    val daysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    val monthStarts = IntArray(13)
    for (i in 2..12) {
        monthStarts[i] = monthStarts[i - 1] + daysInMonth[i - 1]
    }
    val doy = monthStarts[month] + day

    // Capricorn wraps year boundary: Dec 22+ (doy 356+) OR Jan 1-19 (doy 1-19)
    if (doy >= 356 || doy <= 19) return "摩羯座"
    if (doy >= 20 && doy <= 49) return "水瓶座"
    if (doy >= 50 && doy <= 79) return "双鱼座"

    // Standard zodiac boundaries (first day): Aries Mar 21, Taurus Apr 20, etc.
    return when {
        doy >= 80 && doy < 110 -> "白羊座"
        doy >= 110 && doy < 140 -> "金牛座"
        doy >= 140 && doy < 171 -> "双子座"
        doy >= 171 && doy < 203 -> "巨蟹座"
        doy >= 203 && doy < 234 -> "狮子座"
        doy >= 234 && doy < 265 -> "处女座"
        doy >= 265 && doy < 295 -> "天秤座"
        doy >= 295 && doy < 326 -> "天蝎座"
        doy >= 326 && doy < 356 -> "射手座"
        else -> "白羊座"
    }
}
