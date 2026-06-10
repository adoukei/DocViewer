package com.cherry.lib.doc.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.cherry.lib.doc.R
import com.cherry.lib.doc.util.ViewUtils.hide
import com.cherry.lib.doc.util.ViewUtils.show

/*
 * -----------------------------------------------------------------
 * Copyright (C) 2018-2028, by Victor, All rights reserved.
 * -----------------------------------------------------------------
 * File: PdfPageViewAdapter
 * Author: Victor
 * Date: 2023/09/28 11:17
 * Description: 已优化 —— ViewPager 模式 PDF 适配器，减少冗余渲染
 * -----------------------------------------------------------------
 */

internal class PdfPageViewAdapter(
    private val renderer: PdfRendererCore?,
    private val pageSpacing: Rect,
    private val enableLoadingForPages: Boolean
) :
    RecyclerView.Adapter<PdfPageViewAdapter.PdfPageViewHolder>() {

    private val pendingRenderPages = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        return PdfPageViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.page_item_pdf, parent, false)
        )
    }

    override fun getItemCount(): Int {
        return renderer?.getPageCount() ?: 0
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.resetView()
    }

    override fun onViewRecycled(holder: PdfPageViewHolder) {
        super.onViewRecycled(holder)
        holder.recycleView()
    }

    inner class PdfPageViewHolder : RecyclerView.ViewHolder, View.OnAttachStateChangeListener {
        private var pdfViewPageLoadingProgress: ProgressBar? = null
        private var pageView: ImageView? = null
        private var isRendered = false

        constructor(itemView: View) : super(itemView) {
            pdfViewPageLoadingProgress = itemView.findViewById(R.id.pdf_view_page_loading_progress)
            pageView = itemView.findViewById(R.id.pageView)
            itemView.addOnAttachStateChangeListener(this)
        }

        fun resetView() {
            isRendered = false
            showLoading()
        }

        fun recycleView() {
            isRendered = false
            pageView?.setImageBitmap(null)
            pageView?.clearAnimation()
            showLoading()
        }

        private fun showLoading() {
            if (enableLoadingForPages) {
                val inCache = renderer?.pageExistInCache(adapterPosition) == true
                if (!inCache) {
                    pdfViewPageLoadingProgress?.show()
                } else {
                    pdfViewPageLoadingProgress?.hide()
                }
            } else {
                pdfViewPageLoadingProgress?.hide()
            }
        }

        private fun handleLoadingForPage(position: Int) {
            if (!enableLoadingForPages) {
                pdfViewPageLoadingProgress?.hide()
                return
            }

            if (renderer?.pageExistInCache(position) == true) {
                pdfViewPageLoadingProgress?.hide()
            } else {
                pdfViewPageLoadingProgress?.show()
            }
        }

        override fun onViewAttachedToWindow(p0: View) {
            val position = adapterPosition
            if (position == RecyclerView.NO_POSITION || isRendered) return

            synchronized(pendingRenderPages) {
                if (pendingRenderPages.contains(position)) {
                    handleLoadingForPage(position)
                    return
                }
                pendingRenderPages.add(position)
            }

            handleLoadingForPage(position)
            renderer?.renderPage(position) { bitmap: Bitmap?, pageNo: Int ->
                synchronized(pendingRenderPages) {
                    pendingRenderPages.remove(pageNo)
                }

                if (pageNo == adapterPosition) {
                    bitmap?.let {
                        isRendered = true
                        pageView?.setImageBitmap(bitmap)
                        pageView?.animation = AlphaAnimation(0F, 1F).apply {
                            interpolator = LinearInterpolator()
                            duration = 200
                        }
                        pdfViewPageLoadingProgress?.hide()
                    }
                }
            }
        }

        override fun onViewDetachedFromWindow(p0: View) {
            pageView?.setImageBitmap(null)
            pageView?.clearAnimation()
            isRendered = false
        }
    }
}
