package me.bnfy.blanc.ui

import android.net.Uri
import android.webkit.ValueCallback

/**
 * Handler interface for file chooser requests from WebViews.
 */
fun interface FileChooserHandler {
    fun onFileChooser(callback: ValueCallback<Array<Uri>>?, acceptTypes: List<String>)
}
