package org.parkjw.capywarp.ui

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import io.noties.markwon.Markwon
import android.text.method.LinkMovementMethod
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Lightweight system overlay popup shown over other apps. Used when resultAction == popup (4).
 * Requires android.permission.SYSTEM_ALERT_WINDOW and user-granted overlay permission.
 * This implementation uses classic Android Views to avoid Compose lifecycle requirements.
 */
object OverlayPopupManager {
    private var wm: WindowManager? = null
    private var view: View? = null

    fun canDrawOverlays(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun requestOverlayPermission(ctx: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun showText(ctx: Context, text: String, isDark: Boolean): Boolean {
        return show(ctx, isDark = isDark, text = text, imageUri = null)
    }

    fun showImage(ctx: Context, imageUri: Uri, isDark: Boolean): Boolean {
        return show(ctx, isDark = isDark, text = null, imageUri = imageUri)
    }

    @Synchronized
    private fun show(ctx: Context, isDark: Boolean, text: String? = null, imageUri: Uri? = null): Boolean {
        if (!canDrawOverlays(ctx)) return false
        dismiss()
        val appCtx = ctx.applicationContext
        val manager = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Display metrics first so we can choose an initial Y offset above the center
        val dm = appCtx.resources.displayMetrics
        val osNight = (appCtx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        Log.d("CapyWarp/OverlayPopup", "show(): kind=${if (imageUri!=null) "image" else "text"}, osNight=$osNight, applied.isDark=$isDark")
        // Palette mapped to our Compose theme tokens (approx)
        val bgColor = if (isDark) 0xFF121214.toInt() else 0xFFFFFFFF.toInt() // surface
        val onSurfaceColor = if (isDark) 0xFFE0E0E0.toInt() else 0xFF1A1C19.toInt()
        val onSurfaceVariantColor = if (isDark) 0xFFC2C3C7.toInt() else 0xFF44474E.toInt()
        val secondaryContainerColor = if (isDark) 0xFF2E3440.toInt() else 0xFFDCE3EE.toInt()
        val onSecondaryContainerColor = if (isDark) 0xFFDDE3EA.toInt() else 0xFF141B22.toInt()
        val outlineColor = if (isDark) 0xFF6E7074.toInt() else 0xFF74777F.toInt()
        val outlineAlpha66 = (0x66000000).toInt() or (outlineColor and 0x00FFFFFF)
        val imageBgColor = if (isDark) 0x11000000 else 0x1F000000

        val initialYOffset = - (dm.heightPixels * 0.15f).toInt() // raise popup by ~15% of screen height
        val lp = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.CENTER
            x = 0
            y = initialYOffset
            format = PixelFormat.TRANSLUCENT
            flags = (
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
        }

        // Root container (card-like)
        val maxW = (dm.widthPixels * 0.85f).toInt()
        // Further limit content height to avoid covering system share sheet or bottom panels
        val maxImageH = (dm.heightPixels * 0.35f).toInt()

        // Root container (card-like)
        val root = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (dm.density * 12).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = 16f * dm.density
                setColor(bgColor)
                val outlineAlpha33 = (0x33000000).toInt() or (outlineColor and 0x00FFFFFF)
                setStroke((1 * dm.density).toInt(), outlineAlpha33)
            }
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    true
                } else false
            }
        }
        // Helper to activate focus so BACK works
        fun activateFocus() {
            if (!root.isFocusableInTouchMode) {
                root.isFocusableInTouchMode = true
                root.requestFocus()
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                try { manager.updateViewLayout(root, lp) } catch (_: Exception) {}
            }
        }
        // Inner content container to constrain max width
        val container = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(maxW, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(container)

        // Prepare drag-to-move state (attach to header to avoid scroll conflicts)
        var lastX = 0f
        var lastY = 0f
        var dragging = false

        // Header
        val header = LinearLayout(appCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val title = TextView(appCtx).apply {
            this.text = appCtx.getString(org.parkjw.capywarp.R.string.popup_title)
            setTextColor(onSurfaceColor)
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val close = TextView(appCtx).apply {
            this.text = appCtx.getString(org.parkjw.capywarp.R.string.close)
            setTextColor(onSurfaceVariantColor)
            textSize = 16f
            setPadding(dp(appCtx, 12), dp(appCtx, 6), dp(appCtx, 12), dp(appCtx, 6))
            setOnClickListener { dismiss() }
        }
        header.addView(title)
        header.addView(close)
        // Attach drag to header only (avoid scroll conflict)
        header.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { 
                    activateFocus()
                    lastX = ev.rawX; lastY = ev.rawY; dragging = false; true 
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - lastX).toInt()
                    val dy = (ev.rawY - lastY).toInt()
                    if (dx != 0 || dy != 0) dragging = true
                    lastX = ev.rawX
                    lastY = ev.rawY
                    updatePosAbsolute((lp.x + dx), (lp.y + dy))
                    true
                }
                else -> false
            }
        }
        container.addView(header)

