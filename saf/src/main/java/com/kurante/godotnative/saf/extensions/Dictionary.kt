package com.kurante.godotnative.saf.extensions

import org.godotengine.godot.Dictionary

fun Dictionary.error(err: String) = apply { this["error"] = err }

fun Dictionary.data(data: Any) = apply { this["data"] = data }

fun Dictionary.code(code: Int) = apply { this["code"] = code }