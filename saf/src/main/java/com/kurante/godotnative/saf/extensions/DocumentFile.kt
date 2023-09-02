package com.kurante.godotnative.saf.extensions

import androidx.documentfile.provider.DocumentFile
import org.godotengine.godot.Dictionary

fun DocumentFile.toGodotDictionary(isTree: Boolean) = Dictionary().apply {
    val document = this@toGodotDictionary
    this["uri"] = document.uri.toString()
    this["is_tree"] = isTree
    this["is_file"] = document.isFile
    this["is_directory"] = document.isDirectory
    this["name"] = document.name
    this["path"] = document.uri.path
}

fun DocumentFile.toGodotDictionary() = toGodotDictionary(uri.isTree)
