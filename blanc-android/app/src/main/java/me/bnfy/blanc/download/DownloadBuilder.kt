package me.bnfy.blanc.download

import me.bnfy.blanc.bridge.BridgeProtocol
import me.bnfy.blanc.storage.DownloadEntity

/**
 * Builder for converting DownloadEntity to protocol DownloadRecord.
 */
object DownloadBuilder {
    fun fromDownload(download: DownloadEntity): BridgeProtocol.DownloadRecord {
        return BridgeProtocol.DownloadRecord(
            id = download.id,
            windowId = download.windowId,
            tabId = download.tabId,
            url = download.url,
            fileName = download.fileName,
            mimeType = download.mimeType,
            totalBytes = download.totalBytes,
            receivedBytes = download.receivedBytes,
            targetPath = download.targetPath,
            state = download.state,
            error = download.error,
            startedAt = download.startedAt,
            completedAt = download.completedAt
        )
    }
}