package app.gyrolet.mpvrx.repository

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import app.gyrolet.mpvrx.domain.anicli.AniCliSubtitleTrack
import app.gyrolet.mpvrx.domain.anicli.AnimeDownloadInfo
import app.gyrolet.mpvrx.domain.anicli.AnimeDownloadQualityMode
import app.gyrolet.mpvrx.domain.anicli.AnimeDownloadRequest
import app.gyrolet.mpvrx.domain.anicli.AnimeSource
import app.gyrolet.mpvrx.domain.anicli.DownloadState
import app.gyrolet.mpvrx.domain.anicli.isEnglishSubtitle
import app.gyrolet.mpvrx.domain.anicli.provider.EpisodeStreamsParams
import app.gyrolet.mpvrx.domain.anicli.provider.SourceRegistry
import app.gyrolet.mpvrx.domain.anicli.provider.Subtitle
import app.gyrolet.mpvrx.preferences.FoldersPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.net.URL

private const val TAG = "AnimeDownloadRepository"
private const val DEFAULT_REFERER = "https://google.com"
private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
private const val ANIMEPAHE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Build/AP3A.240905.009)"
private const val PERSISTED_DOWNLOADS_FALLBACK = "[]"
private const val DOWNLOAD_PROGRESS_PERSIST_INTERVAL_MS = 1500L
private const val DOWNLOAD_NOTIFICATION_SYNC_INTERVAL_MS = 750L

class AnimeDownloadRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val foldersPreferences: FoldersPreferences,
    private val sourceRegistry: SourceRegistry,
    private val json: Json,
) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notifier = AnimeDownloadNotifier(context)
    private val stateLock = Any()
    private val records = linkedMapOf<String, PersistedAnimeDownload>()
    private val jobs = mutableMapOf<String, Job>()
    private var lastPersistedAtMs = 0L
    private var lastNotifierSyncAtMs = 0L

    private val _downloads = MutableStateFlow<Map<String, AnimeDownloadInfo>>(emptyMap())
    val downloads: StateFlow<Map<String, AnimeDownloadInfo>> = _downloads.asStateFlow()

    init {
        restoreDownloads()
    }

    fun queueDownload(request: AnimeDownloadRequest) {
        val key = downloadKey(request.animeName, request.episodeNumber)
        val existing = snapshotRecord(key)
        if (existing?.state == PersistedDownloadState.COMPLETED) return
        val sourceChanged = existing?.source?.let { it != request.source } == true

        val animeFolderUri = foldersPreferences.animeFolder.get().ifBlank {
            existing?.animeFolderUri.orEmpty()
        }.ifBlank {
            throw IllegalStateException("Anime download folder is not configured")
        }
        val requestedSubtitle = request.subtitleTracks.toAniCliPreferredSubtitle()
        val subtitleChanged = requestedSubtitle?.url != null &&
            requestedSubtitle.url != existing?.preferredSubtitleUrl
        val mergedRequestHeaders = buildMap {
            putAll(existing?.requestHeaders.orEmpty())
            putAll(request.requestHeaders.filterValues { it.isNotBlank() })
        }.takeUnless { sourceChanged } ?: request.requestHeaders.filterValues { it.isNotBlank() }

        val merged = (existing ?: PersistedAnimeDownload(
            key = key,
            animeId = request.animeId,
            animeName = request.animeName,
            episodeId = request.episodeId,
            epNo = request.episodeNumber,
            episodeTitle = request.episodeTitle,
            preferredSubtitleUrl = requestedSubtitle?.url,
            preferredSubtitleLabel = requestedSubtitle?.label,
            source = request.source,
            translationType = request.translationType,
            quality = request.qualityLabel,
            qualityMode = request.qualityMode,
            animeFolderUri = animeFolderUri,
        )).copy(
            animeId = request.animeId,
            animeName = request.animeName,
            episodeId = request.episodeId,
            epNo = request.episodeNumber,
            episodeTitle = request.episodeTitle ?: existing?.episodeTitle,
            source = request.source,
            translationType = request.translationType,
            quality = request.qualityLabel.ifBlank { existing?.quality ?: "Auto" },
            qualityMode = request.qualityMode,
            directUrl = request.directUrl ?: existing?.directUrl?.takeUnless { sourceChanged },
            referer = request.referer
                ?: request.requestHeaders["Referer"]
                ?: existing?.referer?.takeUnless { sourceChanged },
            userAgent = request.userAgent
                ?: request.requestHeaders["User-Agent"]
                ?: existing?.userAgent?.takeUnless { sourceChanged }
                ?: defaultUserAgentFor(request.source),
            requestHeaders = mergedRequestHeaders,
            preferredSubtitleUrl = requestedSubtitle?.url ?: existing?.preferredSubtitleUrl,
            preferredSubtitleLabel = requestedSubtitle?.label ?: existing?.preferredSubtitleLabel,
            subtitleFileUri = if (subtitleChanged) null else existing?.subtitleFileUri,
            subtitleFileName = if (subtitleChanged) null else existing?.subtitleFileName,
            subtitleMimeType = if (subtitleChanged) null else existing?.subtitleMimeType,
            animeFolderUri = animeFolderUri,
            state = PersistedDownloadState.PREPARING,
            transferRateBytesPerSecond = null,
            error = null,
            updatedAt = System.currentTimeMillis(),
        )

        upsertRecord(merged)
        startDownload(key)
    }

    fun pauseDownload(animeName: String, epNo: String) {
        pauseDownload(downloadKey(animeName, epNo))
    }

    fun resumeDownload(animeName: String, epNo: String) {
        resumeDownload(downloadKey(animeName, epNo))
    }

    fun removeDownload(animeName: String, epNo: String) {
        removeDownload(downloadKey(animeName, epNo))
    }

    fun pauseAllDownloads() {
        synchronized(stateLock) {
            jobs.keys.toList()
        }.forEach(::pauseDownload)
    }

    fun resumeAllDownloads() {
        synchronized(stateLock) {
            records.values
                .filter { it.state == PersistedDownloadState.PAUSED || it.state == PersistedDownloadState.FAILED }
                .map { it.key }
        }.forEach(::resumeDownload)
    }

    fun hasActiveDownloads(): Boolean =
        synchronized(stateLock) {
            records.values.any {
                it.state == PersistedDownloadState.PREPARING || it.state == PersistedDownloadState.IN_PROGRESS
            }
        }

    fun hasPausedDownloads(): Boolean =
        synchronized(stateLock) {
            records.values.any { it.state == PersistedDownloadState.PAUSED }
        }

    private fun pauseDownload(key: String) {
        var currentState: PersistedDownloadState? = null
        var jobToCancel: Job? = null
        var publishedPause = false

        synchronized(stateLock) {
            val current = records[key]
            currentState = current?.state
            jobToCancel = jobs.remove(key)
            if (current != null &&
                (current.state == PersistedDownloadState.IN_PROGRESS ||
                    current.state == PersistedDownloadState.PREPARING)
            ) {
                records[key] = current.copy(
                    state = PersistedDownloadState.PAUSED,
                    transferRateBytesPerSecond = null,
                    updatedAt = System.currentTimeMillis(),
                )
                publishLocked()
                publishedPause = true
            }
        }

        Log.d(
            TAG,
            "pauseDownload($key) requested state=$currentState activeJob=${jobToCancel != null} publishedPause=$publishedPause",
        )
        if (jobToCancel == null && !publishedPause) {
            Log.d(TAG, "pauseDownload($key) ignored because there was no active job to cancel")
            return
        }
        jobToCancel?.cancel(PauseDownloadException())
    }

    private fun resumeDownload(key: String) {
        var previousState: PersistedDownloadState? = null
        var shouldStart = false
        var skipReason: String? = null

        synchronized(stateLock) {
            val current = records[key]
            previousState = current?.state
            when {
                current == null -> skipReason = "record not found"
                current.state == PersistedDownloadState.COMPLETED -> skipReason = "download already completed"
                jobs[key]?.isActive == true -> skipReason = "download job already active"
                else -> {
                    records[key] = current.copy(
                        state = PersistedDownloadState.PREPARING,
                        transferRateBytesPerSecond = null,
                        error = null,
                        updatedAt = System.currentTimeMillis(),
                    )
                    publishLocked()
                    shouldStart = true
                }
            }
        }

        if (!shouldStart) {
            Log.d(TAG, "resumeDownload($key) ignored because ${skipReason ?: "state=$previousState"}")
            return
        }
        Log.d(TAG, "resumeDownload($key) scheduled from state=$previousState")
        startDownload(key)
    }

    private fun removeDownload(key: String) {
        var removedRecord: PersistedAnimeDownload? = null
        var jobToCancel: Job? = null

        synchronized(stateLock) {
            removedRecord = records.remove(key)
            jobToCancel = jobs.remove(key)
            if (removedRecord != null) {
                publishLocked()
            }
        }

        val existing = removedRecord ?: run {
            Log.d(TAG, "removeDownload($key) ignored because no record exists")
            return
        }
        Log.d(
            TAG,
            "removeDownload($key) requested state=${existing.state} activeJob=${jobToCancel != null}",
        )
        repositoryScope.launch {
            jobToCancel?.cancelAndJoin()
            if (existing.state != PersistedDownloadState.COMPLETED) {
                deletePartialFile(existing.fileUri)
                deletePartialFile(existing.subtitleFileUri)
            }
            Log.d(TAG, "removeDownload($key) finished for state=${existing.state}")
        }
    }

    private fun startDownload(key: String) {
        val job = repositoryScope.launch(start = CoroutineStart.LAZY) {
            try {
                downloadInternal(key)
            } catch (pause: PauseDownloadException) {
                updateRecord(key) {
                    it.copy(
                        state = PersistedDownloadState.PAUSED,
                        transferRateBytesPerSecond = null,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            } catch (cancelled: CancellationException) {
                if (snapshotRecord(key) != null) {
                    updateRecord(key) {
                        it.copy(
                            state = PersistedDownloadState.PAUSED,
                            transferRateBytesPerSecond = null,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Download failed for $key", error)
                updateRecord(key) {
                    it.copy(
                        state = PersistedDownloadState.FAILED,
                        transferRateBytesPerSecond = null,
                        error = error.message ?: "Download failed",
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            } finally {
                synchronized(stateLock) {
                    jobs.remove(key)
                }
            }
        }

        synchronized(stateLock) {
            if (jobs[key]?.isActive == true) return
            jobs[key] = job
        }
        job.start()
    }

    private suspend fun downloadInternal(key: String) {
        var record = snapshotRecord(key) ?: return
        var resolved = false

        repeat(2) { attempt ->
            try {
                if (!resolved || record.directUrl.isNullOrBlank()) {
                    record = resolveStream(record, forceRefresh = attempt > 0)
                    resolved = true
                }
                record = ensureTargetFile(record)
                record = syncPreferredSubtitle(record)
                record = transferToFile(record)
                upsertRecord(
                    record.copy(
                        state = PersistedDownloadState.COMPLETED,
                        error = null,
                        bytesDownloaded = record.totalBytes ?: record.bytesDownloaded,
                        totalBytes = record.totalBytes ?: record.bytesDownloaded,
                        transferRateBytesPerSecond = null,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                return
            } catch (pause: PauseDownloadException) {
                throw pause
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (attempt == 0) {
                    // For sources with no stream resolver (streaming embeds, torrent),
                    // preserve the directUrl so the retry can still attempt the download.
                    val canReResolve = sourceRegistry.getOrNull(record.source) != null
                    record = updateRecordAndGet(key) {
                        it.copy(
                            directUrl = if (canReResolve) null else it.directUrl,
                            referer = if (canReResolve) null else it.referer,
                            state = PersistedDownloadState.PREPARING,
                            transferRateBytesPerSecond = null,
                            error = null,
                            updatedAt = System.currentTimeMillis(),
                        )
                    } ?: throw error
                    resolved = false
                } else {
                    throw error
                }
            }
        }
    }

    private suspend fun resolveStream(
        record: PersistedAnimeDownload,
        forceRefresh: Boolean,
    ): PersistedAnimeDownload {
        // Sources with no BaseAnimeProvider (streaming embeds, torrent sources) cannot re-resolve
        // their stream URLs — if directUrl is already set, skip resolution regardless of forceRefresh.
        val hasStreamResolver = sourceRegistry.getOrNull(record.source) != null
        if (
            !record.directUrl.isNullOrBlank() &&
            (!forceRefresh || !hasStreamResolver)
        ) {
            return record
        }

        updateRecord(record.key) {
            it.copy(
                state = PersistedDownloadState.PREPARING,
                transferRateBytesPerSecond = null,
                error = null,
                updatedAt = System.currentTimeMillis(),
            )
        }

        val params = EpisodeStreamsParams(
            query = record.animeName,
            animeId = record.animeId,
            episode = record.epNo,
            episodeId = record.episodeId,
            translationType = record.translationType,
        )

        val servers = sourceRegistry.getOrNull(record.source)
            ?.episodeStreams(params)
            .orEmpty()

        val candidates = servers.flatMap { server ->
            val preferredSubtitle = server.subtitles.toProviderPreferredSubtitle()
            server.links.map { link ->
                ResolvedEpisodeLink(
                    url = link.link,
                    quality = link.quality,
                    isM3u8 = link.isHls == true,
                    isEmbed = link.format.equals("embed", ignoreCase = true),
                    referer = link.referer ?: server.headers["Referer"],
                    requestHeaders = buildMap {
                        putAll(server.headers.filterValues { it.isNotBlank() })
                        putAll(link.requestHeaders.filterValues { it.isNotBlank() })
                    },
                    translationType = link.translationType,
                    preferredSubtitleUrl = preferredSubtitle?.url,
                    preferredSubtitleLabel = preferredSubtitle?.label,
                )
            }
        }.distinctBy { it.url }
            .filterNot { it.isEmbed }

        val selected = selectDownloadLink(
            candidates = candidates,
            qualityMode = record.qualityMode,
            preferredQuality = record.quality,
            preferredTranslationType = record.translationType,
        )

        val resolvedForTransfer = selected

        val resolvedSubtitleUrl = resolvedForTransfer.preferredSubtitleUrl?.takeIf { it.isNotBlank() }
        val resolvedSubtitleLabel = resolvedForTransfer.preferredSubtitleLabel?.takeIf { it.isNotBlank() }
        return updateRecordAndGet(record.key) {
            val subtitleChanged = resolvedSubtitleUrl != null &&
                resolvedSubtitleUrl != it.preferredSubtitleUrl
            it.copy(
                directUrl = resolvedForTransfer.url,
                referer = resolvedForTransfer.referer ?: defaultRefererFor(record.source),
                userAgent = it.userAgent ?: defaultUserAgentFor(record.source),
                requestHeaders = buildMap {
                    putAll(it.requestHeaders.filterValues { header -> header.isNotBlank() })
                    putAll(resolvedForTransfer.requestHeaders.filterValues { header -> header.isNotBlank() })
                },
                quality = resolvedForTransfer.quality,
                preferredSubtitleUrl = resolvedSubtitleUrl ?: it.preferredSubtitleUrl,
                preferredSubtitleLabel = resolvedSubtitleLabel ?: it.preferredSubtitleLabel,
                subtitleFileUri = if (subtitleChanged) null else it.subtitleFileUri,
                subtitleFileName = if (subtitleChanged) null else it.subtitleFileName,
                subtitleMimeType = if (subtitleChanged) null else it.subtitleMimeType,
                error = null,
                updatedAt = System.currentTimeMillis(),
            )
        } ?: throw IllegalStateException("Unable to persist resolved stream")
    }

    private fun selectDownloadLink(
        candidates: List<ResolvedEpisodeLink>,
        qualityMode: AnimeDownloadQualityMode,
        preferredQuality: String,
        preferredTranslationType: String,
    ): ResolvedEpisodeLink {
        val nonHlsCandidates = candidates.filterNot { it.isM3u8 }
        val pool = if (nonHlsCandidates.isNotEmpty()) nonHlsCandidates else candidates
        require(pool.isNotEmpty()) { "No download streams found" }

        val preferredTranslation = normalizeTranslationType(preferredTranslationType)
        val translationScopedPool = pool.filter {
            normalizeTranslationType(it.translationType) == preferredTranslation
        }
        val effectivePool = if (translationScopedPool.isNotEmpty()) translationScopedPool else pool
        val exactMatch = effectivePool.firstOrNull {
            normalizeQuality(it.quality) == normalizeQuality(preferredQuality)
        }
        return when (qualityMode) {
            AnimeDownloadQualityMode.EXACT -> exactMatch ?: effectivePool.maxByOrNull { qualityValue(it.quality) } ?: effectivePool.first()
            AnimeDownloadQualityMode.LOWEST -> effectivePool.minByOrNull { qualityValue(it.quality) } ?: effectivePool.first()
            AnimeDownloadQualityMode.HIGHEST -> effectivePool.maxByOrNull { qualityValue(it.quality) } ?: effectivePool.first()
        }
    }

    private fun ensureTargetFile(record: PersistedAnimeDownload): PersistedAnimeDownload {
        val existingUri = record.fileUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (existingUri != null) {
            val existingFile = DocumentFile.fromSingleUri(context, existingUri)
            if (existingFile != null && existingFile.exists()) {
                return updateRecordAndGet(record.key) {
                    it.copy(
                        fileName = existingFile.name ?: it.fileName,
                        mimeType = existingFile.type ?: it.mimeType,
                        bytesDownloaded = maxOf(it.bytesDownloaded, existingFile.length()),
                        updatedAt = System.currentTimeMillis(),
                    )
                } ?: record
            }
        }

        val animeDir = findOrCreateAnimeDirectory(record)
        val extension = guessExtension(record.directUrl.orEmpty())
        val fileName = record.fileName ?: buildEpisodeFileName(record, extension)
        val mimeType = record.mimeType ?: guessMimeType(fileName)
        val file = animeDir.findFile(fileName) ?: animeDir.createFile(mimeType, fileName)
            ?: throw IllegalStateException("Cannot create output file")

        return updateRecordAndGet(record.key) {
            it.copy(
                fileUri = file.uri.toString(),
                fileName = file.name ?: fileName,
                mimeType = file.type ?: mimeType,
                bytesDownloaded = maxOf(it.bytesDownloaded, file.length()),
                updatedAt = System.currentTimeMillis(),
            )
        } ?: throw IllegalStateException("Cannot persist output file")
    }

    private suspend fun syncPreferredSubtitle(record: PersistedAnimeDownload): PersistedAnimeDownload {
        val subtitleUrl = record.preferredSubtitleUrl?.takeIf { it.isNotBlank() } ?: return record
        val videoBaseName = record.fileName
            ?.let { fileName -> fileName.substringBeforeLast('.', missingDelimiterValue = fileName) }
            ?.takeIf { it.isNotBlank() }
            ?: buildEpisodeFileName(record, "").ifBlank { return record }
        val subtitleExtension = guessSubtitleExtension(subtitleUrl)
        val subtitleFileName = buildEpisodeSubtitleFileName(videoBaseName, subtitleExtension)
        val subtitleMimeType = guessSubtitleMimeType(subtitleFileName)

        val existingSubtitle = record.subtitleFileUri
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?.let { uri -> DocumentFile.fromSingleUri(context, uri) }
            ?.takeIf { file ->
                file.exists() &&
                    file.name == subtitleFileName &&
                    file.length() > 0L
            }
        if (existingSubtitle != null) {
            return updateRecordAndGet(record.key) {
                it.copy(
                    subtitleFileUri = existingSubtitle.uri.toString(),
                    subtitleFileName = existingSubtitle.name ?: subtitleFileName,
                    subtitleMimeType = existingSubtitle.type ?: subtitleMimeType,
                    updatedAt = System.currentTimeMillis(),
                )
            } ?: record
        }

        val animeDir = findOrCreateAnimeDirectory(record)
        val subtitleFile = animeDir.findFile(subtitleFileName)
            ?: animeDir.createFile(subtitleMimeType, subtitleFileName)
            ?: throw IllegalStateException("Cannot create subtitle file")

        downloadAuxiliaryFile(
            url = subtitleUrl,
            outputUri = subtitleFile.uri,
            headers = replayHeadersFor(record),
        )
        verifySubtitleFile(subtitleFile.uri, subtitleFileName)

        return updateRecordAndGet(record.key) {
            it.copy(
                subtitleFileUri = subtitleFile.uri.toString(),
                subtitleFileName = subtitleFile.name ?: subtitleFileName,
                subtitleMimeType = subtitleFile.type ?: subtitleMimeType,
                updatedAt = System.currentTimeMillis(),
            )
        } ?: record.copy(
            subtitleFileUri = subtitleFile.uri.toString(),
            subtitleFileName = subtitleFile.name ?: subtitleFileName,
            subtitleMimeType = subtitleFile.type ?: subtitleMimeType,
        )
    }

    private suspend fun transferToFile(record: PersistedAnimeDownload): PersistedAnimeDownload {
        if (isHlsDownload(record.directUrl.orEmpty())) {
            return transferHlsToFile(record)
        }

        val outputUri = record.fileUri?.let(Uri::parse)
            ?: throw IllegalStateException("Missing output file")
        val existingFile = DocumentFile.fromSingleUri(context, outputUri)
            ?: throw IllegalStateException("Output file is not accessible")
        var existingBytes = maxOf(record.bytesDownloaded, existingFile.length())
        val headers = replayHeadersFor(record)

        if (record.totalBytes != null && record.totalBytes > 0L && existingBytes >= record.totalBytes) {
            return updateRecordAndGet(record.key) {
                it.copy(
                    bytesDownloaded = record.totalBytes,
                    totalBytes = record.totalBytes,
                    transferRateBytesPerSecond = null,
                    updatedAt = System.currentTimeMillis(),
                )
            } ?: record.copy(
                bytesDownloaded = record.totalBytes,
                totalBytes = record.totalBytes,
                transferRateBytesPerSecond = null,
            )
        }

        val requestBuilder = Request.Builder()
            .url(record.directUrl ?: throw IllegalStateException("Missing stream URL"))

        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 416) {
                val completedBytes = parseCompletedRangeSize(response.header("Content-Range"))
                    ?: record.totalBytes
                    ?: existingBytes.takeIf { it > 0L }
                if (completedBytes != null && existingBytes >= completedBytes) {
                    return updateRecordAndGet(record.key) {
                        it.copy(
                            bytesDownloaded = completedBytes,
                            totalBytes = completedBytes,
                            transferRateBytesPerSecond = null,
                            updatedAt = System.currentTimeMillis(),
                        )
                    } ?: record.copy(
                        bytesDownloaded = completedBytes,
                        totalBytes = completedBytes,
                        transferRateBytesPerSecond = null,
                    )
                }
                throw IllegalStateException("Unexpected HTTP 416")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("Unexpected HTTP ${response.code}")
            }

            val body = response.body
            val append = existingBytes > 0L && response.code == 206
            if (!append) {
                existingBytes = 0L
            }

            val totalBytes = when {
                response.code == 206 -> {
                    val remaining = body.contentLength().takeIf { it > 0L }
                    remaining?.let { existingBytes + it } ?: record.totalBytes
                }

                else -> body.contentLength().takeIf { it > 0L } ?: record.totalBytes
            }

            var downloadedBytes = existingBytes
            var lastPersistedBytes = existingBytes
            var lastPersistAt = System.currentTimeMillis()

            writeToDocument(outputUri, append = append, startPosition = existingBytes) { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (shouldPersistProgress(downloadedBytes, lastPersistedBytes, now - lastPersistAt)) {
                            updateProgress(
                                key = record.key,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                state = PersistedDownloadState.IN_PROGRESS,
                                sampleElapsedMs = now - lastPersistAt,
                            )
                            lastPersistedBytes = downloadedBytes
                            lastPersistAt = now
                        }
                    }
                    output.flush()
                }
            }

            updateProgress(
                key = record.key,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                state = PersistedDownloadState.IN_PROGRESS,
                sampleElapsedMs = System.currentTimeMillis() - lastPersistAt,
            )
            return snapshotRecord(record.key)?.copy(
                bytesDownloaded = downloadedBytes,
                totalBytes = totalBytes ?: downloadedBytes,
            ) ?: record.copy(
                bytesDownloaded = downloadedBytes,
                totalBytes = totalBytes ?: downloadedBytes,
            )
        }
    }

    private fun verifySubtitleFile(uri: Uri, fileName: String) {
        val file = DocumentFile.fromSingleUri(context, uri)
            ?: throw IllegalStateException("Subtitle file is not accessible after download")
        if (!file.exists() || file.length() <= 0L) {
            file.delete()
            throw IllegalStateException("Subtitle download produced an empty file: $fileName")
        }
    }

    private suspend fun transferHlsToFile(record: PersistedAnimeDownload): PersistedAnimeDownload {
        val outputUri = record.fileUri?.let(Uri::parse)
            ?: throw IllegalStateException("Missing output file")
        val existingFile = DocumentFile.fromSingleUri(context, outputUri)
            ?: throw IllegalStateException("Output file is not accessible")
        val existingBytes = maxOf(record.bytesDownloaded, existingFile.length())
        if (record.totalBytes != null && record.totalBytes > 0L && existingBytes >= record.totalBytes) {
            return updateRecordAndGet(record.key) {
                it.copy(
                    bytesDownloaded = record.totalBytes,
                    totalBytes = record.totalBytes,
                    transferRateBytesPerSecond = null,
                    updatedAt = System.currentTimeMillis(),
                )
            } ?: record.copy(
                bytesDownloaded = record.totalBytes,
                totalBytes = record.totalBytes,
                transferRateBytesPerSecond = null,
            )
        }

        var downloadedBytes = existingBytes
        var lastPersistedBytes = existingBytes
        var lastPersistAt = System.currentTimeMillis()

        val headers = replayHeadersFor(record)

        writeToDocument(outputUri, append = existingBytes > 0L, startPosition = existingBytes) { output ->
            downloadedBytes = HlsDownloadSupport.download(
                client = client,
                playlistUrl = record.directUrl ?: throw IllegalStateException("Missing stream URL"),
                headers = headers,
                resumeFromBytes = existingBytes,
                onChunk = { chunk ->
                    output.write(chunk)
                },
                onProgress = { bytes, total ->
                    val now = System.currentTimeMillis()
                    if (shouldPersistProgress(bytes, lastPersistedBytes, now - lastPersistAt)) {
                        updateProgress(
                            key = record.key,
                            downloadedBytes = bytes,
                            totalBytes = total,
                            state = PersistedDownloadState.IN_PROGRESS,
                            sampleElapsedMs = now - lastPersistAt,
                        )
                        lastPersistedBytes = bytes
                        lastPersistAt = now
                    }
                }
            )
            output.flush()
        }

        updateProgress(
            key = record.key,
            downloadedBytes = downloadedBytes,
            totalBytes = downloadedBytes,
            state = PersistedDownloadState.IN_PROGRESS,
            sampleElapsedMs = System.currentTimeMillis() - lastPersistAt,
        )
        return snapshotRecord(record.key)?.copy(
            bytesDownloaded = downloadedBytes,
            totalBytes = downloadedBytes,
        ) ?: record.copy(
            bytesDownloaded = downloadedBytes,
            totalBytes = downloadedBytes,
        )
    }

    private inline fun writeToDocument(
        uri: Uri,
        append: Boolean,
        startPosition: Long,
        block: (FileOutputStream) -> Unit,
    ) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")
            ?: throw IllegalStateException("Cannot open output stream")
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
            if (!append) {
                output.channel.truncate(0L)
                output.channel.position(0L)
            } else {
                output.channel.position(startPosition)
            }
            block(output)
        }
    }

    private fun updateProgress(
        key: String,
        downloadedBytes: Long,
        totalBytes: Long?,
        state: PersistedDownloadState,
        sampleElapsedMs: Long? = null,
    ) {
        updateRecord(key, persistNow = false, notifyNow = false) {
            val timestamp = System.currentTimeMillis()
            val elapsedMs = (sampleElapsedMs ?: (timestamp - it.updatedAt)).coerceAtLeast(1L)
            val bytesDelta = (downloadedBytes - it.bytesDownloaded).coerceAtLeast(0L)
            val measuredSpeed = if (bytesDelta > 0L) {
                ((bytesDelta * 1000L) / elapsedMs).coerceAtLeast(1L)
            } else {
                it.transferRateBytesPerSecond
            }
            val effectiveState = if (
                it.state == PersistedDownloadState.PAUSED &&
                    state == PersistedDownloadState.IN_PROGRESS
            ) {
                PersistedDownloadState.PAUSED
            } else {
                state
            }
            it.copy(
                state = effectiveState,
                bytesDownloaded = downloadedBytes,
                totalBytes = totalBytes,
                transferRateBytesPerSecond = measuredSpeed,
                updatedAt = timestamp,
            )
        }
    }

    private fun restoreDownloads() {
        val restored = runCatching {
            json.decodeFromString<List<PersistedAnimeDownload>>(foldersPreferences.animeDownloads.get())
        }.getOrElse {
            Log.w(TAG, "Failed to restore persisted anime downloads", it)
            emptyList()
        }

        synchronized(stateLock) {
            records.clear()
            restored.forEach { record ->
                val normalizedState = when (record.state) {
                    PersistedDownloadState.PREPARING,
                    PersistedDownloadState.IN_PROGRESS -> PersistedDownloadState.PAUSED
                    else -> record.state
                }
                records[record.key] = record.copy(
                    state = normalizedState,
                    transferRateBytesPerSecond = null,
                )
            }
            publishLocked(persistNow = false)
        }
    }

    private fun upsertRecord(record: PersistedAnimeDownload) {
        synchronized(stateLock) {
            records[record.key] = record
            publishLocked()
        }
    }

    private fun updateRecord(
        key: String,
        persistNow: Boolean = true,
        notifyNow: Boolean = true,
        transform: (PersistedAnimeDownload) -> PersistedAnimeDownload,
    ) {
        synchronized(stateLock) {
            val current = records[key] ?: return
            records[key] = transform(current)
            publishLocked(persistNow = persistNow, notifyNow = notifyNow)
        }
    }

    private fun updateRecordAndGet(
        key: String,
        persistNow: Boolean = true,
        notifyNow: Boolean = true,
        transform: (PersistedAnimeDownload) -> PersistedAnimeDownload,
    ): PersistedAnimeDownload? =
        synchronized(stateLock) {
            val current = records[key] ?: return null
            val updated = transform(current)
            records[key] = updated
            publishLocked(persistNow = persistNow, notifyNow = notifyNow)
            updated
        }


    private fun snapshotRecord(key: String): PersistedAnimeDownload? =
        synchronized(stateLock) { records[key] }

    private fun publishLocked(
        persistNow: Boolean = true,
        notifyNow: Boolean = true,
    ) {
        val published = records
            .values
            .sortedWith(
                compareBy<PersistedAnimeDownload>(
                    { downloadStateRank(it.state) },
                    { it.animeName.lowercase() },
                    { episodeSortKey(it.epNo) ?: Double.MAX_VALUE },
                    { it.epNo }
                )
            )
            .associateBy(
                keySelector = { it.key },
                valueTransform = { it.toUiModel() }
            )
        if (_downloads.value != published) {
            _downloads.value = published
        }

        val now = System.currentTimeMillis()
        val hasActiveTransfers = records.values.any {
            it.state == PersistedDownloadState.PREPARING || it.state == PersistedDownloadState.IN_PROGRESS
        }

        if (persistNow || !hasActiveTransfers || now - lastPersistedAtMs >= DOWNLOAD_PROGRESS_PERSIST_INTERVAL_MS) {
            foldersPreferences.animeDownloads.set(
                runCatching { json.encodeToString(records.values.toList()) }
                    .getOrDefault(PERSISTED_DOWNLOADS_FALLBACK)
            )
            lastPersistedAtMs = now
        }

        if (notifyNow || !hasActiveTransfers || now - lastNotifierSyncAtMs >= DOWNLOAD_NOTIFICATION_SYNC_INTERVAL_MS) {
            notifier.sync(_downloads.value.values.toList())
            lastNotifierSyncAtMs = now
        }
    }

    private fun deletePartialFile(fileUri: String?) {
        val uri = fileUri?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return
        runCatching {
            DocumentFile.fromSingleUri(context, uri)?.delete()
        }.onFailure {
            Log.w(TAG, "Failed to delete partial anime download: $fileUri", it)
        }
    }

    private fun PersistedAnimeDownload.toUiModel(): AnimeDownloadInfo {
        val progress = progressValue(bytesDownloaded, totalBytes)
        val uiState = when (state) {
            PersistedDownloadState.IDLE -> DownloadState.Idle
            PersistedDownloadState.PREPARING -> DownloadState.Preparing
            PersistedDownloadState.IN_PROGRESS -> DownloadState.InProgress(progress)
            PersistedDownloadState.PAUSED -> DownloadState.Paused(progress)
            PersistedDownloadState.COMPLETED -> DownloadState.Completed
            PersistedDownloadState.FAILED -> DownloadState.Failed(error ?: "Download failed")
        }
        return AnimeDownloadInfo(
            key = key,
            animeId = animeId,
            animeName = animeName,
            episodeId = episodeId,
            epNo = epNo,
            episodeTitle = episodeTitle,
            quality = quality,
            state = uiState,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            transferRateBytesPerSecond = transferRateBytesPerSecond,
            fileUri = fileUri,
            subtitleFileUri = subtitleFileUri,
            subtitleLabel = preferredSubtitleLabel,
            updatedAt = updatedAt,
        )
    }

    private fun progressValue(downloadedBytes: Long, totalBytes: Long?): Float {
        if (totalBytes == null || totalBytes <= 0L) return 0f
        return (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }

    private fun parseCompletedRangeSize(contentRangeHeader: String?): Long? =
        contentRangeHeader
            ?.let { Regex("""bytes \*/(\d+)""").find(it) }
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private fun shouldPersistProgress(
        downloadedBytes: Long,
        lastPersistedBytes: Long,
        elapsedSinceLastPersistMs: Long,
    ): Boolean {
        val bytesDelta = downloadedBytes - lastPersistedBytes
        return bytesDelta >= 256 * 1024 || elapsedSinceLastPersistMs >= 400L
    }

    private fun normalizeQuality(quality: String): String =
        quality.filter { it.isDigit() }.ifBlank { quality.trim().lowercase() }

    private fun qualityValue(quality: String): Int =
        quality.filter { it.isDigit() }.toIntOrNull() ?: 0

    private fun normalizeTranslationType(translationType: String): String =
        when (translationType.trim().lowercase()) {
            "dub", "eng", "english" -> "dub"
            else -> "sub"
        }

    private fun downloadStateRank(state: PersistedDownloadState): Int =
        when (state) {
            PersistedDownloadState.PREPARING,
            PersistedDownloadState.IN_PROGRESS -> 0
            PersistedDownloadState.PAUSED,
            PersistedDownloadState.FAILED -> 1
            PersistedDownloadState.COMPLETED -> 2
            PersistedDownloadState.IDLE -> 3
        }

    private fun episodeSortKey(episode: String): Double? =
        Regex("""\d+(?:\.\d+)?""")
            .find(episode)
            ?.value
            ?.toDoubleOrNull()

    private fun findOrCreateAnimeDirectory(record: PersistedAnimeDownload): DocumentFile {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(record.animeFolderUri))
            ?: throw IllegalStateException("Invalid anime folder URI")
        val safeAnimeName = sanitizeFileName(record.animeName)
        return root.findFile(safeAnimeName) ?: root.createDirectory(safeAnimeName)
            ?: throw IllegalStateException("Cannot create anime folder")
    }

    private fun buildEpisodeFileName(record: PersistedAnimeDownload, extension: String): String {
        val safeName = sanitizeFileName(record.animeName)
        val episodeMarker = if (record.epNo.equals("movie", ignoreCase = true)) {
            ""
        } else {
            " - Ep${record.epNo.replace("/", "_")}"
        }
        val title = record.episodeTitle
            ?.takeIf { it.isNotBlank() && !it.equals(record.animeName, ignoreCase = true) }
            ?.let { " - ${sanitizeFileName(it)}" } ?: ""
        return "$safeName$episodeMarker$title$extension"
    }

    private fun buildEpisodeSubtitleFileName(videoBaseName: String, extension: String): String =
        "${videoBaseName.ifBlank { "Episode" }}$extension"

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)

    private fun guessExtension(url: String): String {
        val path = try {
            URL(url).path
        } catch (_: Exception) {
            url
        }
        if (isHlsDownload(path)) return ".ts"
        val knownExtensions = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".ts", ".flv")
        for (extension in knownExtensions) {
            if (path.contains(extension, ignoreCase = true)) return extension
        }
        return ".mp4"
    }

    private fun isHlsDownload(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains(".m3u8") ||
            lowerUrl.contains(".m3u?") ||
            lowerUrl.endsWith(".m3u") ||
            lowerUrl.contains("/index.m3u") ||
            lowerUrl.contains("/master.m3u") ||
            lowerUrl.contains("/playlist.m3u")
    }

    private fun guessSubtitleExtension(url: String): String {
        val path = try {
            URL(url).path
        } catch (_: Exception) {
            url
        }
        val knownExtensions = listOf(".srt", ".ass", ".ssa", ".vtt", ".ttml")
        return knownExtensions.firstOrNull { extension ->
            path.contains(extension, ignoreCase = true)
        } ?: ".vtt"
    }

    private fun guessMimeType(fileName: String): String =
        when {
            fileName.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            fileName.endsWith(".webm", ignoreCase = true) -> "video/webm"
            fileName.endsWith(".avi", ignoreCase = true) -> "video/x-msvideo"
            fileName.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            fileName.endsWith(".ts", ignoreCase = true) -> "video/mp2t"
            fileName.endsWith(".flv", ignoreCase = true) -> "video/x-flv"
            else -> "video/mp4"
        }

    private fun guessSubtitleMimeType(fileName: String): String =
        when {
            fileName.endsWith(".srt", ignoreCase = true) -> "application/x-subrip"
            fileName.endsWith(".ass", ignoreCase = true) ||
                fileName.endsWith(".ssa", ignoreCase = true) -> "text/x-ssa"
            fileName.endsWith(".ttml", ignoreCase = true) -> "application/ttml+xml"
            fileName.endsWith(".vtt", ignoreCase = true) -> "text/vtt"
            else -> "text/plain"
        }

    private suspend fun downloadAuxiliaryFile(
        url: String,
        outputUri: Uri,
        headers: Map<String, String>,
    ) {
        val request = Request.Builder()
            .url(url)

        headers.forEach { (key, value) ->
            request.header(key, value)
        }

        client.newCall(request.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Unexpected subtitle HTTP ${response.code}")
            }
            val body = response.body
            writeToDocument(outputUri, append = false, startPosition = 0L) { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
        }
    }

    private fun replayHeadersFor(record: PersistedAnimeDownload): Map<String, String> =
        buildMap {
            putAll(record.requestHeaders.filterValues { it.isNotBlank() })

            if (!containsKey("Referer")) {
                (record.referer ?: defaultRefererFor(record.source))
                    .takeIf { it.isNotBlank() }
                    ?.let { put("Referer", it) }
            }

            if (!containsKey("User-Agent")) {
                (record.userAgent ?: defaultUserAgentFor(record.source))
                    .takeIf { it.isNotBlank() }
                    ?.let { put("User-Agent", it) }
            }

            if (!containsKey("Origin")) {
                (record.requestHeaders["Referer"] ?: record.referer ?: defaultRefererFor(record.source))
                    .takeIf { it.isNotBlank() }
                    ?.let(::originHeaderFromUrl)
                    ?.let { put("Origin", it) }
            }
        }

    private fun originHeaderFromUrl(url: String): String? =
        runCatching {
            URL(url).let { parsed ->
                val port = parsed.port.takeIf { it != -1 }?.let { ":$it" }.orEmpty()
                "${parsed.protocol}://${parsed.host}$port"
            }
        }.getOrNull()

    private fun defaultRefererFor(source: AnimeSource): String =
        sourceRegistry.defaultRefererFor(source)

    private fun defaultUserAgentFor(source: AnimeSource): String =
        sourceRegistry.defaultUserAgentFor(source)

    private fun downloadKey(animeName: String, epNo: String): String = "${animeName}_ep$epNo"
}

