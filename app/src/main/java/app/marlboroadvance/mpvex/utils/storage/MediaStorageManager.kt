package app.marlboroadvance.mpvex.utils.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * PRODUCTION-READY Unified Media Storage Manager
 * 
 * Single source of truth for ALL media scanning and storage operations.
 * Consolidates: UnifiedMediaScanner, StorageScanUtils, FolderScanUtils, MediaStoreScanner
 * 
 * Features:
 * - MediaStore scanning for speed (10-100x faster)
 * - Filesystem fallback for external devices (USB OTG, SD cards)
 * - Global cache system with 5-minute validity
 * - Folder list mode and tree mode support
 * - Video listing with full metadata
 * - Storage volume detection and management
 */
object MediaStorageManager {
    private const val TAG = "MediaStorageManager"
    
    // Global cache for all folders (built once, reused everywhere)
    private var globalFolderCache: Map<String, FolderData>? = null
    private var globalCacheTimestamp: Long = 0
    private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L // 5 minutes
    
    // ============================================================================
    // DATA CLASSES
    // ============================================================================
    
    /**
     * Folder metadata
     */
    data class FolderData(
        val path: String,
        val name: String,
        val videoCount: Int,
        val totalSize: Long,
        val totalDuration: Long,
        val lastModified: Long,
        val hasSubfolders: Boolean = false
    )
    
    /**
     * Video metadata extracted from files
     */
    data class VideoMetadata(
        val duration: Long,
        val mimeType: String,
        val width: Int = 0,
        val height: Int = 0,
    )
    
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
    
    // ============================================================================
    // FILE EXTENSIONS
    // ============================================================================
    
    // Video file extensions to scan for
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
    
    // Folders to skip during scanning (system/cache folders)
    private val SKIP_FOLDERS = setOf(
        // System & OS Junk
        "android", "data", "obb", "system", "lost.dir", ".android_secure", "android_secure",
        
        // Hidden & Temp Files
        ".thumbnails", "thumbnails", "thumbs", ".thumbs",
        ".cache", "cache", "temp", "tmp", ".temp", ".tmp",
        
        // Trash & Recycle Bins
        ".trash", "trash", ".trashbin", ".trashed", "recycle", "recycler",
        
        // App Clutters
        "log", "logs", "backup", "backups",
        "stickers", "whatsapp stickers", "telegram stickers"
    )
    
    // ============================================================================
    // CACHE MANAGEMENT
    // ============================================================================
    
    /**
     * Clear all caches (call when media library changes)
     */
    fun clearCache() {
        globalFolderCache = null
        globalCacheTimestamp = 0
        Log.d(TAG, "Cache cleared")
    }
    
    // ============================================================================
    // FOLDER LIST MODE - Get all video folders
    // ============================================================================
    
