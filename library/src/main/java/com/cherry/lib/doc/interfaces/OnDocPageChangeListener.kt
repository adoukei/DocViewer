package com.cherry.lib.doc.interfaces

/*
 * -----------------------------------------------------------------
 * Copyright (C) 2018-2028, by Victor, All rights reserved.
 * -----------------------------------------------------------------
 * File: OnPdfPageChangeListener
 * Author: Victor
 * Date: 2023/10/31 11:12
 * Description: 
 * -----------------------------------------------------------------
 */

interface OnDocPageChangeListener {
    fun OnPageChanged(foundPosition: Int, totalPageCount: Int)
}

/**
 * Office 文档（Word/PPT）页码变化监听器
 */
interface OnOfficePageChangeListener {
    fun onOfficePageChanged(currentPage: Int, totalPages: Int)
}