package org.parkjw.capywarp.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.activity.compose.BackHandler
import org.parkjw.capywarp.ui.viewmodels.PromptListViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class WarpRouterActivity : ComponentActivity() {
    private var pendingNotificationText: String? = null
    private var pendingNotificationImageBytes: ByteArray? = null
    private val REQ_POST_NOTI = 5001

    private fun showResultNotificationAndFinish(text: String) {
        val channelId = "capywarp_results"
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val ch = android.app.NotificationChannel(
                channelId,
                getString(org.parkjw.capywarp.R.string.notif_channel_name),
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            mgr.createNotificationChannel(ch)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this@WarpRouterActivity, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(org.parkjw.capywarp.R.string.notif_result_title))
            .setContentText(text.take(40))
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .build()
        mgr.notify(1001, notification)
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_POST_NOTI) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            val imageBytes = pendingNotificationImageBytes
            val text = pendingNotificationText
            pendingNotificationImageBytes = null
            pendingNotificationText = null
            if (granted) {
                if (imageBytes != null) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    if (bmp != null) {
                        showImageNotificationAndFinish(bmp)
                        return
                    }
                }
                if (text != null) {
                    showResultNotificationAndFinish(text)
                    return
                }
            }
            // 권한 거부 또는 처리할 데이터 없음
            android.widget.Toast.makeText(this, getString(org.parkjw.capywarp.R.string.toast_no_post_noti_perm), android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun decodeImageBytesFromString(data: String): ByteArray? {
        return try {
            val base64 = if (data.startsWith("data:image")) {
                val idx = data.indexOf(',')
                if (idx >= 0) data.substring(idx + 1) else data
            } else data
            android.util.Base64.decode(base64.trim(), android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveImageToGallery(bytes: ByteArray): Boolean {
        return try {
            val filename = "CapyWarp_${System.currentTimeMillis()}.png"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CapyWarp")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, cv, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeImageToCacheAndGetUri(bytes: ByteArray): android.net.Uri? {
        return try {
            val dir = java.io.File(cacheDir, "shared_images")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "CapyWarp_${System.currentTimeMillis()}.png")
            file.outputStream().use { it.write(bytes) }
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun showImageNotificationAndFinish(bitmap: android.graphics.Bitmap) {
        val channelId = "capywarp_results"
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val ch = android.app.NotificationChannel(
                channelId,
                getString(org.parkjw.capywarp.R.string.notif_channel_name),
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            mgr.createNotificationChannel(ch)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this@WarpRouterActivity, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(org.parkjw.capywarp.R.string.notif_image_result_title))
            .setContentText(getString(org.parkjw.capywarp.R.string.notif_image_result_text))
            .setStyle(androidx.core.app.NotificationCompat.BigPictureStyle().bigPicture(bitmap))
            .build()
        mgr.notify(1002, notification)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        val type = intent?.type

        val selectedText = when (action) {
            Intent.ACTION_PROCESS_TEXT, "android.intent.action.PROCESS_TEXT_READONLY" -> {
                intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            }
            Intent.ACTION_SEND -> {
                intent?.getStringExtra(Intent.EXTRA_TEXT)
            }
            else -> null
        } ?: ""

        val sharedImageUri: android.net.Uri? = if (action == Intent.ACTION_SEND && type?.startsWith("image/") == true) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri
            }
        } else null

        setContent {
            val promptListViewModel: PromptListViewModel = hiltViewModel()
            val prompts by promptListViewModel.prompts.collectAsState(initial = emptyList())

            // 뒤로가기 시 닫기
            androidx.activity.compose.BackHandler { finish() }

            // 패널 표시 상태 및 드래그/사이즈 상태
            var panelVisible by remember { mutableStateOf(true) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }
            val config = androidx.compose.ui.platform.LocalConfiguration.current
            var panelWidth by remember { mutableStateOf((config.screenWidthDp * 0.7f).dp.coerceIn(260.dp, 520.dp)) }
            var panelHeight by remember { mutableStateOf((config.screenHeightDp * 0.6f).dp.coerceIn(300.dp, 720.dp)) }
            var userResized by remember { mutableStateOf(false) }
            var query by remember { mutableStateOf("") }

            // Transparent full-screen overlay that doesn't dim the host app
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (panelVisible) {
                    // Centered floating panel (draggable, resizable)
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 8.dp,
                        shadowElevation = 12.dp,
                        color = Color(0xFF2B2B2F),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .sizeIn(minWidth = 260.dp, minHeight = 260.dp, maxWidth = 520.dp, maxHeight = 720.dp)
                            .width(panelWidth)
                            .height(panelHeight)
                            .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()) }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { _, dragAmount ->
                                        offsetX += dragAmount.x
                                        offsetY += dragAmount.y
                                    }
                                )
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFF2F2F3),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { finish() }) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.close), color = Color(0xFFEDEDED)) }
                                }

                                // 검색 필터
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                placeholder = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.search), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1F1F22),
                                    unfocusedContainerColor = Color(0xFF1F1F22),
                                    focusedTextColor = Color(0xFFECECEC),
                                    unfocusedTextColor = Color(0xFFECECEC),
                                    focusedBorderColor = Color(0xFF747579),
                                    unfocusedBorderColor = Color(0xFF3C3D41),
                                    cursorColor = Color(0xFFE0E0E0),
                                    focusedLabelColor = Color(0xFFE0E0E0),
                                    focusedLeadingIconColor = Color(0xFFE0E0E0),
                                    focusedTrailingIconColor = Color(0xFFE0E0E0)
                                )
                            )

                            var showText by remember { mutableStateOf(false) }
                            if (selectedText.isNotBlank()) {
                                TextButton(onClick = { showText = !showText }) {
                                    Text(if (showText) androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.selected_text_hide) else androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.selected_text_show), color = Color(0xFFEDEDED))
                                }
                                if (showText) {
                                    Surface(
                                        tonalElevation = 1.dp,
                                        shape = MaterialTheme.shapes.medium,
                                        color = Color(0xFF1F1F22),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = selectedText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFFCCCCCC),
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }

                            if (sharedImageUri != null) {
                                Surface(
                                    tonalElevation = 1.dp,
                                    shape = MaterialTheme.shapes.medium,
                                    color = Color(0xFF1F2A1F),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.image_attached, sharedImageUri.toString()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB7E4C7),
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            val list = remember(query, prompts) {
                                prompts
                                    .filter { it.resultAction != 0 }
                                    .filter { !(it.outputType == 1 && it.resultAction == 1) }
                                    .filter { p ->
                                        val q = query.trim().lowercase()
                                        if (q.isEmpty()) true else p.title.lowercase().contains(q) || p.content.lowercase().contains(q)
                                    }
                            }
                            // Adjust initial height based on prompt count (max 4 visible)
                            LaunchedEffect(list.size) {
                                if (!userResized) {
                                    val visible = kotlin.math.min(4, list.size)
                                    val itemArea = (visible * 64) // each approx 64dp height
                                    val headerArea = 220 // title + search + paddings
                                    val target = (itemArea + headerArea).dp
                                    panelHeight = target.coerceIn(300.dp, 720.dp)
                                }
                            }
                            if (list.isEmpty()) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.no_matching_prompt),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyColumn(modifier = Modifier.weight(1f, fill = true), contentPadding = PaddingValues(vertical = 4.dp)) {
                                    items(list, key = { it.id }) { prompt ->
                                        Button(
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF3A3A3F),
                                                contentColor = Color(0xFFEDEDED)
                                            ),
                                            onClick = {
                                                // 백그라운드 처리 서비스 시작 후 즉시 종료
                                                panelVisible = false
                                                val svc = Intent(this@WarpRouterActivity, WarpProcessService::class.java)
                                                svc.putExtra(WarpProcessService.EXTRA_TEXT, selectedText)
                                                svc.putExtra(WarpProcessService.EXTRA_PROMPT_ID, prompt.id)
                                                if (sharedImageUri != null) {
                                                    svc.putExtra(WarpProcessService.EXTRA_IMAGE_URI, sharedImageUri)
                                                    svc.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    // also grant to service explicitly
                                                    try { grantUriPermission(packageName, sharedImageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                                                }
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                    startForegroundService(svc)
                                                } else {
                                                    startService(svc)
                                                }
                                                finish()
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Text(prompt.title)
                                                val actionLabel = if (prompt.outputType == 0) {
                                                    when (prompt.resultAction) {
                                                        1 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_copy_to_clipboard)
                                                        2 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_notification)
                                                        else -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_process_result)
                                                    }
                                                } else {
                                                    when (prompt.resultAction) {
                                                        2 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_notification_image)
                                                        3 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_save_gallery)
                                                        else -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_process_result)
                                                    }
                                                }
                                                Text(actionLabel, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }

                            // 사이즈 조절 핸들 (오른쪽 아래)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(24.dp)
                                        .pointerInput(Unit) {
                                            detectDragGestures { _, drag ->
                                                val dw = drag.x.dp
                                                val dh = drag.y.dp
                                                userResized = true
                                                panelWidth = (panelWidth + dw).coerceIn(260.dp, 520.dp)
                                                panelHeight = (panelHeight + dh).coerceIn(260.dp, 720.dp)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

}