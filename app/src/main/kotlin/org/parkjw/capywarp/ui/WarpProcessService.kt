package org.parkjw.capywarp.ui

import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import dagger.hilt.android.AndroidEntryPoint
import android.app.PendingIntent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.parkjw.capywarp.domain.repository.GeminiRepository
import org.parkjw.capywarp.domain.repository.PromptRepository
import javax.inject.Inject

@AndroidEntryPoint
class WarpProcessService : Service() {
    private fun resolveIsDarkForOverlay(appCtx: Context): Boolean {
        return try {
            val themeValue = settingsRepository.getThemeSync()
            val osNight = (appCtx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val result = when (themeValue) {
                "light" -> false
                "dark" -> true
                else -> osNight
            }
            Log.d("CapyWarp/WarpServiceTheme", "resolveIsDarkForOverlay(sync): themeValue=" + themeValue + ", osNight=" + osNight + ", result=" + result)
            result
        } catch (e: Exception) {
            val osNight = (appCtx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            Log.w("CapyWarp/WarpServiceTheme", "resolveIsDarkForOverlay fallback due to ${e.message}; osNight=" + osNight)
            osNight
        }
    }
    @Inject lateinit var promptRepository: PromptRepository
    @Inject lateinit var geminiRepository: GeminiRepository
    @Inject lateinit var settingsRepository: org.parkjw.capywarp.domain.repository.SettingsRepository

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle notification action intents first
        when (intent?.action) {
            ACTION_COPY_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT)
                if (!text.isNullOrEmpty()) {
                    try {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("CapyWarp", text)
                        cm.setPrimaryClip(clip)
                        showToast(getString(org.parkjw.capywarp.R.string.toast_copy_text))
                    } catch (e: Exception) {
                        showError(getString(org.parkjw.capywarp.R.string.copy_failed, e.message ?: ""))
                    }
                } else {
                    showError(getString(org.parkjw.capywarp.R.string.toast_no_text_to_copy))
                }
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_COPY_IMAGE -> {
                val uri = intent.data
                if (uri != null) {
                    try {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newUri(contentResolver, "CapyWarp Image", uri)
                        cm.setPrimaryClip(clip)
                        showToast(getString(org.parkjw.capywarp.R.string.toast_copy_image))
                    } catch (e: Exception) {
                        showError(getString(org.parkjw.capywarp.R.string.toast_copy_image_failed, e.message ?: ""))
                    }
                } else {
                    showError(getString(org.parkjw.capywarp.R.string.toast_no_image_uri))
                }
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_SAVE_IMAGE -> {
                val uri = intent.data
                if (uri != null) {
                    try {
                        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val ok = saveImageToGallery(bytes)
                            if (ok) showInfo(getString(org.parkjw.capywarp.R.string.toast_saved_to_gallery)) else showError(getString(org.parkjw.capywarp.R.string.toast_save_gallery_failed))
                        } else {
                            showError(getString(org.parkjw.capywarp.R.string.toast_cannot_read_image))
                        }
                    } catch (e: android.os.TransactionTooLargeException) {
                        showError(getString(org.parkjw.capywarp.R.string.toast_data_too_large))
                    } catch (e: Exception) {
                        showError(getString(org.parkjw.capywarp.R.string.toast_save_gallery_failed))
                    }
                } else {
                    showError(getString(org.parkjw.capywarp.R.string.toast_no_image_uri))
                }
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_SHARE_IMAGE -> {
                val uri = intent.data
                if (uri != null) {
                    // Launch share sheet from service; starting an Activity collapses the shade automatically
                    serviceScope.launch(Dispatchers.Main) {
                        try {
                            try { kotlinx.coroutines.delay(120) } catch (_: Exception) {}
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val chooser = Intent.createChooser(share, getString(org.parkjw.capywarp.R.string.share_image_title)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(chooser)
                        } catch (e: Exception) {
                            showError(getString(org.parkjw.capywarp.R.string.toast_share_error, e.message ?: ""))
                        } finally {
                            stopSelf(startId)
                        }
                    }
                } else {
                    showError(getString(org.parkjw.capywarp.R.string.toast_no_image_uri))
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
        }
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        val promptId = intent?.getIntExtra(EXTRA_PROMPT_ID, -1) ?: -1
        // Support generic attachment (images, documents, etc.) with backward-compatible fallback
        val attachmentUri: Uri? = when {
            intent?.hasExtra(EXTRA_ATTACHMENT_URI) == true -> {
                if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_ATTACHMENT_URI, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_ATTACHMENT_URI) as? Uri
            }
            intent?.hasExtra(EXTRA_IMAGE_URI) == true -> { // legacy
                if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_IMAGE_URI, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_IMAGE_URI) as? Uri
            }
            else -> null
        }
        if ((text.isBlank() && attachmentUri == null) || promptId <= 0) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        ensureChannel()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val progressNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(getString(org.parkjw.capywarp.R.string.notif_processing_title))
            .setContentText(getString(org.parkjw.capywarp.R.string.notif_processing_text))
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        startForeground(NOTI_ID_PROGRESS, progressNotification)

        serviceScope.launch {
            try {
                val prompt = promptRepository.getPrompt(promptId)
                if (prompt == null) {
                    showError(getString(org.parkjw.capywarp.R.string.error_prompt_not_found))
                    stopSelf(startId)
                    return@launch
                }

                // Resolve attachment bytes and MIME (fallback to content resolver if not provided)
                val providedMime = intent?.getStringExtra(EXTRA_ATTACHMENT_MIME)
                val resolvedMime = providedMime ?: (attachmentUri?.let { contentResolver.getType(it) })
                val attachmentBytes: ByteArray? = try {
                    if (attachmentUri != null) contentResolver.openInputStream(attachmentUri)?.use { it.readBytes() } else null
                } catch (e: Exception) { null }

                // Retry generateContent up to 3 times for non-network/server errors
                var result: String
                var attemptGen = 0
                var lastEx: Exception? = null
                while (true) {
                    attemptGen++
                    try {
                        if (attemptGen > 1) {
                            updateProgressText(getString(org.parkjw.capywarp.R.string.notif_processing_retry_text, attemptGen, 3))
                        }
                        result = geminiRepository.generateContent(text, prompt, attachmentBytes, resolvedMime)
                        break
                    } catch (e: Exception) {
                        lastEx = e
                        val isNetwork = e is java.io.IOException || (e::class.java.name.contains("HttpException"))
                        if (isNetwork || attemptGen >= 3) throw e
                        // small backoff
                        try { 
                            updateProgressText(getString(org.parkjw.capywarp.R.string.notif_processing_retry_text, attemptGen + 1, 3))
                            kotlinx.coroutines.delay(150L * attemptGen) 
                        } catch (_: Exception) {}
                    }
                }

                // POST_NOTIFICATIONS 권한 체크 (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ActivityCompat.checkSelfPermission(
                        this@WarpProcessService,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        // 권한이 없으면 조용히 종료 (포그라운드는 이미 보여줬으므로 최소한 처리 완료 알림만 실패)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                        return@launch
                    }
                }

                when (prompt.outputType) {
                    0 -> { // text
                        when (prompt.resultAction) {
                            1 -> {
                                // Clipboard copy (silent toast feedback)
                                try {
                                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("CapyWarp", result)
                                    cm.setPrimaryClip(clip)
                                    showToast(getString(org.parkjw.capywarp.R.string.toast_copy_text))
                                } catch (e: Exception) {
                                    showError(getString(org.parkjw.capywarp.R.string.copy_failed, e.message ?: ""))
                                }
                            }
                            4 -> {
                                // External overlay popup only (no Activity fallback)
                                serviceScope.launch(Dispatchers.Main) {
                                    val resolved = resolveIsDarkForOverlay(applicationContext)
                                    Log.d("CapyWarp/WarpServiceTheme", "launch overlay (text): osNight=" + ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) + ", resolved.isDark=" + resolved)
                                    try { OverlayPopupManager.showText(this@WarpProcessService, result, resolved) } catch (_: Exception) {}
                                }
                            }
                            else -> {
                                // Show notification with actions
                                val copyTextIntent = Intent(this@WarpProcessService, WarpProcessService::class.java).apply {
                                    action = ACTION_COPY_TEXT
                                    putExtra(EXTRA_TEXT, result)
                                }
                                val piCopyText = PendingIntent.getService(
                                    this@WarpProcessService,
                                    REQ_COPY_TEXT,
                                    copyTextIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )
                                val shareText = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, result)
                                }
                                val chooser = Intent.createChooser(shareText, getString(org.parkjw.capywarp.R.string.share_text_title)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                val piShareText = PendingIntent.getActivity(
                                    this@WarpProcessService,
                                    REQ_SHARE_TEXT,
                                    chooser,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )
                                val n: Notification = NotificationCompat.Builder(this@WarpProcessService, CHANNEL_ID)
                                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                                    .setContentTitle(getString(org.parkjw.capywarp.R.string.notif_result_title))
                                    .setContentText(result.take(40))
                                    .setStyle(NotificationCompat.BigTextStyle().bigText(result))
                                    .addAction(android.R.drawable.ic_menu_edit, getString(org.parkjw.capywarp.R.string.action_copy_to_clipboard), piCopyText)
                                    .addAction(android.R.drawable.ic_menu_share, getString(org.parkjw.capywarp.R.string.action_share), piShareText)
                                    .build()
                                nm.notify(NOTI_ID_RESULT_TEXT, n)
                            }
                        }
                    }
                    else -> { // image
                        // 이미지 파싱 실패 시에도 최대 3회까지 재시도 (필요 시 재생성 포함)
                        var attempt = 0
                        var currentResult = result
                        var finalBytes: ByteArray? = null
                        var finalBitmap: android.graphics.Bitmap? = null
                        var finalMime: String = "image/png"
                        var lastErr: String? = null
                        while (attempt < 3 && finalBitmap == null) {
                            attempt++
                            if (attempt > 1) {
                                updateProgressText(getString(org.parkjw.capywarp.R.string.notif_processing_retry_text, attempt, 3))
                            }
                            // 1) 문자열 -> (MIME, 바이트) 파싱 시도
                            val parsed = try { parseImageData(currentResult) } catch (e: Exception) { null }
                            if (parsed != null) {
                                finalMime = parsed.second
                                val bytes = parsed.first
                                // 우선 ImageDecoder (API 28+) 시도 후 실패 시 BitmapFactory
                                val bmp = try { decodeBitmapRobust(bytes) } catch (_: Exception) { null }
                                if (bmp != null) {
                                    finalBytes = bytes
                                    finalBitmap = bmp
                                    break
                                } else {
                                    lastErr = "이미지 디코딩에 실패했습니다."
                                }
                            } else {
                                // 파싱되지 않으면 텍스트일 가능성 → 루프 종료하여 텍스트 알림으로 대체
                                lastErr = null
                                break
                            }

                            if (attempt >= 3) break
                            // 2) 짧은 백오프 후, 재생성을 시도하여 형식 문제를 우회
                            try { 
                                updateProgressText(getString(org.parkjw.capywarp.R.string.notif_processing_retry_text, attempt + 1, 3))
                                kotlinx.coroutines.delay(200L * attempt) 
                            } catch (_: Exception) {}
                            try {
                                currentResult = geminiRepository.generateContent(text, prompt, attachmentBytes, resolvedMime)
                            } catch (e: Exception) {
                                // 네트워크/서버 오류는 상위 로직과 동일한 정책을 따르므로 바로 중단
                                lastErr = e.message ?: lastErr
                                break
                            }
                        }

                        // 캐시 파일은 비트맵이 없어도 생성해서 공유/저장 동작은 가능하게 유지
                        val cacheUri: Uri? = try {
                            val bytes = finalBytes ?: parseImageData(currentResult)?.first
                            if (bytes != null) writeImageToCacheAndGetUri(bytes, finalMime) else null
                        } catch (_: Exception) { null }

                        if (finalBitmap != null && finalBytes != null) {
                            val uri = cacheUri ?: writeImageToCacheAndGetUri(finalBytes!!, finalMime)
                            if (prompt.resultAction == 4 && uri != null) {
                                // External overlay popup only (no Activity fallback)
                                serviceScope.launch(Dispatchers.Main) {
                                    val resolved = resolveIsDarkForOverlay(applicationContext)
                                    Log.d("CapyWarp/WarpServiceTheme", "launch overlay (image): osNight=" + ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) + ", resolved.isDark=" + resolved)
                                    try { OverlayPopupManager.showImage(this@WarpProcessService, uri, resolved) } catch (_: Exception) {}
                                }
                            } else {
                                val copyIntent = Intent(this@WarpProcessService, WarpProcessService::class.java).apply {
                                    action = ACTION_COPY_IMAGE
                                    data = uri
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val saveIntent = Intent(this@WarpProcessService, WarpProcessService::class.java).apply {
                                    action = ACTION_SAVE_IMAGE
                                    data = uri
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val piCopy = PendingIntent.getService(
                                    this@WarpProcessService,
                                    REQ_COPY,
                                    copyIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )
                                val piSave = PendingIntent.getService(
                                    this@WarpProcessService,
                                    REQ_SAVE,
                                    saveIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )
                                // 공유 액션: 바로 공유 시트를 Activity로 띄워 알림 패널을 닫고 전면 표시
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val chooser = Intent.createChooser(sendIntent, getString(org.parkjw.capywarp.R.string.share_image_title)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                val piShare = PendingIntent.getActivity(
                                    this@WarpProcessService,
                                    REQ_SHARE,
                                    chooser,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )

                                val n: Notification = NotificationCompat.Builder(this@WarpProcessService, CHANNEL_ID)
                                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                                    .setContentTitle(getString(org.parkjw.capywarp.R.string.notif_image_result_title))
                                    .setContentText(getString(org.parkjw.capywarp.R.string.notif_image_result_text))
                                    .setStyle(NotificationCompat.BigPictureStyle().bigPicture(finalBitmap!!))
                                    .addAction(android.R.drawable.ic_menu_edit, getString(org.parkjw.capywarp.R.string.action_copy_to_clipboard), piCopy)
                                    .addAction(android.R.drawable.ic_menu_save, getString(org.parkjw.capywarp.R.string.action_save_gallery), piSave)
                                    .addAction(android.R.drawable.ic_menu_share, getString(org.parkjw.capywarp.R.string.action_share), piShare)
                                    .build()
                                nm.notify(NOTI_ID_RESULT_IMAGE, n)
                            }
                        } else {
                            // 이미지가 아니라고 판단되면 텍스트 알림으로 대체
                            val textResult = currentResult.trim().ifBlank { lastErr ?: "" }
                            if (textResult.isNotBlank()) {
                                if (prompt.resultAction == 4) {
                                    // External overlay popup only (no Activity fallback)
                                    serviceScope.launch(Dispatchers.Main) {
                                        val resolved = resolveIsDarkForOverlay(applicationContext)
                                        Log.d("CapyWarp/WarpServiceTheme", "launch overlay (text fallback): osNight=" + ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) + ", resolved.isDark=" + resolved)
                                        try { OverlayPopupManager.showText(this@WarpProcessService, textResult, resolved) } catch (_: Exception) {}
                                    }
                                } else {
                                    val copyTextIntent = Intent(this@WarpProcessService, WarpProcessService::class.java).apply {
                                        action = ACTION_COPY_TEXT
                                        putExtra(EXTRA_TEXT, textResult)
                                    }
                                    val piCopyText = PendingIntent.getService(
                                        this@WarpProcessService,
                                        REQ_COPY_TEXT,
                                        copyTextIntent,
                                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                    )
                                    val shareText = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, textResult)
                                    }
                                    val chooser = Intent.createChooser(shareText, getString(org.parkjw.capywarp.R.string.share_text_title)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                    val piShareText = PendingIntent.getActivity(
                                        this@WarpProcessService,
                                        REQ_SHARE_TEXT,
                                        chooser,
                                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                    )
                                    val n: Notification = NotificationCompat.Builder(this@WarpProcessService, CHANNEL_ID)
                                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                                        .setContentTitle(getString(org.parkjw.capywarp.R.string.notif_result_title))
                                        .setContentText(textResult.take(40))
                                        .setStyle(NotificationCompat.BigTextStyle().bigText(textResult))
                                        .addAction(android.R.drawable.ic_menu_edit, getString(org.parkjw.capywarp.R.string.action_copy_to_clipboard), piCopyText)
                                        .addAction(android.R.drawable.ic_menu_share, getString(org.parkjw.capywarp.R.string.action_share), piShareText)
                                        .build()
                                    nm.notify(NOTI_ID_RESULT_TEXT, n)
                                }
                            } else {
                                showError(lastErr ?: "이미지 데이터를 해석할 수 없습니다.")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                showError(e.message ?: "알 수 없는 오류가 발생했습니다.")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, getString(org.parkjw.capywarp.R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(ch)
        }
    }

    private fun updateProgressText(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(getString(org.parkjw.capywarp.R.string.notif_processing_title))
            .setContentText(text)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTI_ID_PROGRESS, n)
    }

    private fun showError(msg: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle(getString(org.parkjw.capywarp.R.string.notif_failure_title))
            .setContentText(msg.take(40))
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .build()
        nm.notify(NOTI_ID_ERROR, n)
    }

    private fun showInfo(msg: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(org.parkjw.capywarp.R.string.app_name))
            .setContentText(msg.take(40))
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .build()
        nm.notify(NOTI_ID_INFO, n)
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

    // Parse data URI or plain Base64 into bytes and mime (defaults to image/png)
    private fun parseImageData(data: String): Pair<ByteArray, String>? {
        return try {
            val trimmed = data.trim()
            if (trimmed.startsWith("data:image")) {
                val headerEnd = trimmed.indexOf(',')
                if (headerEnd <= 0) return null
                val header = trimmed.substring(5, headerEnd) // "image/png;base64"
                val mime = header.substringBefore(';', "image/png")
                val b64 = trimmed.substring(headerEnd + 1)
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                bytes to mime
            } else {
                val bytes = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
                bytes to "image/png"
            }
        } catch (e: Exception) {
            null
        }
    }

    // Robust bitmap decode with ImageDecoder on API 28+ then BitmapFactory fallback
    private fun decodeBitmapRobust(bytes: ByteArray): android.graphics.Bitmap? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                val source = android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Throwable) {
            try { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Throwable) { null }
        }
    }

    private fun writeImageToCacheAndGetUri(bytes: ByteArray): Uri? {
        return writeImageToCacheAndGetUri(bytes, "image/png")
    }

    private fun writeImageToCacheAndGetUri(bytes: ByteArray, mime: String): Uri? {
        return try {
            val dir = java.io.File(cacheDir, "shared_images")
            if (!dir.exists()) dir.mkdirs()
            val ext = when {
                mime.contains("png", true) -> "png"
                mime.contains("jpeg", true) || mime.contains("jpg", true) -> "jpg"
                mime.contains("webp", true) -> "webp"
                else -> "png"
            }
            val file = java.io.File(dir, "CapyWarp_${System.currentTimeMillis()}.$ext")
            file.outputStream().use { it.write(bytes) }
            FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun saveImageToGallery(bytes: ByteArray): Boolean {
        return saveImageToGallery(bytes, "image/png")
    }

    private fun saveImageToGallery(bytes: ByteArray, mime: String): Boolean {
        return try {
            val ext = when {
                mime.contains("png", true) -> "png"
                mime.contains("jpeg", true) || mime.contains("jpg", true) -> "jpg"
                mime.contains("webp", true) -> "webp"
                else -> "png"
            }
            val filename = "CapyWarp_${System.currentTimeMillis()}.$ext"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CapyWarp")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    private fun showToast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val CHANNEL_ID = "capywarp_results"
        const val NOTI_ID_PROGRESS = 9001
        const val NOTI_ID_RESULT_TEXT = 9002
        const val NOTI_ID_RESULT_IMAGE = 9003
        const val NOTI_ID_ERROR = 9004
        const val NOTI_ID_INFO = 9005

        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_PROMPT_ID = "extra_prompt_id"
        const val EXTRA_IMAGE_BYTES = "extra_image_bytes"
        const val EXTRA_IMAGE_URI = "extra_image_uri" // legacy for image-only input
        const val EXTRA_ATTACHMENT_URI = "extra_attachment_uri"
        const val EXTRA_ATTACHMENT_MIME = "extra_attachment_mime"

        const val ACTION_COPY_IMAGE = "org.parkjw.capywarp.action.COPY_IMAGE"
        const val ACTION_SAVE_IMAGE = "org.parkjw.capywarp.action.SAVE_IMAGE"
        const val ACTION_SHARE_IMAGE = "org.parkjw.capywarp.action.SHARE_IMAGE"
        const val ACTION_COPY_TEXT = "org.parkjw.capywarp.action.COPY_TEXT"

        const val REQ_COPY = 7001
        const val REQ_SAVE = 7002
        const val REQ_SHARE = 7003
        const val REQ_COPY_TEXT = 7004
        const val REQ_SHARE_TEXT = 7005
    }
}
