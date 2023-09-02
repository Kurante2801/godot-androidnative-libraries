package com.kurante.godotnative.saf.extensions

import androidx.documentfile.provider.DocumentFile
import org.godotengine.godot.Dictionary

fun DocumentFile.toGodotDictionary() = Dictionary().apply {
    val document = this@toGodotDictionary
    this["uri"] = document.uri.toString()
    this["is_tree"] = document.uri.isTree
    this["is_file"] = document.isFile
    this["is_directory"] = document.isDirectory
    this["name"] = document.name
    this["path"] = document.uri.path
}
