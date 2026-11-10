package dev.aiharness.app.platform

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.media.FrameActions
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.PlannerEntry
import java.io.File
import kotlinx.coroutines.launch
import java.time.LocalTime

/*
 * The phone's side of Expect.kt: the code each screen had before the screens were shared, moved
 * here unchanged in what it does.
 */

actual val deviceNoun: String = "phone"

actual val deviceOptionsCard: (@Composable () -> Unit)? = null

actual val hasDeviceCalendars: Boolean = true

actual val hasCamera: Boolean = true

actual val systemMotionSetting: String? = "Android's \"Remove animations\""

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun rememberPicker(kind: PickKind, onPicked: (List<Picked>) -> Unit): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    return when (kind) {
        PickKind.PHOTOS -> {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris -> picked(uris.map { Picked(it.toString()) }) }
            remember(launcher) { { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
        }
        PickKind.PHOTO -> {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> picked(listOfNotNull(uri?.let { Picked(it.toString()) })) }
            remember(launcher) { { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
        }
        PickKind.VIDEO -> {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> picked(listOfNotNull(uri?.let { Picked(it.toString()) })) }
            remember(launcher) { { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) } }
        }
        PickKind.FILE -> {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> picked(listOfNotNull(uri?.let { Picked(it.toString()) })) }
            remember(launcher) { { launcher.launch(arrayOf("*/*")) } }
        }
        PickKind.SONGS -> {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> picked(uris.map { Picked(it.toString()) }) }
            remember(launcher) { { launcher.launch(arrayOf("audio/*")) } }
        }
    }
}

// Mouse and keyboard: nothing on the phone (see Expect.kt), so its screens stay as they were.

actual fun Modifier.onRightClick(action: () -> Unit): Modifier = this

actual val dragsWithoutHolding: Boolean = false

actual suspend fun PointerInputScope.detectHoldDrag(
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) = detectDragGesturesAfterLongPress(onDragStart = onDragStart, onDragEnd = onDragEnd, onDragCancel = onDragCancel, onDrag = onDrag)

actual fun Modifier.onWheel(onWheel: (steps: Float, shift: Boolean) -> Unit): Modifier = this

actual fun Modifier.sendOnCtrlEnter(onSend: () -> Unit): Modifier = this

actual fun Modifier.scrollbar(state: ScrollState): Modifier = this

actual fun Modifier.scrollbar(state: LazyListState): Modifier = this

actual fun Modifier.scrollbar(state: LazyGridState): Modifier = this

/** The phone's pickers add files; nothing is dropped or pasted. */
@Composable
actual fun Modifier.receiveFiles(onFiles: (List<Picked>) -> Unit): Modifier = this

@Composable
actual fun inFrontOfYou(): Boolean = true

@Composable
actual fun rememberCamera(onTaken: (Picked) -> Unit): (() -> Unit)? {
    val media = LocalAppContainer.current.media
    val taken by rememberUpdatedState(onTaken)
    var cameraShot by remember { mutableStateOf<Picked?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val shot = cameraShot
        if (ok && shot != null) taken(shot)
    }
    return remember(media, takePicture) {
        {
            media.cameraTarget()?.let { target ->
                cameraShot = target
                takePicture.launch(Uri.parse(target.uri))
            }
        }
    }
}

@Composable
actual fun rememberCalendarPermission(onResult: (granted: Boolean) -> Unit): (() -> Unit)? {
    val result by rememberUpdatedState(onResult)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> result(granted) }
    return { ask.launch(Manifest.permission.READ_CALENDAR) }
}

@Composable
actual fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
actual fun rememberApproximateLocation(onLocation: (latitude: Double, longitude: Double) -> Unit, onProblem: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val found by rememberUpdatedState(onLocation)
    val problem by rememberUpdatedState(onProblem)
    val save = { location: Location -> found(location.latitude, location.longitude) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            fetchCoarseLocation(context, save) { problem(it) }
        } else {
            problem("Location permission declined. You can type a latitude and longitude instead.")
        }
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchCoarseLocation(context, save) { problem(it) }
        } else {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}

/** The freshest last-known coarse location, or a one-off fix on Android 11+. No Play services needed. */
private fun fetchCoarseLocation(context: Context, onLocation: (Location) -> Unit, onProblem: (String) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java) ?: return onProblem("No location service on this device.")
    try {
        val known = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        if (known != null) return onLocation(known)
        if (Build.VERSION.SDK_INT >= 30 && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, ContextCompat.getMainExecutor(context)) { location ->
                if (location != null) onLocation(location) else onProblem("Couldn't get a location fix. Try again, or type it in.")
            }
        } else {
            onProblem("Couldn't get a location fix. Try again, or type it in.")
        }
    } catch (e: SecurityException) {
        onProblem("Location permission is needed for sunrise and sunset.")
    }
}

@Composable
actual fun systemUses24HourClock(): Boolean {
    val context = LocalContext.current
    return remember(context) { DateFormat.is24HourFormat(context) }
}

@Composable
actual fun systemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

