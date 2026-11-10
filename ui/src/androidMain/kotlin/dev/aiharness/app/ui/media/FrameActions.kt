package dev.aiharness.app.ui.media

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.FileProvider
import dev.aiharness.app.ui.R
import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What a frame does when opened outside the app's players, or held: open a page or a file, share
 * it, save it to the phone, or copy a link.
 */
object FrameActions {
    private fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** The file behind an attachment or picture, when it's stored in the app. */
    fun fileOf(blobs: FileBlobStore, ref: String?): File? = ref?.let { blobs.fileFor(it) }?.takeIf { it.exists() }

    /**
     * Opens a web address: in the app's own browser (Custom Tabs, its toolbar in the paper's
     * [toolbar] color, sliding up), or with [inApp] off in the phone's default browser. A music
     * service's link opens in its app when that's installed.
     */
    fun openLink(context: Context, url: String, inApp: Boolean, toolbar: Int, night: Boolean) {
        val uri = Uri.parse(url)
        val web = uri.scheme == "https" || uri.scheme == "http"
        if (inApp && web) {
            val colors = CustomTabColorSchemeParams.Builder().setToolbarColor(toolbar).setNavigationBarColor(toolbar).build()
            val tab = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colors)
                .setColorScheme(if (night) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
                .setShowTitle(true)
                .setStartAnimations(context, R.anim.sheet_up, R.anim.stay)
                .setExitAnimations(context, R.anim.stay, R.anim.sheet_down)
                .build()
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { tab.launchUrl(context, uri) }.isSuccess) return
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "No app on this phone opens that.", Toast.LENGTH_SHORT).show() }
    }

    /** Opens [attachment]: a link's page, or the file in whichever app handles it. */
    fun open(context: Context, blobs: FileBlobStore, attachment: ContentPart.Attachment) {
        val intent = when {
            attachment.kind == AttachmentKind.LINK -> attachment.url?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
            else -> {
                val ref = attachment.ref ?: attachment.tracks.firstOrNull()?.ref
                val mime = attachment.mimeType ?: attachment.tracks.firstOrNull()?.mimeType ?: "*/*"
                fileOf(blobs, ref)?.let { file ->
                    Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(context, file), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } ?: attachment.url?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
            }
        } ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "No app on this phone opens that.", Toast.LENGTH_SHORT).show() }
    }

    /** Shares a stored file (or a link's address) through the phone's share sheet. */
    fun share(context: Context, blobs: FileBlobStore, ref: String?, mime: String?, url: String? = null) {
        val file = fileOf(blobs, ref)
        val intent = when {
            file != null -> Intent(Intent.ACTION_SEND).setType(mime ?: "*/*").putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            url != null -> Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
            else -> return
        }
        runCatching { context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Whether this phone can save straight to its shared folders (Android 10 and newer). */
    val canSave: Boolean get() = Build.VERSION.SDK_INT >= 29

    /** Saves a stored file to the phone: pictures and videos to their folders, everything else to Downloads. */
    suspend fun save(context: Context, blobs: FileBlobStore, ref: String?, mime: String?, name: String?): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext false
        val file = fileOf(blobs, ref) ?: return@withContext false
        val type = mime ?: "application/octet-stream"
        val collection = when {
            type.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            type.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            type.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name ?: file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, type)
        }
        runCatching {
            val uri = context.contentResolver.insert(collection, values) ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            true
        }.getOrDefault(false)
    }

    fun copyLink(context: Context, url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Link", url))
    }
}
