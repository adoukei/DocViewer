package com.cherry.lib.doc.pdf

import com.cherry.lib.doc.interfaces.OnDownloadListener
import com.cherry.lib.doc.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.net.URL

/*
 * -----------------------------------------------------------------
 * Copyright (C) 2018-2028, by Victor, All rights reserved.
 * -----------------------------------------------------------------
 * File: PdfDownloader
 * Author: Victor
 * Date: 2023/09/28 11:13
 * Description: 已优化 —— 增大缓冲区、修复进度计算、支持协程取消
 * -----------------------------------------------------------------
 */

internal class PdfDownloader(url: String, private val listener: OnDownloadListener) {

    private val scope = listener.getCoroutineScope()

    init {
        scope.launch(Dispatchers.IO) {
            download(url)
        }
    }

    private suspend fun CoroutineScope.download(downloadUrl: String) {
        val format = FileUtils.getFileFormatForUrl(downloadUrl)
        launch(Dispatchers.Main) { listener.onDownloadStart() }
        val outputFile = File(listener.getDownloadContext().cacheDir, "doc.$format")
        if (outputFile.exists())
            outputFile.delete()
        try {
            val bufferSize = 32768
            val url = URL(downloadUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            val totalLength = connection.contentLength
            val inputStream = BufferedInputStream(url.openStream(), bufferSize)
            val outputStream = outputFile.outputStream().buffered(bufferSize)
            var downloaded = 0L
            var lastReportTime = 0L

            val data = ByteArray(bufferSize)
            while (isActive) {
                val count = inputStream.read(data)
                if (count == -1)
                    break
                outputStream.write(data, 0, count)
                downloaded += count

                if (totalLength > 0) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastReportTime > 200 || downloaded >= totalLength) {
                        lastReportTime = currentTime
                        launch(Dispatchers.Main) {
                            listener.onDownloadProgress(downloaded, totalLength.toLong())
                        }
                    }
                }
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
            launch(Dispatchers.Main) { listener.onError(e) }
            return
        }
        launch(Dispatchers.Main) { listener.onDownloadSuccess(outputFile.absolutePath) }
    }
}