    /**
     * Get all video folders for folder list view
     * Uses MediaStore + filesystem fallback
     */
    suspend fun getAllVideoFolders(context: Context): List<VideoFolder> = withContext(Dispatchers.IO) {
        val cache = getOrBuildGlobalCache(context)
        
        // Convert to VideoFolder list
        cache.values.map { data ->
            VideoFolder(
                bucketId = data.path,
                name = data.name,
                path = data.path,
                videoCount = data.videoCount,
                totalSize = data.totalSize,
                totalDuration = data.totalDuration,
                lastModified = data.lastModified
            )
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }
    
    // ============================================================================
    // TREE MODE - Get folders in specific directory
    // ============================================================================
    
    /**
     * Get direct child folders of a parent directory for tree view
     * Uses cached data for instant results
     */
    suspend fun getFoldersInDirectory(
        context: Context,
        parentPath: String
    ): List<FolderData> = withContext(Dispatchers.IO) {
        val cache = getOrBuildGlobalCache(context)
        
        // Filter for direct children only
        cache.values.filter { folder ->
            val parent = File(folder.path).parent
            parent == parentPath
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }
    
    // ============================================================================
    // VIDEO LISTING - Get videos in a folder
    // ============================================================================
    
    /**
     * Get all videos in a specific folder
     * MediaStore first, filesystem fallback for external devices
     */
    suspend fun getVideosInFolder(
        context: Context,
        folderPath: String
    ): List<Video> = withContext(Dispatchers.IO) {
        val videosMap = mutableMapOf<String, Video>()
        
        // Try MediaStore first (fast)
        scanVideosFromMediaStore(context, folderPath, videosMap)
        
        // Fallback to filesystem if MediaStore returned nothing
        val folder = File(folderPath)
        if (folder.exists() && folder.canRead() && videosMap.isEmpty()) {
            Log.d(TAG, "MediaStore empty for $folderPath, scanning filesystem")
            scanVideosFromFileSystem(context, folder, videosMap)
        }
        
        videosMap.values.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
    }
    
    // ============================================================================
    // CORE CACHE BUILDING
    // ============================================================================
    
    /**
     * Get or build the global folder cache
     * This is the heart of the scanner - builds once, reuses everywhere
     */
    private suspend fun getOrBuildGlobalCache(
        context: Context,
        forceRebuild: Boolean = false
    ): Map<String, FolderData> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        
        // Return cached if valid
        if (!forceRebuild && globalFolderCache != null && 
            (now - globalCacheTimestamp) < CACHE_VALIDITY_MS) {
            Log.d(TAG, "Using cached data (${globalFolderCache!!.size} folders)")
            return@withContext globalFolderCache!!
        }
        
        Log.d(TAG, "Building global cache...")
        val startTime = System.currentTimeMillis()
        
        val allFolders = mutableMapOf<String, FolderData>()
        
        // Step 1: Scan MediaStore (fast, covers most cases)
        scanMediaStore(context, allFolders)
        
        // Step 2: Scan external volumes via filesystem (USB OTG, SD cards)
        scanExternalVolumes(context, allFolders)
        
        // Note: We don't build parent hierarchy anymore - only show folders with immediate video children
        
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Cache built: ${allFolders.size} folders in ${elapsed}ms")
        
        // Update cache
        globalFolderCache = allFolders
        globalCacheTimestamp = now
        
        allFolders
    }
    
    /**
     * Scan MediaStore for all videos and build folder map
     */
    private fun scanMediaStore(
        context: Context,
        folders: MutableMap<String, FolderData>
    ) {
        val projection = arrayOf(
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                
                // Collect videos by folder
                val videosByFolder = mutableMapOf<String, MutableList<VideoInfo>>()
                
                while (cursor.moveToNext()) {
                    val videoPath = cursor.getString(dataColumn)
                    val file = File(videoPath)
                    
                    if (!file.exists()) continue
                    
                    val folderPath = file.parent ?: continue
                    val size = cursor.getLong(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateModified = cursor.getLong(dateColumn)
                    
                    videosByFolder.getOrPut(folderPath) { mutableListOf() }.add(
                        VideoInfo(size, duration, dateModified)
                    )
                }
                
                // Build folder data - only count immediate children videos
                for ((folderPath, videos) in videosByFolder) {
                    // Only count videos directly in this folder (not subdirectories)
                    var totalSize = 0L
                    var totalDuration = 0L
                    var lastModified = 0L
                    
                    for (video in videos) {
                        totalSize += video.size
                        totalDuration += video.duration
                        if (video.dateModified > lastModified) {
                            lastModified = video.dateModified
                        }
                    }
                    
                    // Check if this folder has subfolders with videos
                    val hasSubfolders = videosByFolder.keys.any { otherPath ->
                        otherPath != folderPath && 
                        otherPath.startsWith("$folderPath${File.separator}") &&
                        File(otherPath).parent == folderPath
                    }
                    
                    folders[folderPath] = FolderData(
                        path = folderPath,
                        name = File(folderPath).name,
                        videoCount = videos.size,
                        totalSize = totalSize,
                        totalDuration = totalDuration,
                        lastModified = lastModified,
                        hasSubfolders = hasSubfolders
                    )
                }
            }
            
            Log.d(TAG, "MediaStore scan: ${folders.size} folders")
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore scan error", e)
        }
    }
    
    /**
     * Scan external volumes (USB OTG, SD cards) via filesystem
     */
    private fun scanExternalVolumes(
        context: Context,
        folders: MutableMap<String, FolderData>
    ) {
        try {
            val externalVolumes = getExternalStorageVolumes(context)
            
            if (externalVolumes.isEmpty()) {
                Log.d(TAG, "No external volumes found")
                return
            }
            
            Log.d(TAG, "Scanning ${externalVolumes.size} external volumes")
            
            for (volume in externalVolumes) {
                val volumePath = getVolumePath(volume)
                if (volumePath == null) {
                    Log.w(TAG, "Could not get path for volume")
                    continue
                }
                
                val volumeDir = File(volumePath)
                if (!volumeDir.exists() || !volumeDir.canRead()) {
                    Log.w(TAG, "Cannot access volume: $volumePath")
                    continue
                }
                
                scanDirectoryRecursive(volumeDir, folders, maxDepth = 20)
            }
            
            Log.d(TAG, "External scan complete: ${folders.size} total folders")
        } catch (e: Exception) {
            Log.e(TAG, "External volume scan error", e)
        }
    }
    
    /**
     * Recursively scan directory for videos
     */
    private fun scanDirectoryRecursive(
        directory: File,
        folders: MutableMap<String, FolderData>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth >= maxDepth) return
        if (!directory.exists() || !directory.canRead() || !directory.isDirectory) return
        
        try {
            val files = directory.listFiles() ?: return
            
            val videoFiles = mutableListOf<File>()
            val subdirectories = mutableListOf<File>()
            
            for (file in files) {
                try {
                    when {
                        file.isDirectory -> {
                            if (!shouldSkipFolder(file)) {
                                subdirectories.add(file)
                            }
                        }
                        file.isFile -> {
                            val extension = file.extension.lowercase(Locale.getDefault())
                            if (VIDEO_EXTENSIONS.contains(extension)) {
                                videoFiles.add(file)
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    continue
                }
            }
            
            // Add folder if it has videos
            if (videoFiles.isNotEmpty()) {
                val folderPath = directory.absolutePath
                
                // Skip if already from MediaStore
                if (!folders.containsKey(folderPath)) {
                    var totalSize = 0L
                    var lastModified = 0L
                    
                    for (video in videoFiles) {
                        totalSize += video.length()
                        val modified = video.lastModified()
                        if (modified > lastModified) {
                            lastModified = modified
                        }
                    }
                    
                    folders[folderPath] = FolderData(
                        path = folderPath,
                        name = directory.name,
                        videoCount = videoFiles.size,
                        totalSize = totalSize,
                        totalDuration = 0L, // Duration not available from filesystem
                        lastModified = lastModified / 1000,
                        hasSubfolders = subdirectories.isNotEmpty()
                    )
                }
            }
            
            // Recurse into subdirectories
            for (subdir in subdirectories) {
                scanDirectoryRecursive(subdir, folders, maxDepth, currentDepth + 1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning: ${directory.absolutePath}", e)
        }
    }
    
    /**
     * Build parent folder hierarchy
     * Ensures intermediate folders (without direct videos) are included
     */
    private fun buildParentHierarchy(folders: MutableMap<String, FolderData>) {
        val allPaths = folders.keys.toSet()
        val parentsToAdd = mutableSetOf<String>()
        
        // Find all parent folders
        for (folderPath in allPaths) {
            var currentPath = File(folderPath).parent
            while (currentPath != null) {
                if (currentPath == "/" || currentPath.length <= 1) break
                if (folders.containsKey(currentPath)) break
                
                parentsToAdd.add(currentPath)
                currentPath = File(currentPath).parent
            }
        }
        
        // Add parent folders with aggregated data
        for (parentPath in parentsToAdd) {
            var totalCount = 0
            var totalSize = 0L
            var totalDuration = 0L
            var lastModified = 0L
            var hasSubfolders = false
            
            // Aggregate from direct children
            for ((folderPath, folderData) in folders) {
                val parent = File(folderPath).parent
                if (parent == parentPath) {
                    totalCount += folderData.videoCount
                    totalSize += folderData.totalSize
                    totalDuration += folderData.totalDuration
                    if (folderData.lastModified > lastModified) {
                        lastModified = folderData.lastModified
                    }
                    hasSubfolders = true
                }
            }
            
            if (totalCount > 0) {
                folders[parentPath] = FolderData(
                    path = parentPath,
                    name = File(parentPath).name,
                    videoCount = totalCount,
                    totalSize = totalSize,
                    totalDuration = totalDuration,
                    lastModified = lastModified,
                    hasSubfolders = hasSubfolders
                )
            }
        }
    }
    
    // ============================================================================
    // VIDEO SCANNING HELPERS
    // ============================================================================
    
    /**
     * Scan videos from MediaStore
     */
    private fun scanVideosFromMediaStore(
        context: Context,
        folderPath: String,
        videosMap: MutableMap<String, Video>
    ) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        
        val selection = "${MediaStore.Video.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath/%")
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    val file = File(path)
                    
                    // Only direct children
                    if (file.parent != folderPath) continue
                    if (!file.exists()) continue
                    
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn)
                    val title = file.nameWithoutExtension
                    val size = cursor.getLong(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val mimeType = cursor.getString(mimeTypeColumn) ?: "video/*"
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    
                    val uri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    
                    videosMap[path] = Video(
                        id = id,
                        title = title,
                        displayName = displayName,
                        path = path,
                        uri = uri,
                        duration = duration,
                        durationFormatted = formatDuration(duration),
                        size = size,
                        sizeFormatted = formatFileSize(size),
                        dateModified = dateModified,
                        dateAdded = dateAdded,
                        mimeType = mimeType,
                        bucketId = folderPath,
                        bucketDisplayName = File(folderPath).name,
                        width = width,
                        height = height,
                        fps = 0f,
                        resolution = formatResolution(width, height),
                        hasEmbeddedSubtitles = false,
                        subtitleCodec = ""
                    )
                }
            }
            
            Log.d(TAG, "MediaStore: ${videosMap.size} videos in $folderPath")
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore video scan error", e)
        }
    }
    
