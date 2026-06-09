package com.cherry.doc

import android.app.Application
import com.cherry.lib.doc.util.AndroidUtils

/*
 * -----------------------------------------------------------------
 * Copyright (C) 2018-2028, by Victor, All rights reserved.
 * -----------------------------------------------------------------
 * File: App
 * Author: Victor
 * Date: 2023/10/16 17:26
 * Description: 
 * -----------------------------------------------------------------
 */

class App: Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidUtils.init(this)
    }
}