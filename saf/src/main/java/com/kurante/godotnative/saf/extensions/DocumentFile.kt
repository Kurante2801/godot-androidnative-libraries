package com.kurante.godotnative.saf.extensions

import androidx.documentfile.provider.DocumentFile
import org.godotengine.godot.Dictionary

fun DocumentFile.toGodotDictionary() = Dictionary().apply {
    val document = this@toGodotDictionary
    this["uri"] = document.uri.toString()

    // Need to rewrite DocumentsContract.isTreeUri, since it needs API 24
    val segments = document.uri.pathSegments
    this["is_tree"] = segments.size >= 2 && "tree" == segments[0]

    this["is_file"] = document.isFile
    this["is_directory"] = document.isDirectory
    this["name"] = document.name
    this["path"] = document.uri.path
}
