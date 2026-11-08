package dev.aiharness.app.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.graphics.ImageBitmap
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.PlannerEntry
import java.io.File
import java.time.LocalTime

/*
 * What screens draw or launch through the platform. Each has its actual in ui/src/androidMain (the
 * phone's code, as it was before the screens were shared) and ui/src/desktopMain.
 */

/** What the app calls the device it runs on, in what it says: "phone" or "computer". */
expect val deviceNoun: String

/** Whether the device has calendars the planner can read (the phone's; none on a computer yet). */
expect val hasDeviceCalendars: Boolean

/** Whether the device has a camera to take a photo for a letter. */
expect val hasCamera: Boolean

/** The device's own "no animations" setting, as the Media page names it; null where there's none. */
expect val systemMotionSetting: String?

/**
 * A Settings card of what only this device has, on the Heartbeat page (on a computer: keeping
 * Fumibako in the tray, starting at login); null where there's nothing.
 */
expect val deviceOptionsCard: (@Composable () -> Unit)?

/** Back: the phone's back gesture; Escape on a computer. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** What a picker picks. */
enum class PickKind {
    /** Up to 12 photos. */
    PHOTOS,

    /** One photo. */
    PHOTO,

    /** One video. */
    VIDEO,

    /** Any one file. */
    FILE,

    /** One or more songs. */
    SONGS,
}

/** Opens the platform's picker for [kind]; [onPicked] gets what was picked (nothing when cancelled). */
@Composable
expect fun rememberPicker(kind: PickKind, onPicked: (List<Picked>) -> Unit): () -> Unit

/**
 * Files dropped on what this modifies, or pasted into it (copied files or a picture), go to
 * [onFiles]: a computer's way to add them to a letter. Nothing on the phone, whose pickers do it.
 */
@Composable
expect fun Modifier.receiveFiles(onFiles: (List<Picked>) -> Unit): Modifier

/**
 * Whether the screen is in front of you, so letters arriving on it count as read: on the phone,
 * whenever it shows; on a computer, while the window has the focus (not hidden in the tray,
 * minimized, or behind the window you're using).
 */
@Composable
expect fun inFrontOfYou(): Boolean

/*
 * Mouse and keyboard (docs/plan-desktop.md, "Mouse, keyboard and the window"). Each is nothing on
 * the phone, so its screens stay as they are.
 */

/** A right-click doing what a long press does: put beside each long press. */
expect fun Modifier.onRightClick(action: () -> Unit): Modifier

/**
 * Whether a drag starts as soon as the pointer moves (a mouse) rather than after holding (a finger,
 * whose first move scrolls the page).
 */
expect val dragsWithoutHolding: Boolean

/** "Hold and drag": after a long press on the phone, straight away with a mouse. */
expect suspend fun PointerInputScope.detectHoldDrag(
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
)

/**
 * The mouse wheel, in place of a pinch or twist: [onWheel] gets its steps (positive toward you) and
 * whether Shift is held. Wheel turns used here don't scroll the page.
 */
expect fun Modifier.onWheel(onWheel: (steps: Float, shift: Boolean) -> Unit): Modifier

/** Ctrl+Enter (Command+Enter on a Mac) in a note sends it; Enter alone starts a new line. */
expect fun Modifier.sendOnCtrlEnter(onSend: () -> Unit): Modifier

/** A scrollbar along the right edge of a long page (put before its verticalScroll). */
expect fun Modifier.scrollbar(state: ScrollState): Modifier

/** A scrollbar along the right edge of a long list. */
expect fun Modifier.scrollbar(state: LazyListState): Modifier

/** A scrollbar along the right edge of a grid. */
expect fun Modifier.scrollbar(state: LazyGridState): Modifier

/** A page that scrolls, with its scrollbar on a computer: verticalScroll with [scrollbar]. */
@Composable
fun Modifier.scrollingPage(state: ScrollState = rememberScrollState()): Modifier = scrollbar(state).verticalScroll(state)