        // Content
        if (imageUri != null) {
            val iv = ImageView(appCtx).apply {
                adjustViewBounds = true
                maxWidth = maxW
                maxHeight = maxImageH
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(imageBgColor)
                setOnTouchListener { _, ev -> if (ev.action == MotionEvent.ACTION_DOWN) { activateFocus(); false } else false }
                try {
                    setImageURI(imageUri)
                } catch (_: Exception) {}
            }
            container.addView(iv)
        } else if (text != null) {
            val markwon = Markwon.create(appCtx)
            val tv = TextView(appCtx).apply {
                setTextColor(onSurfaceColor)
                textSize = 14f
                setLineSpacing(0f, 1.1f)
                movementMethod = LinkMovementMethod.getInstance()
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            // Render markdown into TextView
            markwon.setMarkdown(tv, text)
            val sv = ScrollView(appCtx).apply {
                isFillViewport = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxImageH) // cap height
                setPadding(0, dp(appCtx, 4), 0, 0)
                addView(tv)
            }
            container.addView(sv)
        }

        // Actions row
        val actions = LinearLayout(appCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(appCtx, 12)
            }
        }
        fun addButton(label: String, onClick: () -> Unit) {
            val b = TextView(appCtx).apply {
                this.text = label
                setTextColor(onSecondaryContainerColor)
                setPadding(dp(appCtx, 12), dp(appCtx, 9), dp(appCtx, 12), dp(appCtx, 9))
                background = GradientDrawable().apply {
                    cornerRadius = 12f * appCtx.resources.displayMetrics.density
                    setColor(secondaryContainerColor)
                    setStroke((1 * appCtx.resources.displayMetrics.density).toInt(), outlineAlpha66)
                }
                setOnClickListener { onClick() }
            }
            val lpBtn = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lpBtn.setMargins(dp(appCtx, 8), 0, dp(appCtx, 8), 0)
            actions.addView(b, lpBtn)
        }
        // Copy
        addButton(appCtx.getString(org.parkjw.capywarp.R.string.action_copy_to_clipboard)) {
            try {
                val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                if (imageUri != null) {
                    cm.setPrimaryClip(android.content.ClipData.newUri(appCtx.contentResolver, "CapyWarp Image", imageUri))
                } else {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("CapyWarp", text ?: ""))
                }
                android.widget.Toast.makeText(appCtx, appCtx.getString(org.parkjw.capywarp.R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(appCtx, appCtx.getString(org.parkjw.capywarp.R.string.copy_failed, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        // Share
        addButton(appCtx.getString(org.parkjw.capywarp.R.string.action_share)) {
            try {
                val send = Intent(Intent.ACTION_SEND).apply {
                    if (imageUri != null) {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text ?: "")
                    }
                }
                val title = if (imageUri != null) appCtx.getString(org.parkjw.capywarp.R.string.share_image_title) else appCtx.getString(org.parkjw.capywarp.R.string.share_text_title)
                val chooser = Intent.createChooser(send, title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (imageUri != null) {
                    // Proactively grant read permission to all resolved targets
                    val resInfos = appCtx.packageManager.queryIntentActivities(send, 0)
                    for (ri in resInfos) {
                        try {
                            appCtx.grantUriPermission(ri.activityInfo.packageName, imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (_: Exception) {}
                    }
                }
                appCtx.startActivity(chooser)
            } catch (e: Exception) {
                android.widget.Toast.makeText(appCtx, appCtx.getString(org.parkjw.capywarp.R.string.toast_share_error, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        // Save (image only)
        if (imageUri != null) {
            addButton(appCtx.getString(org.parkjw.capywarp.R.string.action_save_gallery)) {
                val ok = try { saveImageToGallery(appCtx, imageUri) } catch (_: Exception) { false }
                android.widget.Toast.makeText(appCtx, if (ok) appCtx.getString(org.parkjw.capywarp.R.string.toast_saved_to_gallery) else appCtx.getString(org.parkjw.capywarp.R.string.toast_save_gallery_failed), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(actions)

        return try {
            manager.addView(root, lp)
            wm = manager
            view = root
            // Ensure the overlay actually receives key events (e.g., BACK)
            try {
                root.requestFocus()
                root.post { try { root.requestFocusFromTouch() } catch (_: Exception) {} }
            } catch (_: Exception) {}
            true
        } catch (_: Exception) {
            dismiss()
            false
        }
    }

    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun updatePosAbsolute(x: Int, y: Int) {
        val v = view ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        lp.x = x
        lp.y = y
        try { wm?.updateViewLayout(v, lp) } catch (_: Exception) {}
    }

    @Synchronized
    fun dismiss() {
        val v = view
        val manager = wm
        if (v != null && manager != null) {
            runCatching { manager.removeView(v) }
        }
        view = null
        wm = null
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
            val values = android.content.ContentValues().apply {
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
                val cv = android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(outUri, cv, null, null)
            }
            ok
        } catch (e: Exception) {
            false
        }
    }
}
