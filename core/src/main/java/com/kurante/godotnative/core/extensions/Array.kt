package com.kurante.godotnative.core.extensions

import org.godotengine.godot.Dictionary

// Since we can't pass arrays of dictionaries, we just make a dictionary of dictionaries
fun <T> Array<T>.toDictionary() = Dictionary().apply {
    val array = this@toDictionary
    this["len"] = array.size

    array.forEachIndexed { i, value ->
        this[i.toString()] = value
    }
}