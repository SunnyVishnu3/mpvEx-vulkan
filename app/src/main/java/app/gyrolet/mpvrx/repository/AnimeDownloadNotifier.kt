package app.gyrolet.mpvrx.repository

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.anicli.AnimeDownloadInfo
import app.gyrolet.mpvrx.domain.anicli.DownloadState

internal class AnimeDownloadNotifier(
    private val context: Context,
) {

    private val manager by lazy { NotificationManagerCompat.from(context) }

    @SuppressLint("MissingPermission")
    fun sync(downloads: List<AnimeDownloadInfo>) {
        ensureChannel()
        if (!canNotify()) {
            clearAll()
            return
        }

        val visibleDownloads = downloads.filter { download ->
            download.state is DownloadState.Preparing ||
                download.state is DownloadState.InProgress ||
                download.state is DownloadState.Paused ||
                download.state is DownloadState.Failed
        }

        val grouped = visibleDownloads.groupBy { it.animeName }
        val activeIds = mutableSetOf<Int>()

        grouped.forEach { (animeName, animeDownloads) ->
            val groupKey = groupKey(animeName)
            animeDownloads.forEach { info ->
                val notificationId = notificationIdFor(info.key)
                activeIds += notificationId
                manager.notify(notificationId, buildEpisodeNotification(info, groupKey))
            }

            val summaryId = summaryNotificationIdFor(animeName)
            activeIds += summaryId
            manager.notify(summaryId, buildSummaryNotification(animeName, animeDownloads.size, groupKey))
        }

        knownNotificationIds
            .toSet()
            .minus(activeIds)
            .forEach(manager::cancel)

        knownNotificationIds.clear()
        knownNotificationIds += activeIds
    }

    private fun buildEpisodeNotification(
        info: AnimeDownloadInfo,
        groupKey: String,
    ) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(info.animeName)
        .setContentText(buildStatusLine(info))
        .setSubText("Episode ${info.epNo}")
        .setContentIntent(contentIntent())
        .setOnlyAlertOnce(true)
        .setOngoing(info.state is DownloadState.Preparing || info.state is DownloadState.InProgress)
        .setAutoCancel(info.state is DownloadState.Failed)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setGroup(groupKey)
        .setProgress(
            100,
            progressPercent(info),
            info.totalBytes == null || info.totalBytes <= 0L,
        )
        .build()

    private fun buildSummaryNotification(
        animeName: String,
        episodeCount: Int,
        groupKey: String,
    ) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(animeName)
        .setContentText(
            if (episodeCount == 1) {
                "1 active download"
            } else {
                "$episodeCount active downloads"
            }
        )
        .setContentIntent(contentIntent())
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setGroup(groupKey)
        .setGroupSummary(true)
        .build()

    private fun buildStatusLine(info: AnimeDownloadInfo): String =
        when (val state = info.state) {
            DownloadState.Preparing -> "Preparing..."
            is DownloadState.InProgress -> {
                val speed = info.transferRateBytesPerSecond?.takeIf { it > 0L }?.let(::formatSpeed)
                listOfNotNull(
                    progressLabel(info.totalBytes, state.progress),
                    formatSize(info.bytesDownloaded, info.totalBytes),
                    speed,
                ).joinToString(" • ")
            }

            is DownloadState.Paused -> {
                listOfNotNull(
                    "Paused",
                    progressLabel(info.totalBytes, state.progress),
                    formatSize(info.bytesDownloaded, info.totalBytes),
                ).joinToString(" • ")
            }

            is DownloadState.Failed -> "Failed • ${state.error}"
            DownloadState.Completed -> "Completed"
            DownloadState.Idle -> "Idle"
        }

    private fun progressPercent(info: AnimeDownloadInfo): Int {
        val total = info.totalBytes?.takeIf { it > 0L } ?: return 0
        return ((info.bytesDownloaded * 100L) / total).toInt().coerceIn(0, 100)
    }

    private fun progressLabel(totalBytes: Long?, progress: Float): String? =
        totalBytes
            ?.takeIf { it > 0L }
            ?.let { "${(progress * 100f).toInt()}%" }

    private fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

    private fun formatSize(downloadedBytes: Long, totalBytes: Long?): String =
        if (totalBytes != null && totalBytes > 0L) {
            "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
        } else {
            formatBytes(downloadedBytes)
        }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val value = bytes.toDouble()
        return when {
            value >= gb -> String.format("%.2f GB", value / gb)
            value >= mb -> String.format("%.1f MB", value / mb)
            value >= kb -> String.format("%.1f KB", value / kb)
            else -> "$bytes B"
        }
    }

    private fun contentIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.flags = intent.flags or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Anime downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows anime episode download progress"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun canNotify(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun clearAll() {
        knownNotificationIds.forEach(manager::cancel)
        knownNotificationIds.clear()
    }

    private fun groupKey(animeName: String): String = "$CHANNEL_ID.${animeName.lowercase()}"

    private fun notificationIdFor(key: String): Int = stableId("episode:$key")

    private fun summaryNotificationIdFor(animeName: String): Int = stableId("summary:$animeName")

    private fun stableId(value: String): Int = value.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }

    private companion object {
        private const val CHANNEL_ID = "anime_downloads"
        private val knownNotificationIds = linkedSetOf<Int>()
    }
}
