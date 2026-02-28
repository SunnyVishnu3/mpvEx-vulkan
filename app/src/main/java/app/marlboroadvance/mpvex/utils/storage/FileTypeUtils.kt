package app.marlboroadvance.mpvex.utils.storage

import java.io.File
import java.util.Locale

/**
 * File Type Utilities
 * Handles file type detection and categorization
 */
object FileTypeUtils {
    
    /**
     * File type categories
     */
    enum class FileType {
        VIDEO,
        AUDIO,
        IMAGE,
        DOCUMENT,
        ARCHIVE,
        CODE,
        OTHER
    }
    
    // Video file extensions
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "3g2",
        "mpg", "mpeg", "m2v", "ogv", "ts", "mts", "m2ts", "vob", "divx", "xvid",
        "f4v", "rm", "rmvb", "asf"
    )
    
    // Audio file extensions
    val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav", "wma", "ape",
        "wv", "mpc", "tta", "tak", "dsd", "dsf", "dff", "aiff", "aif", "aifc"
    )
    
    // Image file extensions
    val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg",
        "tiff", "tif", "ico", "avif", "jxl"
    )
    
    // Document file extensions
    val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf",
        "odt", "ods", "odp", "csv", "log", "md", "epub", "mobi"
    )
    
    // Archive file extensions
    val ARCHIVE_EXTENSIONS = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "dmg", "apk", "jar"
    )
    
    // Code file extensions
    val CODE_EXTENSIONS = setOf(
        "java", "kt", "kts", "xml", "json", "html", "css", "js", "ts", "py",
        "c", "cpp", "h", "hpp", "swift", "go", "rs", "sh", "bat", "gradle"
    )
    
    /**
     * Checks if a file is a video based on extension
     */
    fun isVideoFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.getDefault())
        return VIDEO_EXTENSIONS.contains(extension)
    }
    
    /**
     * Checks if a file is audio based on extension
     */
    fun isAudioFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.getDefault())
        return AUDIO_EXTENSIONS.contains(extension)
    }
    
    /**
     * Checks if a file is an image based on extension
     */
    fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.getDefault())
        return IMAGE_EXTENSIONS.contains(extension)
    }
    
    /**
     * Checks if a file is a document based on extension
     */
    fun isDocumentFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.getDefault())
        return DOCUMENT_EXTENSIONS.contains(extension)
    }
    
    /**
     * Checks if a file is an archive based on extension
     */
    fun isArchiveFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.getDefault())
        return ARCHIVE_EXTENSIONS.contains(extension)
    }
    
    /**
     * Checks if a file is a code file based on extension
     */
    fun isCodeFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.getDefault())
        return CODE_EXTENSIONS.contains(extension)
    }
    
    /**
     * Gets the file type category
     */
    fun getFileType(file: File): FileType {
        return when {
            isVideoFile(file) -> FileType.VIDEO
            isAudioFile(file) -> FileType.AUDIO
            isImageFile(file) -> FileType.IMAGE
            isDocumentFile(file) -> FileType.DOCUMENT
            isArchiveFile(file) -> FileType.ARCHIVE
            isCodeFile(file) -> FileType.CODE
            else -> FileType.OTHER
        }
    }
    
    /**
     * Gets MIME type from file extension
     */
    fun getMimeTypeFromExtension(extension: String): String =
        when (extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "m4v" -> "video/x-m4v"
            "3gp" -> "video/3gpp"
            "mpg", "mpeg" -> "video/mpeg"
            else -> "video/*"
        }
}
