package com.cherry.lib.doc.pdf

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min

/*
 * -----------------------------------------------------------------
 * Copyright (C) 2018-2028, by Victor, All rights reserved.
 * -----------------------------------------------------------------
 * File: PdfRendererCore
 * Author: Victor
 * Date: 2023/09/28 11:16
 * Description: PDF 渲染核心 —— 已优化：共享协程作用域、并行渲染、JPEG 缓存
 * -----------------------------------------------------------------
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
internal class PdfRendererCore(
    private val context: Context,
    pdfFile: File,
    private val pdfQuality: PdfQuality
) {
    companion object {
        private const val PREFETCH_COUNT = 3
        private const val MAX_CONCURRENT_RENDERS = 2
        private const val JPEG_QUALITY = 80
    }

    private var pdfRenderer: PdfRenderer? = null

    private val cachePath = "___pdf___cache___"
    private val renderSemaphore = Semaphore(MAX_CONCURRENT_RENDERS)

    // 共享协程作用域：使用 SupervisorJob，避免某个渲染失败影响其他任务
    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 记录正在渲染的页码，避免重复渲染
    private val renderingPages = mutableSetOf<Int>()
    private val renderingLock = Object()

    init {
        initCache()
        openPdfFile(pdfFile)
    }

    private fun initCache() {
        val cache = File(context.cacheDir, cachePath)
        if (cache.exists())
            cache.deleteRecursively()
        cache.mkdirs()
    }

    private fun getBitmapFromCache(pageNo: Int, quality: PdfQuality? = pdfQuality): Bitmap? {
        val loadPath = File(File(context.cacheDir, cachePath), "$quality-$pageNo")
        if (!loadPath.exists())
            return null

        return try {
            BitmapFactory.decodeFile(loadPath.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun pageExistInCache(pageNo: Int, quality: PdfQuality? = pdfQuality): Boolean {
        val loadPath = File(File(context.cacheDir, cachePath), "$quality-$pageNo")
        return loadPath.exists()
    }

    private fun writeBitmapToCache(pageNo: Int, quality: PdfQuality? = pdfQuality, bitmap: Bitmap) {
        try {
            val savePath = File(File(context.cacheDir, cachePath), "$quality-$pageNo")
            savePath.createNewFile()
            val fos = FileOutputStream(savePath)
            // 使用 JPEG 替代 PNG，大幅提升缓存写入速度（PNG 压缩极慢）
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
            fos.flush()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openPdfFile(pdfFile: File) {
        try {
            val fileDescriptor =
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPageCount(): Int {
        var pageCount = 0
        try {
            pageCount = pdfRenderer?.pageCount ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return pageCount
    }

    fun renderPage(pageNo: Int, quality: PdfQuality? = pdfQuality, onBitmapReady: ((bitmap: Bitmap?, pageNo: Int) -> Unit)? = null) {
        if (pageNo >= getPageCount())
            return

        // 先检查缓存
        val cachedBitmap = getBitmapFromCache(pageNo, quality)
        if (cachedBitmap != null) {
            mainScope.launch { onBitmapReady?.invoke(cachedBitmap, pageNo) }
            return
        }

        // 避免同一页重复渲染
        synchronized(renderingLock) {
            if (renderingPages.contains(pageNo)) return
            renderingPages.add(pageNo)
        }

        // 使用共享作用域发起渲染，通过 Semaphore 控制并发数
        renderScope.launch {
            renderSemaphore.withPermit {
                buildBitmap(pageNo, quality) { bitmap ->
                    mainScope.launch { onBitmapReady?.invoke(bitmap, pageNo) }
                    synchronized(renderingLock) {
                        renderingPages.remove(pageNo)
                    }
                    // 渲染完成后预取后续页面
                    onBitmapReady?.let {
                        prefetchNext(pageNo + 1, quality)
                    }
                }
            }
        }
    }

    private fun prefetchNext(pageNo: Int, quality: PdfQuality? = pdfQuality) {
        val totalPageCount = getPageCount()
        if (pageNo >= totalPageCount) return

        val countForPrefetch = min(totalPageCount, pageNo + PREFETCH_COUNT)
        for (pageToPrefetch in pageNo until countForPrefetch) {
            if (pageExistInCache(pageToPrefetch, quality)) continue
            val alreadyRendering = synchronized(renderingLock) {
                if (renderingPages.contains(pageToPrefetch)) {
                    true
                } else {
                    renderingPages.add(pageToPrefetch)
                    false
                }
            }
            if (alreadyRendering) continue
            renderScope.launch {
                renderSemaphore.withPermit {
                    buildBitmap(pageToPrefetch, quality) {
                        synchronized(renderingLock) {
                            renderingPages.remove(pageToPrefetch)
                        }
                    }
                }
            }
        }
    }

    private fun buildBitmap(pageNo: Int, quality: PdfQuality? = pdfQuality, onBitmap: (Bitmap?) -> Unit) {
        var bitmap = getBitmapFromCache(pageNo, quality)
        bitmap?.let {
            onBitmap(it)
            return@buildBitmap
        }

        try {
            val ratio = quality?.ratio ?: 1
            val pdfPage = pdfRenderer!!.openPage(pageNo)
            bitmap = Bitmap.createBitmap(
                pdfPage.width * ratio,
                pdfPage.height * ratio,
                Bitmap.Config.ARGB_8888
            )
            bitmap ?: return
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pdfPage.close()
            writeBitmapToCache(pageNo, quality, bitmap)

            onBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            onBitmap(null)
        }
    }

    fun closePdfRender() {
        // 取消所有渲染任务
        renderScope.coroutineContext[Job]?.cancel()
        mainScope.coroutineContext[Job]?.cancel()
        synchronized(renderingLock) {
            renderingPages.clear()
        }
        try {
            pdfRenderer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pdfRenderer = null
    }
}
