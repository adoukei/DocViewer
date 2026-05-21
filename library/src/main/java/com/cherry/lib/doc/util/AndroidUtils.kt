package com.cherry.lib.doc.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object AndroidUtils {

    private var appContext: Context? = null

    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @JvmStatic
    fun getContext(): Context {
        return appContext ?: throw IllegalStateException("AndroidUtils not initialized. Call init() first.")
    }

    @JvmStatic
    fun getPackageName(): String {
        return getContext().packageName
    }

    @JvmStatic
    fun getResources(): android.content.res.Resources {
        return getContext().resources
    }

    @JvmStatic
    fun getScreenWidth(): Int {
        val displayMetrics = DisplayMetrics()
        val windowManager = getContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics.widthPixels
    }

    @JvmStatic
    fun getScreenHeight(): Int {
        val displayMetrics = DisplayMetrics()
        val windowManager = getContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics.heightPixels
    }

    @JvmStatic
    fun uriToFile(uri: Uri): File? {
        var filePath: String? = null
        val cursor = getContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex != -1) {
                    val displayName = it.getString(columnIndex)
                    val cacheDir = File(getContext().cacheDir, "uri_cache")
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs()
                    }
                    filePath = File(cacheDir, displayName).absolutePath
                }
            }
        }

        if (filePath != null) {
            val file = File(filePath)
            if (!file.exists()) {
                try {
                    val inputStream: InputStream? = getContext().contentResolver.openInputStream(uri)
                    inputStream?.use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(4 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            return file
        }
        return null
    }

    @JvmStatic
    fun uriToFileNoCacheCopy(uri: Uri): File? {
        return uriToFile(uri)
    }
}