@Composable
actual fun rememberTimePicker(is24: Boolean, onPicked: (LocalTime) -> Unit): (LocalTime) -> Unit {
    val context = LocalContext.current
    val twentyFour by rememberUpdatedState(is24)
    val picked by rememberUpdatedState(onPicked)
    return { time -> TimePickerDialog(context, { _, h, m -> picked(LocalTime.of(h, m)) }, time.hour, time.minute, twentyFour).show() }
}

@Composable
actual fun rememberOutside(): Outside {
    val context = LocalContext.current
    val blobs = LocalAppContainer.current.blobStore
    return remember(context, blobs) { AndroidOutside(context, blobs) }
}

/** [FrameActions] and the rest, with the screen's own context (Custom Tabs slide up from an activity). */
private class AndroidOutside(private val context: Context, private val blobs: FileBlobStore) : Outside {
    override val device = "phone"

    override fun openLink(url: String, inApp: Boolean, toolbar: Int, night: Boolean) = FrameActions.openLink(context, url, inApp, toolbar, night)

    override fun open(attachment: ContentPart.Attachment) = FrameActions.open(context, blobs, attachment)

    override val canShare = true

    override val canSave: Boolean get() = FrameActions.canSave

    override fun share(ref: String?, mime: String?, url: String?) = FrameActions.share(context, blobs, ref, mime, url)

    /** Copied into the cache's exports folder first, which the file provider can hand out, under its own name. */
    override fun shareFile(file: File, name: String, mime: String) {
        runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val named = File(dir, name.replace('/', '-'))
            file.copyTo(named, overwrite = true)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", named)
            val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { toast("Couldn't share it.") }
    }

    override suspend fun save(ref: String?, mime: String?, name: String?): Boolean = FrameActions.save(context, blobs, ref, mime, name)

    override fun copyText(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    override fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    /** A phone calendar's event opens in the phone's calendar app (it's changed there, not here). */
    override fun openPhoneEvent(entry: PlannerEntry) {
        val (eventId, _) = dev.aiharness.core.planner.PhoneEntries.parse(entry.id) ?: return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

actual fun pngOf(bytes: ByteArray): ByteArray? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
    java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

/** Android's save dialog (a document the user names and places), then the file copied into it. */
@Composable
actual fun rememberSaveAs(onSaved: (Boolean) -> Unit): (file: File, name: String, mime: String) -> Unit {
    val context = LocalContext.current
    val saved by rememberUpdatedState(onSaved)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pending by remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(SaveDocument) { uri ->
        val file = pending
        pending = null
        if (uri == null || file == null) {
            saved(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null }.getOrDefault(false)
            }
            dev.aiharness.app.ui.onMain { saved(ok) }
        }
    }
    return remember(launcher) { { file, name, mime -> pending = file; launcher.launch(name to mime) } }
}

/** ACTION_CREATE_DOCUMENT with the type chosen per file (the stock contract fixes it once). */
private object SaveDocument : androidx.activity.result.contract.ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input.second).putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}

actual fun decodeSampledImage(file: File, maxSize: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

@Composable
actual fun rememberVideoController(): VideoController {
    val context = LocalContext.current
    return remember { ExoVideo(context) }
}

private class ExoVideo(context: Context) : VideoController {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), /* handleAudioFocus = */ true)
        .build()

    override val isPlaying: Boolean get() = player.isPlaying
    override val currentPosition: Long get() = player.currentPosition
    override val duration: Long get() = player.duration
    override var volume: Float
        get() = player.volume
        set(value) {
            player.volume = value
        }

    override fun load(file: File) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
    }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(ms: Long) = player.seekTo(ms)
    override fun release() = player.release()
}

@Composable
actual fun VideoSurface(controller: VideoController, modifier: Modifier) {
    ContentFrame((controller as ExoVideo).player, modifier, contentScale = ContentScale.Fit)
}

@Composable
actual fun rememberScreenTurner(): (landscape: Boolean) -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    return { landscape ->
        activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The service's own player, in a web view. It loads only the embed (pages it links to open in the
 * browser), with the app as its origin, which embedded players ask apps to send.
 */
@Composable
actual fun EmbedView(embed: String, modifier: Modifier) {
    val context = LocalContext.current
    val src = embed.replace("\"", "%22").replace("<", "%3C")
    val page = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        |<style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style></head>
        |<body><iframe src="$src" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen
        |referrerpolicy="strict-origin-when-cross-origin"></iframe></body></html>""".trimMargin()
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        return true
                    }
                }
                loadDataWithBaseURL("https://${ctx.packageName}/", page, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier,
    )
}

actual fun newSongPreview(): SongPreview = MediaPlayerPreview()

/** Plays one song's preview at a time, from the web; stops when the tray goes. */
private class MediaPlayerPreview : SongPreview {
    override var playing by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null

    override fun toggle(url: String?) {
        val was = playing
        stop()
        if (url == null || url == was) return
        playing = url
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { if (playing == url) stop() }
                setOnErrorListener { _, _, _ -> if (playing == url) stop(); true }
                prepareAsync()
            }
        }.getOrElse { playing = null; null }
    }

    override fun stop() {
        player?.let { runCatching { it.reset(); it.release() } }
        player = null
        playing = null
    }
}