    /**
     * Scan videos from filesystem (fallback)
     */
    private fun scanVideosFromFileSystem(
        context: Context,
        folder: File,
        videosMap: MutableMap<String, Video>
    ) {
        try {
            val files = folder.listFiles() ?: return
            
            for (file in files) {
                try {
                    if (!file.isFile) continue
                    
                    val extension = file.extension.lowercase(Locale.getDefault())
                    if (!VIDEO_EXTENSIONS.contains(extension)) continue
                    
                    val path = file.absolutePath
                    if (videosMap.containsKey(path)) continue
                    
                    val uri = Uri.fromFile(file)
                    val displayName = file.name
                    val title = file.nameWithoutExtension
                    val size = file.length()
                    val dateModified = file.lastModified() / 1000
                    
                    // Extract metadata
                    val metadata = extractVideoMetadata(context, file)
                    
                    videosMap[path] = Video(
                        id = path.hashCode().toLong(),
                        title = title,
                        displayName = displayName,
                        path = path,
                        uri = uri,
                        duration = metadata.duration,
                        durationFormatted = formatDuration(metadata.duration),
                        size = size,
                        sizeFormatted = formatFileSize(size),
                        dateModified = dateModified,
                        dateAdded = dateModified,
                        mimeType = metadata.mimeType,
                        bucketId = folder.absolutePath,
                        bucketDisplayName = folder.name,
                        width = metadata.width,
                        height = metadata.height,
                        fps = 0f,
                        resolution = formatResolution(metadata.width, metadata.height),
                        hasEmbeddedSubtitles = false,
                        subtitleCodec = ""
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing file: ${file.absolutePath}", e)
                    continue
                }
            }
            
            Log.d(TAG, "Filesystem: ${videosMap.size} videos in ${folder.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Filesystem video scan error", e)
        }
    }
    
    // ============================================================================
    // STORAGE VOLUME MANAGEMENT
    // ============================================================================
    
    /**
     * Gets all mounted storage volumes
     * Note: Uses a lenient check as volume.state can sometimes be unreliable
     */
    fun getAllStorageVolumes(context: Context): List<StorageVolume> =
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            // Filter volumes more leniently - include volumes even if state is not MEDIA_MOUNTED
            // as the state can be unreliable, especially for SD cards
            storageManager.storageVolumes.filter { volume ->
                // Consider a volume available if:
                // 1. It's reported as mounted, OR
                // 2. We can get a valid path for it and it exists
                volume.state == Environment.MEDIA_MOUNTED ||
                    (getVolumePath(volume)?.let { path -> File(path).exists() } == true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage volumes", e)
            emptyList()
        }
    
    /**
     * Gets non-primary (external) storage volumes (SD cards, USB OTG)
     */
    fun getExternalStorageVolumes(context: Context): List<StorageVolume> =
        getAllStorageVolumes(context).filter { !it.isPrimary }
    
    /**
     * Determines which storage volume a given path belongs to
     */
    fun getVolumeForPath(
        context: Context,
        path: String,
    ): StorageVolume? {
        try {
            val volumes = getAllStorageVolumes(context)
            
            for (volume in volumes) {
                val volumePath = getVolumePath(volume)
                if (volumePath != null && path.startsWith(volumePath)) {
                    return volume
                }
            }
            
            return volumes.firstOrNull { it.isPrimary }
        } catch (e: Exception) {
            Log.w(TAG, "Error determining volume for path: $path", e)
            return null
        }
    }
    
    /**
     * Gets the physical path of a storage volume
     */
    fun getVolumePath(volume: StorageVolume): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val directory = volume.directory
                if (directory != null) {
                    return directory.absolutePath
                }
            }
            
            val method = volume.javaClass.getMethod("getPath")
            val path = method.invoke(volume) as? String
            if (path != null) {
                return path
            }
            
            volume.uuid?.let { uuid ->
                val possiblePaths = listOf(
                    "/storage/$uuid",
                    "/mnt/media_rw/$uuid",
                )
                for (possiblePath in possiblePaths) {
                    if (File(possiblePath).exists()) {
                        return possiblePath
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Could not get volume path", e)
            return null
        }
    }
    
    // ============================================================================
    // FILE TYPE DETECTION
    // ============================================================================
    
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
    
    // ============================================================================
    // FOLDER/FILE FILTERING
    // ============================================================================
    
    /**
     * Checks if a folder contains a .nomedia file
     */
    fun hasNoMediaFile(folder: File): Boolean {
        if (!folder.isDirectory || !folder.canRead()) {
            return false
        }
        
        return try {
            val noMediaFile = File(folder, ".nomedia")
            noMediaFile.exists()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking for .nomedia file in: ${folder.absolutePath}", e)
            false
        }
    }
    
    /**
     * Checks if a folder should be skipped during scanning
     * Skips .nomedia folders, hidden folders, and system folders
     */
    fun shouldSkipFolder(folder: File): Boolean {
        // Check for .nomedia, hidden folders, and system folders
        if (hasNoMediaFile(folder)) {
            return true
        }
        
        val name = folder.name.lowercase()
        val isHidden = name.startsWith(".")
        return isHidden || SKIP_FOLDERS.contains(name)
    }
    
    /**
     * Checks if a file should be skipped during file listing
     * Skips hidden files (starting with dot)
     */
    fun shouldSkipFile(file: File): Boolean {
        // Skip files that start with a dot (hidden files)
        return file.name.startsWith(".")
    }
    
    // ============================================================================
    // METADATA EXTRACTION
    // ============================================================================
    
    /**
     * Extracts video metadata using MediaInfo library
     * @return VideoMetadata object with duration, mime type, width, and height
     */
    fun extractVideoMetadata(
        context: Context,
        file: File,
    ): VideoMetadata {
        var duration = 0L
        var mimeType = "video/*"
        var width = 0
        var height = 0
        
        try {
            // Use MediaInfo library for better accuracy and performance
            val uri = Uri.fromFile(file)
            val result = runBlocking {
                MediaInfoOps.extractBasicMetadata(context, uri, file.name)
            }
            
            result.onSuccess { metadata ->
                duration = metadata.durationMs
                width = metadata.width
                height = metadata.height
                // Get mime type from extension since MediaInfo doesn't return it directly
                mimeType = getMimeTypeFromExtension(file.extension.lowercase())
            }.onFailure { e ->
                Log.w(TAG, "Could not extract metadata for ${file.absolutePath}, using fallback", e)
                // Fallback to mime type based on extension
                mimeType = getMimeTypeFromExtension(file.extension.lowercase())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract metadata for ${file.absolutePath}, using fallback", e)
            // Fallback to mime type based on extension
            mimeType = getMimeTypeFromExtension(file.extension.lowercase())
        }
        
        return VideoMetadata(duration, mimeType, width, height)
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
    
    // ============================================================================
    // FORMATTING UTILITIES
    // ============================================================================
    
    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0s"
        
        val seconds = durationMs / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
            minutes > 0 -> String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
            else -> "${secs}s"
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            bytes / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
    }
    
    private fun formatResolution(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "--"
        
        return when {
            width >= 7680 || height >= 4320 -> "4320p"
            width >= 3840 || height >= 2160 -> "2160p"
            width >= 2560 || height >= 1440 -> "1440p"
            width >= 1920 || height >= 1080 -> "1080p"
            width >= 1280 || height >= 720 -> "720p"
            width >= 854 || height >= 480 -> "480p"
            width >= 640 || height >= 360 -> "360p"
            width >= 426 || height >= 240 -> "240p"
            else -> "${height}p"
        }
    }
    
    // ============================================================================
    // HELPER DATA CLASSES
    // ============================================================================
    
    /**
     * Helper data class for video info during scanning
     */
    private data class VideoInfo(
        val size: Long,
        val duration: Long,
        val dateModified: Long
    )
}
