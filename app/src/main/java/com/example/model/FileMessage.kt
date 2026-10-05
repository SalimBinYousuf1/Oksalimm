package com.example.model

import org.json.JSONArray
import org.json.JSONObject

data class RemoteFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0,
    val lastModified: Long = 0
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "Folder"
            val kb = sizeBytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }
}

data class FileTransferProgress(
    val transferId: String,
    val filename: String,
    val isUpload: Boolean,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val currentChunk: Int,
    val totalChunks: Int,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val percentage: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        else if (totalChunks > 0) (currentChunk.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)
        else 0f
}

object FileProtocol {
    fun createListFilesRequest(path: String): String = JSONObject().apply {
        put("type", "list_files")
        put("path", path)
    }.toString()

    fun createDownloadRequest(path: String, transferId: String): String = JSONObject().apply {
        put("type", "download_file")
        put("path", path)
        put("transferId", transferId)
    }.toString()

    fun createUploadInit(destinationFolder: String, filename: String, sizeBytes: Long, totalChunks: Int, transferId: String): String = JSONObject().apply {
        put("type", "upload_file_init")
        put("path", destinationFolder)
        put("filename", filename)
        put("totalBytes", sizeBytes)
        put("totalChunks", totalChunks)
        put("transferId", transferId)
    }.toString()

    fun createChunkMessage(transferId: String, filename: String, chunkIndex: Int, totalChunks: Int, isLast: Boolean, base64Data: String): String = JSONObject().apply {
        put("type", "file_chunk")
        put("transferId", transferId)
        put("filename", filename)
        put("chunkIndex", chunkIndex)
        put("totalChunks", totalChunks)
        put("isLast", isLast)
        put("data", base64Data)
    }.toString()

    fun parseFileListResponse(json: JSONObject): Pair<String, List<RemoteFileItem>>? {
        try {
            val path = json.optString("path", "/")
            val itemsJson = json.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<RemoteFileItem>()
            for (i in 0 until itemsJson.length()) {
                val item = itemsJson.getJSONObject(i)
                val name = item.getString("name")
                val itemPath = item.optString("path", if (path.endsWith("/")) "$path$name" else "$path/$name")
                val isDir = item.optBoolean("isDirectory", item.optBoolean("isDir", false))
                val size = item.optLong("size", item.optLong("sizeBytes", 0))
                val modified = item.optLong("lastModified", 0)
                list.add(RemoteFileItem(name, itemPath, isDir, size, modified))
            }
            // Sort folders first, then alphabetical
            return path to list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (_: Exception) {
            return null
        }
    }
}
