package com.kurante.godotnative.core

import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.LENGTH_SHORT
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot

class Plugin(godot: Godot) : GodotPlugin(godot) {
    override fun getPluginName() = "AndroidNativeCore"

    @UsedByGodot
    fun toast(text: String, long: Boolean) = runOnUiThread {
        if (activity != null)
            Toast.makeText(activity, text, if (long) LENGTH_LONG else LENGTH_SHORT).show()
    }
}