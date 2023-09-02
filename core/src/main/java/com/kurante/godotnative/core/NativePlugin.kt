package com.kurante.godotnative.core

import android.content.Intent
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin

/**
 * Includes helper functions for Godot
 */
open class NativePlugin(
    private val name: String,
    godot: Godot,
) : GodotPlugin(godot) {
    override fun getPluginName() = name

    /**
     * Calls emitSignal on the render thread
     */
    protected fun signal(name: String, vararg args: Any) = runOnRenderThread { emitSignal(name, *args) }

    private val requests = mutableSetOf<Int>()

    /**
     * Returns an activity code that's not being used by this plugin.
     */
    protected fun getCode(initial: Int): Int {
        var code = initial
        while (requests.contains(code)) code++
        requests.add(code)
        return code
    }

    protected open fun onActivityCode(code: Int, result: Int, data: Intent?) { }

    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onMainActivityResult(requestCode, resultCode, data)

        if (requests.contains(requestCode)) {
            requests.remove(requestCode)
            onActivityCode(requestCode, resultCode, data)
        }
    }
}