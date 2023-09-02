package com.kurante.godotnative.saf

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kurante.godotnative.saf.Plugin.Companion.Activity.OPEN_DOCUMENT
import com.kurante.godotnative.saf.Plugin.Companion.Activity.READ_BYTES
import com.kurante.godotnative.saf.Plugin.Companion.Signal.OPEN_DOCUMENT_ERROR
import com.kurante.godotnative.saf.Plugin.Companion.Signal.OPEN_DOCUMENT_DATA
import com.kurante.godotnative.saf.Plugin.Companion.Signal.READ_BYTES_DATA
import com.kurante.godotnative.saf.Plugin.Companion.Signal.READ_BYTES_ERROR
import com.kurante.godotnative.saf.extensions.toDocumentFile
import com.kurante.godotnative.saf.extensions.toGodotDictionary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import java.io.InputStream

class Plugin(godot: Godot) : GodotPlugin(godot) {
    override fun getPluginName() = "AndroidNativeSAF"

    companion object {
        const val INITIAL_CODE = 2801

        enum class Activity {
            OPEN_DOCUMENT,
            READ_BYTES, // Not an activity, but we can reuse the await system we have on godot
        }

        enum class Signal {
            OPEN_DOCUMENT_ERROR,
            OPEN_DOCUMENT_DATA,
            READ_BYTES_ERROR,
            READ_BYTES_DATA,
        }
    }

    override fun getPluginSignals() = setOf(
        SignalInfo(OPEN_DOCUMENT_ERROR.name.lowercase(), Int::class.javaObjectType, String::class.javaObjectType),
        SignalInfo(OPEN_DOCUMENT_DATA.name.lowercase(), Int::class.javaObjectType, Dictionary::class.javaObjectType),
        SignalInfo(READ_BYTES_ERROR.name.lowercase(), Int::class.javaObjectType, String::class.javaObjectType),
        SignalInfo(READ_BYTES_DATA.name.lowercase(), Int::class.javaObjectType, ByteArray::class.javaObjectType),
    )

    private fun signal(signal: Signal, vararg args: Any) = runOnRenderThread {
        emitSignal(signal.name.lowercase(), *args)
    }

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
            else -> {}
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

        runOnUiThread {
            activity!!.startActivityForResult(intent, code)
        }

        return code
    }

    @Suppress("FunctionName")
    @SuppressLint("UseRequireInsteadOfGet")
    fun open_document_response(code: Int, result: Int, data: Intent?) {
        if (result != RESULT_OK) return signal(OPEN_DOCUMENT_ERROR, code, "User cancelled operation")

        val uri = data?.data ?: return signal(OPEN_DOCUMENT_ERROR, code, "Could not get Intent's Uri")
        val document = DocumentFile.fromSingleUri(activity!!, uri)!!

        if (data.getBooleanExtra("PERSIST", false)) persistUri(uri)
        signal(OPEN_DOCUMENT_DATA, code, document.toGodotDictionary())
    }

    @UsedByGodot
    @Suppress("FunctionName")
    fun read_bytes(uri: String): Int {
        val document = Uri.parse(uri).toDocumentFile(activity!!)
        val code = getCode(READ_BYTES)

        CoroutineScope(Dispatchers.IO).launch {
            lateinit var stream: InputStream

            val error = when {
                !document.exists() -> "File doesn't exist"
                !document.canRead() -> "File can't be read"
                else -> {
                    try {
                        stream = activity!!.contentResolver.openInputStream(document.uri)!!
                        null
                    } catch (e: Exception) { e.message }
                }
            }

            // Launch the error with a bit of delay, so Godot has the chance to register the code we send
            if (error != null) {
                delay(100)
                signal(OPEN_DOCUMENT_ERROR, code, error)
                return@launch
            }

            stream.use {
                signal(READ_BYTES_DATA, code, it.readBytes())
            }
        }

        return code
    }
}