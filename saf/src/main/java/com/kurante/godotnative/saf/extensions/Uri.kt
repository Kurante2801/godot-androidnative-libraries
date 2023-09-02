package com.kurante.godotnative.saf.extensions

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

// Need to rewrite DocumentsContract.isTreeUri, since it needs API 24
val Uri.isTree: Boolean
    get() {
        val segments = pathSegments
        return segments.size >= 2 && segments.first() == "tree"
    }

fun Uri.toDocumentFile(context: Context): DocumentFile {
    return if (isTree) DocumentFile.fromTreeUri(context, this)!!
    else DocumentFile.fromSingleUri(context, this)!!
}
