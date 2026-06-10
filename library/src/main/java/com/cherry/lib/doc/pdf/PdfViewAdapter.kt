package com.cherry.lib.doc.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.cherry.lib.doc.R
import com.cherry.lib.doc.interfaces.OnPdfItemClickListener
import com.cherry.lib.doc.util.ViewUtils.hide
import com.cherry.lib.doc.util.ViewUtils.show

/*
 * -----------------------------------------------------------------
 * Copyright (C) 2018-2028, by Victor, All rights reserved.
 * -----------------------------------------------------------------
 * File: PdfViewAdapter
 * Author: Victor
 * Date: 2023/09/28 11:17
 * Description: 已优化 —— 减少冗余渲染请求，离屏自动清理
 * -----------------------------------------------------------------
 */

internal class PdfViewAdapter(
    private val renderer: PdfRendererCore?,
    private val pageSpacing: Rect,
    private val enableLoadingForPages: Boolean,
    private val listener: OnPdfItemClickListener?
) :
    RecyclerView.Adapter<PdfViewAdapter.PdfPageViewHolder>() {

    // 跟踪当前已请求渲染但尚未完成的页码
    private val pendingRenderPages = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        return PdfPageViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_pdf, parent, false)
        )
    }

    override fun getItemCount(): Int {
        try {
            return renderer?.getPageCount() ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        // 不在 onBindViewHolder 中触发渲染，改由 onViewAttachedToWindow 处理
        // 这里只做基本的重置
        holder.resetView()
    }

    override fun onViewRecycled(holder: PdfPageViewHolder) {
        super.onViewRecycled(holder)
        holder.recycleView()
    }

    inner class PdfPageViewHolder : RecyclerView.ViewHolder, View.OnAttachStateChangeListener {
        private var containerView: FrameLayout? = null
        private var pdfViewPageLoadingProgress: ProgressBar? = null
        private var pageView: ImageView? = null
        private var isRendered = false

        constructor(itemView: View) : super(itemView) {
            containerView = itemView.findViewById(R.id.container_view)
            pdfViewPageLoadingProgress = itemView.findViewById(R.id.pdf_view_page_loading_progress)
            pageView = itemView.findViewById(R.id.pageView)

            containerView?.setOnClickListener {
                listener?.OnPdfItemClick(adapterPosition)
            }

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

            // 避免对同一个位置发起重复的渲染请求
            synchronized(pendingRenderPages) {
                if (pendingRenderPages.contains(position)) {
                    // 已经请求过渲染了，只需更新 loading 状态
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

                // 仅当 RecyclerView 尚未复用此 ViewHolder 时才更新 UI
                if (pageNo == adapterPosition) {
                    bitmap?.let {
                        isRendered = true
                        containerView?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                            val scaleFactor = 1.2f
                            height = ((containerView?.width?.toFloat() ?: 0f) / (bitmap.width.toFloat() / bitmap.height.toFloat()) * scaleFactor).toInt()
                            this.topMargin = pageSpacing.top
                            this.leftMargin = pageSpacing.left
                            this.rightMargin = pageSpacing.right
                            this.bottomMargin = pageSpacing.bottom
                        }
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

    fun drawableToBitmap(drawable: Drawable): Bitmap? {
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            if (drawable.opacity != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        return bitmap
    }
}
