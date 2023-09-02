package com.kurante.godotnative.saf

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kurante.godotnative.core.NativePlugin
import com.kurante.godotnative.saf.extensions.code
import com.kurante.godotnative.saf.extensions.data
import com.kurante.godotnative.saf.extensions.error
import com.kurante.godotnative.saf.extensions.persist
import com.kurante.godotnative.saf.extensions.toDocumentFile
import com.kurante.godotnative.saf.extensions.toGodotDictionary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import java.io.InputStream

class Plugin(godot: Godot) : NativePlugin("AndroidNativeSAF", godot) {
    enum class Activity {
        OPEN_DOCUMENT, READ_BYTES_ASYNC
    }

    override fun getPluginSignals() = setOf(
        signalInfo<Dictionary>(Activity.OPEN_DOCUMENT),
        signalInfo<Dictionary>(Activity.READ_BYTES_ASYNC),
    )

    private inline fun <reified T : Any> signalInfo(activity: Activity,) =
        SignalInfo(activity.name.lowercase(), T::class.javaObjectType)

    private fun signal(activity: Activity, vararg args: Any) = signal(activity.name.lowercase(), *args)

    private val requests = mutableMapOf<Int, Activity>()

    /**
     * Register an activity to allow responding to it from onMainActivityResult
     */
    private fun getCode(activity: Activity) = getCode(2801).apply {
        requests[this] = activity
    }

    override fun onActivityCode(code: Int, result: Int, data: Intent?) {
        val activity = requests[code] ?: return
        when (activity) {
            Activity.OPEN_DOCUMENT -> open_document_response(code, result, data)
            else -> {}
        }
    }


    /**
     * Launches an Intent.ACTION_OPEN_DOCUMENT and returns the code that will be used in a future response.
     */
    @UsedByGodot
    @Suppress("FunctionName")
    fun open_document(persist: Boolean, mimeType: String): Int {
        val code = getCode(Activity.OPEN_DOCUMENT)
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

    /**
     * Emits OPEN_DOCUMENT with a code and a dictionary
     */
    @Suppress("FunctionName")
    private fun open_document_response(code: Int, result: Int, data: Intent?) {
        val dictionary = Dictionary().code(code)
        if (result != RESULT_OK) return signal(Activity.OPEN_DOCUMENT, dictionary.error("User cancelled operation"))

        val uri = data?.data ?: return signal(Activity.OPEN_DOCUMENT, dictionary.error("Could not get Intent's Uri"))
        val document = DocumentFile.fromSingleUri(activity!!, uri)!!

        if (data.getBooleanExtra("PERSIST", false)) uri.persist(activity!!)
        signal(Activity.OPEN_DOCUMENT, dictionary.data(document.toGodotDictionary()))
    }

    /**
     * Reads the contents of an uri while blocking the thread
     */
    @UsedByGodot
    @Suppress("FunctionName")
    fun read_bytes_sync(uri: String): Dictionary {
        val dictionary = Dictionary()
        val document = Uri.parse(uri).toDocumentFile(activity!!)
        lateinit var stream: InputStream

        val error = when {
            !document.exists() -> "File doesn't exist"
            !document.canRead() -> "File can't be read"
            else -> {
                try {
                    stream = activity!!.contentResolver.openInputStream(document.uri)!!
                    null
                } catch (e: Exception) {
                    e.message
                }
            }
        }

        if (error != null)
            return dictionary.error(error)
        return dictionary.data(stream.use { it.readBytes() })
    }

    /**
     * Return a code, then read bytes on the IO scope and signals with the code
     */
    @UsedByGodot
    @Suppress("FunctionName")
    fun read_bytes(uri: String): Int {
        val code = getCode(Activity.READ_BYTES_ASYNC)

        CoroutineScope(Dispatchers.IO).launch {
            val dictionary = Dictionary().code(code)
            val document = Uri.parse(uri).toDocumentFile(activity!!)
            lateinit var stream: InputStream

            val error = when {
                !document.exists() -> "File doesn't exist"
                !document.canRead() -> "File can't be read"
                else -> {
                    try {
                        stream = activity!!.contentResolver.openInputStream(document.uri)!!
                        null
                    } catch (e: Exception) {
                        e.message
                    }
                }
            }

            if (error != null) {
                delay(100) // Tiny delay so Godot gets the code before this signal
                signal(Activity.READ_BYTES_ASYNC, dictionary.error(error))
                return@launch
            }

            stream.use {
                signal(Activity.READ_BYTES_ASYNC, dictionary.data(it.readBytes()))
            }
        }

        return code
    }


}

