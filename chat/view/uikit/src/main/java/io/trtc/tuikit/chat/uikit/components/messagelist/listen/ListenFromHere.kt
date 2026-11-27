package io.trtc.tuikit.chat.uikit.components.messagelist.listen
import io.trtc.tuikit.chat.uikit.components.ai.tts.TtsTextSanitizer
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.senderDisplayName
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MergedMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload

// A single unit of the "listen from here" playback queue. [speechText] is
// synthesized via TTS; for voice messages [audioPath] is the original audio to
// play right after the spoken prefix (null otherwise).
internal data class ListenItem(
    val speechText: String,
    val audioPath: String? = null
)

// Resolved, context-free strings supplied to [buildListenPlan] so the planning
// logic stays a pure function (no Android Context) and is easy to unit test.
// Mirrors the Flutter ChatLocalizations listen* keys: media/merged use a name
// prefix (e.g. "{name} sent an image") rather than a separate "said" prefix.
internal data class ListenPlanResources(
    val selfSpeaker: String,
    val says: (name: String) -> String,
    val sentImage: (name: String) -> String,
    val sentVideo: (name: String) -> String,
    val sentFile: (name: String) -> String,
    val sentMerged: (name: String, title: String) -> String
)

private const val SELF_SPEAKER_KEY = "self"
private const val OTHER_SPEAKER_KEY_PREFIX = "user:"

// Build the ordered playback plan from [messages] (expected oldest -> newest).
//
// - text: speaks "{name}says:{content}"; emoji-only text is skipped entirely.
// - image/video/file: speaks "{name} sent an image/video/file".
// - merged: speaks "{name} sent {title}".
// - audio: speaks "{name}says:" then plays the original audio.
// - other types are skipped.
//
// When a message shares its sender with the previously spoken message, the
// speaker announcement is omitted: text falls back to the bare content, media
// and merged use an empty name (then trim) so the listener isn't told the same
// name repeatedly.
internal fun buildListenPlan(
    messages: List<MessageInfo>,
    resources: ListenPlanResources
): List<ListenItem> {
    val items = mutableListOf<ListenItem>()
    var lastSpeakerKey: String? = null
    for (message in messages) {
        val isSelf = message.isSentBySelf
        val speakerKey = if (isSelf) SELF_SPEAKER_KEY else OTHER_SPEAKER_KEY_PREFIX + message.from.userID
        val speaker = if (isSelf) resources.selfSpeaker else message.senderDisplayName
        val sameAsPrevious = lastSpeakerKey != null && lastSpeakerKey == speakerKey
        when (message.messageType) {
            MessageType.TEXT -> {
                val raw = (message.messagePayload as? TextMessagePayload)?.text ?: ""
                val content = TtsTextSanitizer.sanitize(raw)
                // Emoji-only text has nothing to speak: skip without claiming the
                // speaker slot so the next message still announces its sender.
                if (content.isEmpty()) continue
                val speechText = if (sameAsPrevious) content else resources.says(speaker) + content
                items.add(ListenItem(speechText = speechText))
            }

            MessageType.IMAGE -> {
                val speechText =
                    if (sameAsPrevious) resources.sentImage("").trim() else resources.sentImage(speaker)
                items.add(ListenItem(speechText = speechText))
            }

            MessageType.VIDEO -> {
                val speechText =
                    if (sameAsPrevious) resources.sentVideo("").trim() else resources.sentVideo(speaker)
                items.add(ListenItem(speechText = speechText))
            }

            MessageType.FILE -> {
                val speechText =
                    if (sameAsPrevious) resources.sentFile("").trim() else resources.sentFile(speaker)
                items.add(ListenItem(speechText = speechText))
            }

            MessageType.MERGED -> {
                val rawTitle = (message.messagePayload as? MergedMessagePayload)?.title ?: ""
                val title = TtsTextSanitizer.sanitize(rawTitle)
                val speechText = if (sameAsPrevious) {
                    resources.sentMerged("", title).trim()
                } else {
                    resources.sentMerged(speaker, title)
                }
                items.add(ListenItem(speechText = speechText))
            }

            MessageType.AUDIO -> {
                val payload = message.messagePayload as? AudioMessagePayload
                val path = payload?.audioPath?.takeIf { it.isNotEmpty() }
                    ?: payload?.audioURL?.takeIf { it.isNotEmpty() }
                val speechText = if (sameAsPrevious) "" else resources.says(speaker)
                items.add(ListenItem(speechText = speechText, audioPath = path))
            }

            else -> continue
        }
        lastSpeakerKey = speakerKey
    }
    return items
}