private data class ResolvedEpisodeLink(
    val url: String,
    val quality: String,
    val isM3u8: Boolean,
    val isEmbed: Boolean = false,
    val referer: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val translationType: String = "sub",
    val preferredSubtitleUrl: String? = null,
    val preferredSubtitleLabel: String? = null,
)

private data class PreferredSubtitleLink(
    val url: String,
    val label: String? = null,
)

private fun List<AniCliSubtitleTrack>.toAniCliPreferredSubtitle(): PreferredSubtitleLink? =
    mapNotNull { track ->
        if (!isEnglishSubtitle(languageCode = track.languageCode, label = track.label)) {
            return@mapNotNull null
        }
        track.url
            .takeIf { it.isNotBlank() }
            ?.let { PreferredSubtitleLink(url = it, label = track.label) }
    }.selectPreferredSubtitle()

private fun List<Subtitle>.toProviderPreferredSubtitle(): PreferredSubtitleLink? =
    mapNotNull { track ->
        if (!isEnglishSubtitle(languageCode = track.language, label = track.language)) {
            return@mapNotNull null
        }
        track.url
            .takeIf { it.isNotBlank() }
            ?.let { PreferredSubtitleLink(url = it, label = track.language) }
    }.selectPreferredSubtitle()

private fun List<PreferredSubtitleLink>.selectPreferredSubtitle(): PreferredSubtitleLink? =
    firstOrNull()

@Serializable
private enum class PersistedDownloadState {
    IDLE,
    PREPARING,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    FAILED,
}

@Serializable
private data class PersistedAnimeDownload(
    val key: String,
    val animeId: String,
    val animeName: String,
    val episodeId: String? = null,
    val epNo: String,
    val episodeTitle: String? = null,
    val preferredSubtitleUrl: String? = null,
    val preferredSubtitleLabel: String? = null,
    val source: AnimeSource,
    val translationType: String,
    val quality: String,
    val qualityMode: AnimeDownloadQualityMode,
    val directUrl: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val animeFolderUri: String,
    val fileUri: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val subtitleFileUri: String? = null,
    val subtitleFileName: String? = null,
    val subtitleMimeType: String? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val transferRateBytesPerSecond: Long? = null,
    val state: PersistedDownloadState = PersistedDownloadState.IDLE,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

private class PauseDownloadException : CancellationException("Download paused")
