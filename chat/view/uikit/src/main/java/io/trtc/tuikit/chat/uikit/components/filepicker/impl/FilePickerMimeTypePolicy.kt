package io.trtc.tuikit.chat.uikit.components.filepicker.impl
internal object FilePickerMimeTypePolicy {

    fun resolve(allowedMimeTypes: List<String>): ResolvedMimeTypes {
        val mimeTypes = allowedMimeTypes
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return when (mimeTypes.size) {
            0 -> ResolvedMimeTypes(intentType = "*/*", extraMimeTypes = emptyArray())
            1 -> ResolvedMimeTypes(intentType = mimeTypes.single(), extraMimeTypes = emptyArray())
            else -> ResolvedMimeTypes(intentType = "*/*", extraMimeTypes = mimeTypes.toTypedArray())
        }
    }
}

internal data class ResolvedMimeTypes(
    val intentType: String,
    val extraMimeTypes: Array<String>,
) {
    val hasExtraMimeTypes: Boolean
        get() = extraMimeTypes.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResolvedMimeTypes) return false
        if (intentType != other.intentType) return false
        return extraMimeTypes.contentEquals(other.extraMimeTypes)
    }

    override fun hashCode(): Int {
        var result = intentType.hashCode()
        result = 31 * result + extraMimeTypes.contentHashCode()
        return result
    }
}
