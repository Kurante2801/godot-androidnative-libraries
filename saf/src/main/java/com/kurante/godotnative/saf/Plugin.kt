package com.kurante.godotnative.saf

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kurante.godotnative.saf.Plugin.Companion.Activity.OPEN_DOCUMENT
import com.kurante.godotnative.saf.Plugin.Companion.Signal.OPEN_DOCUMENT_ERROR
import com.kurante.godotnative.saf.Plugin.Companion.Signal.OPEN_DOCUMENT_INFO
import com.kurante.godotnative.saf.extensions.toGodotDictionary
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

class Plugin(godot: Godot) : GodotPlugin(godot) {
    override fun getPluginName() = "AndroidNativeSAF"

    companion object {
        const val INITIAL_CODE = 2801

        enum class Activity {
            OPEN_DOCUMENT
        }

        enum class Signal(val signal: String) {
            OPEN_DOCUMENT_ERROR("open_document_error"), OPEN_DOCUMENT_INFO("open_document_info"),
        }
    }

    override fun getPluginSignals() = setOf(
        SignalInfo(OPEN_DOCUMENT_ERROR.signal, Int::class.javaObjectType, String::class.javaObjectType),
        SignalInfo(OPEN_DOCUMENT_INFO.signal, Int::class.javaObjectType, Dictionary::class.javaObjectType),
    )

    //private fun signal(signal: Signal, vararg args: Any) = emitSignal(signal.signal, args)

    private val requests = mutableMapOf<Int, Activity>()

    // Reserves an activity code so it's not used for anything else
    private fun getCode(activity: Activity): Int {
        // Get a code that's not being used
        var code = INITIAL_CODE
        while (requests.containsKey(code)) code++

        requests[code] = activity
        return code
    }

    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onMainActivityResult(requestCode, resultCode, data)

        val activity = requests[requestCode] ?: return
        requests.remove(requestCode)

        when (activity) {
            OPEN_DOCUMENT -> open_document_response(requestCode, resultCode, data)
        }
    }

    private fun persistUri(uri: Uri) {
        activity!!.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @UsedByGodot
    @Suppress("FunctionName")
    fun open_document(persist: Boolean, mimeType: String): Int {
        val code = getCode(OPEN_DOCUMENT)

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (persist) addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

            putExtra("PERSIST", persist)
        }

        runOnRenderThread {
            activity!!.startActivityForResult(intent, code)
        }

        return code
    }

    @Suppress("FunctionName")
    @SuppressLint("UseRequireInsteadOfGet")
    fun open_document_response(code: Int, result: Int, data: Intent?) {
        if (result != RESULT_OK) return emitSignal(OPEN_DOCUMENT_ERROR.signal, code, "User cancelled operation")

        val uri = data?.data ?: return emitSignal(OPEN_DOCUMENT_ERROR.signal, code, "Could not get Intent's Uri")
        val document = DocumentFile.fromSingleUri(activity!!, uri)!!

        if (data.getBooleanExtra("PERSIST", false)) persistUri(uri)
        emitSignal(OPEN_DOCUMENT_INFO.signal, code, document.toGodotDictionary())
    }
}