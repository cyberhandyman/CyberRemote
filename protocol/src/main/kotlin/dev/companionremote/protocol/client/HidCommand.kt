package dev.companionremote.protocol.client

/**
 * HID command codes, ported from pyatv
 * `pyatv/protocols/companion/api.py HidCommand`.
 */
enum class HidCommand(val code: Long) {
    Up(1),
    Down(2),
    Left(3),
    Right(4),
    Menu(5),
    Select(6),
    Home(7),
    VolumeUp(8),
    VolumeDown(9),
    Siri(10),
    Screensaver(11),
    Sleep(12),
    Wake(13),
    PlayPause(14),
    ChannelIncrement(15),
    ChannelDecrement(16),
    Guide(17),
    PageUp(18),
    PageDown(19),
}

/** Media Control commands (`api.py MediaControlCommand`). */
enum class MediaControlCommand(val code: Long) {
    Play(1),
    Pause(2),
    NextTrack(3),
    PreviousTrack(4),
    GetVolume(5),
    SetVolume(6),
    SkipBy(7),
    FastForwardBegin(8),
    FastForwardEnd(9),
    RewindBegin(10),
    RewindEnd(11),
    GetCaptionSettings(12),
    SetCaptionSettings(13),
}

/** System power states (`api.py SystemStatus`). */
enum class SystemStatus(val code: Long) {
    Unknown(0),
    Asleep(1),
    Screensaver(2),
    Awake(3),
    Idle(4),
    ;

    companion object {
        fun fromCode(code: Long): SystemStatus = entries.firstOrNull { it.code == code } ?: Unknown
    }
}

/** Keyboard focus states (`pyatv/const.py KeyboardFocusState`). */
enum class KeyboardFocusState {
    Unknown,
    Unfocused,
    Focused,
}

/** Touch phases (`pyatv/const.py TouchAction`). */
enum class TouchPhase(val code: Long) {
    Press(1),
    Hold(3),
    Release(4),
    Click(5),
}
