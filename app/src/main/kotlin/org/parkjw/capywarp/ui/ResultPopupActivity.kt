package org.parkjw.capywarp.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ResultPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ResultPopupScreen(
                text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
                imageUri = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_IMAGE_URI, Uri::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_IMAGE_URI) as? Uri,
                onClose = { finish() }
            )
        }
    }


    companion object {
        const val EXTRA_TEXT = "popup_extra_text"
        const val EXTRA_IMAGE_URI = "popup_extra_image_uri"
    }
}

@Composable
private fun ResultPopupScreen(text: String, imageUri: Uri?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val conf = LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Raise initial position a bit to avoid overlapping the share panel
    val initialOffsetYPx = remember(conf) { with(density) { (-conf.screenHeightDp * 0.12f).dp.toPx() } }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(initialOffsetYPx) }

    // Back press closes
    BackHandler { onClose() }

    // Compute dynamic size caps to avoid overlapping with bottom system panels (e.g., share sheet)
    val maxSurfaceH = (conf.screenHeightDp * 0.45f).dp
    val maxContentH = (conf.screenHeightDp * 0.35f).dp

    // Background pass-through (no dim)
    Box(Modifier.fillMaxSize()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.Center)
                .sizeIn(minWidth = 260.dp, minHeight = 220.dp, maxWidth = 680.dp, maxHeight = 820.dp)
                .width((LocalConfiguration.current.screenWidthDp * 0.85f).dp)
                .heightIn(min = 260.dp, max = maxSurfaceH)
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()) }
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(onDrag = { _, dragAmount ->
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        })
                    }
                ) {
                    Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.popup_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) {
                        Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.close))
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (imageUri != null) {
                    // Show image
                    androidx.compose.foundation.Image(
                        painter = rememberAsyncImagePainterCompat(imageUri),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = maxContentH)
                            .background(Color.Black.copy(alpha = 0.06f))
                    )
                } else {
                    // Scrollable markdown-rendered text using AndroidView + Markwon
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { context ->
                            val scroll = android.widget.ScrollView(context).apply {
                                isFillViewport = false
                            }
                            val tv = android.widget.TextView(context).apply {
                                setTextColor(0xFFE0E0E0.toInt())
                                textSize = 14f
                                setLineSpacing(0f, 1.1f)
                                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                                setPadding(0, 0, 0, 0)
                            }
                            scroll.addView(tv)
                            // Render markdown
                            val markwon = io.noties.markwon.Markwon.create(context)
                            markwon.setMarkdown(tv, text)
                            scroll
                        },
                        update = { view ->
                            // Update text when recomposed
                            val tv = (view.getChildAt(0) as? android.widget.TextView)
                            if (tv != null) {
                                val markwon = io.noties.markwon.Markwon.create(view.context)
                                markwon.setMarkdown(tv, text)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 420.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    // Copy
                    Button(
                        onClick = {
                            try {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                if (imageUri != null) {
                                    val clip = ClipData.newUri(ctx.contentResolver, "CapyWarp Image", imageUri)
                                    cm.setPrimaryClip(clip)
                                } else {
                                    val clip = ClipData.newPlainText("CapyWarp", text)
                                    cm.setPrimaryClip(clip)
                                }
                                android.widget.Toast.makeText(ctx, ctx.getString(org.parkjw.capywarp.R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(ctx, ctx.getString(org.parkjw.capywarp.R.string.copy_failed, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3A3A3F),
                            contentColor = Color(0xFFFFFFFF)
                        )
                    ) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_copy_to_clipboard)) }

                    // Share
                    Button(
                        onClick = {
                            try {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    if (imageUri != null) {
                                        type = "image/*"
                                        putExtra(Intent.EXTRA_STREAM, imageUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    } else {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                }
                                val title = if (imageUri != null) ctx.getString(org.parkjw.capywarp.R.string.share_image_title) else ctx.getString(org.parkjw.capywarp.R.string.share_text_title)
                                val chooser = Intent.createChooser(send, title).apply {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                if (imageUri != null) {
                                    val resInfos = ctx.packageManager.queryIntentActivities(send, 0)
                                    for (ri in resInfos) {
                                        try {
                                            ctx.grantUriPermission(ri.activityInfo.packageName, imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        } catch (_: Exception) {}
                                    }
                                }
                                ctx.startActivity(chooser)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(ctx, ctx.getString(org.parkjw.capywarp.R.string.toast_share_error, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3A3A3F),
                            contentColor = Color(0xFFFFFFFF)
                        )
                    ) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_share)) }

                    if (imageUri != null) {
                        // Save to gallery
                        Button(
                            onClick = {
                                val ok = try { saveImageToGallery(ctx, imageUri) } catch (e: Exception) { false }
                                android.widget.Toast.makeText(ctx, if (ok) ctx.getString(org.parkjw.capywarp.R.string.toast_saved_to_gallery) else ctx.getString(org.parkjw.capywarp.R.string.toast_save_gallery_failed), android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3A3A3F),
                                contentColor = Color(0xFFFFFFFF)
                            )
                        ) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_save_gallery)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberAsyncImagePainterCompat(uri: Uri): androidx.compose.ui.graphics.painter.Painter {
    // Minimal inline painter using Android bitmap decode to avoid adding an image library dependency.
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                bmp = android.graphics.BitmapFactory.decodeStream(stream)
            }
        }
    }
    return remember(bmp) {
        if (bmp != null) androidx.compose.ui.graphics.painter.BitmapPainter(bmp!!.asImageBitmap())
        else androidx.compose.ui.graphics.painter.ColorPainter(Color(0x11000000))
    }
}

private fun saveImageToGallery(ctx: Context, uri: Uri): Boolean {
    return try {
        val mime = ctx.contentResolver.getType(uri) ?: "image/png"
        val ext = when {
            mime.contains("png", true) -> "png"
            mime.contains("jpeg", true) || mime.contains("jpg", true) -> "jpg"
            mime.contains("webp", true) -> "webp"
            else -> "png"
        }
        val filename = "CapyWarp_${System.currentTimeMillis()}.$ext"
        val values = ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CapyWarp")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = ctx.contentResolver
        val outUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val ok = resolver.openOutputStream(outUri)?.use { out ->
            resolver.openInputStream(uri)?.use { input -> input.copyTo(out); true } ?: false
        } ?: false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(outUri, cv, null, null)
        }
        ok
    } catch (e: Exception) {
        false
    }
}