/** The camera: takes a photo into [MediaImporter.cameraTarget] and hands it over. Null without one. */
@Composable
expect fun rememberCamera(onTaken: (Picked) -> Unit): (() -> Unit)?

/** Asks to read the device's calendars; [onResult] says whether it may. Null where there are none to read. */
@Composable
expect fun rememberCalendarPermission(onResult: (granted: Boolean) -> Unit): (() -> Unit)?

/** Asks once for permission to notify, where the platform asks for it. */
@Composable
expect fun RequestNotificationPermission()

/**
 * Finds the device's approximate location (asking for permission first), for sunrise and sunset.
 * Null where there's no location to ask for: the latitude and longitude are typed in.
 */
@Composable
expect fun rememberApproximateLocation(onLocation: (latitude: Double, longitude: Double) -> Unit, onProblem: (String) -> Unit): (() -> Unit)?

/** Whether the device's own clock is 24-hour (the clock setting's "Phone's setting", "Computer's setting"). */
@Composable
expect fun systemUses24HourClock(): Boolean

/** Whether the device asks for no animations (Android's "Remove animations"). */
@Composable
expect fun systemReducedMotion(): Boolean

/** A time picker: call the result with the time to start from; [onPicked] gets the new one. */
@Composable
expect fun rememberTimePicker(is24: Boolean, onPicked: (LocalTime) -> Unit): (LocalTime) -> Unit

/** Things handed to the world outside the app: links, files, saving, the clipboard, short notices. */
interface Outside {
    /** "phone" or "computer", for what the app says ("Saved to your phone."). */
    val device: String

    fun openLink(url: String, inApp: Boolean, toolbar: Int, night: Boolean)

    /** Opens an attachment's file (or its link) in the app that opens that kind of file. */
    fun open(attachment: ContentPart.Attachment)

    /** Whether [share] can send things to other apps here. */
    val canShare: Boolean

    /** Whether [save] can save here (on the phone, Android 10 and newer). */
    val canSave: Boolean

    fun share(ref: String?, mime: String?, url: String? = null)

    /** Saves a picture or file where the device keeps them; false if it couldn't. */
    suspend fun save(ref: String?, mime: String?, name: String?): Boolean

    fun copyText(text: String, label: String = "Link")

    fun toast(message: String)

    /** Opens one of the device's calendar events (the planner's pencil entries). */
    fun openPhoneEvent(entry: PlannerEntry)
}

@Composable
expect fun rememberOutside(): Outside

/** A picture from its encoded bytes (PNG, JPEG...), or null if it isn't one. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/** A picture file, decoded at about [maxSize] on its longer side (or a little larger). */
expect fun decodeSampledImage(file: File, maxSize: Int): ImageBitmap?

/** Plays one video file at a time. */
interface VideoController {
    val isPlaying: Boolean
    val currentPosition: Long

    /** The length once it's known, else zero or less. */
    val duration: Long
    var volume: Float

    fun load(file: File)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun release()
}

@Composable
expect fun rememberVideoController(): VideoController

/** Where [controller]'s picture shows, fitted inside [modifier]. */
@Composable
expect fun VideoSurface(controller: VideoController, modifier: Modifier)

/** Turns the screen for a video: landscape, or back to however the device is held (false). */
@Composable
expect fun rememberScreenTurner(): (landscape: Boolean) -> Unit

/** A music service's own player (YouTube, SoundCloud, Bandcamp) from its embed URL. */
@Composable
expect fun EmbedView(embed: String, modifier: Modifier)

/** Plays one song preview from the web at a time. */
interface SongPreview {
    /** The preview playing (its URL), or null. Compose state. */
    val playing: String?

    /** Plays [url], or stops it if it's the one playing. */
    fun toggle(url: String?)

    fun stop()
}

expect fun newSongPreview(): SongPreview
