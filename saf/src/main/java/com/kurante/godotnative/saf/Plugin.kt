package com.kurante.godotnative.saf

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kurante.godotnative.core.NativePlugin
import com.kurante.godotnative.core.extensions.code
import com.kurante.godotnative.core.extensions.data
import com.kurante.godotnative.core.extensions.error
import com.kurante.godotnative.saf.extensions.isTree
import com.kurante.godotnative.saf.extensions.persist
import com.kurante.godotnative.core.extensions.toDictionary
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
    companion object {
        const val RESULT_SIGNAL = "result"
    }
    enum class Activity {
        NONE, OPEN_DOCUMENT, OPEN_DOCUMENT_TREE
    }

    override fun getPluginSignals() = setOf(
        SignalInfo(RESULT_SIGNAL, Dictionary::class.javaObjectType)
    )

    private fun signalResult(result: Dictionary) = emitSignal(RESULT_SIGNAL, result)


    private val requests = mutableMapOf<Int, Activity>()

    /**
     * Register an activity to allow responding to it from onMainActivityResult
     */
    private fun getCode(activity: Activity) = getCode(2801).apply {
        requests[this] = activity
    }

    override fun onActivityCode(code: Int, result: Int, data: Intent?) {
        val activity = requests[code] ?: return
        requests.remove(code)

        when (activity) {
            Activity.OPEN_DOCUMENT -> open_document_response(code, result, data)
            Activity.OPEN_DOCUMENT_TREE -> open_document_tree_response(code, result, data)
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
        if (result != RESULT_OK) return signalResult(dictionary.error("User cancelled operation"))

        val uri = data?.data ?: return signalResult(dictionary.error("Could not get Intent's Uri"))
        val document = DocumentFile.fromSingleUri(activity!!, uri)!!

        if (data.getBooleanExtra("PERSIST", false)) uri.persist(activity!!)
        signalResult(dictionary.data(document.toGodotDictionary(false)))
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
     * Returns a code, then reads bytes on the IO scope and signals with the code
     */
    @UsedByGodot
    @Suppress("FunctionName")
    fun read_bytes(uri: String): Int {
        val code = getCode(Activity.NONE)

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
                signalResult(dictionary.error(error))
                requests.remove(code)
                return@launch
            }

            stream.use {
                signalResult(dictionary.data(it.readBytes()))
            }
            requests.remove(code)
        }

        return code
    }

    /**
     * Launches an Intent.ACTION_OPEN_DOCUMENT_TREE and returns the code that will be used in a future response.
     */
    @UsedByGodot
    @Suppress("FunctionName")
    fun open_document_tree(persist: Boolean): Int {
        val code = getCode(Activity.OPEN_DOCUMENT_TREE)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
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
     * Emits OPEN_DOCUMENT_TREE with a code and a dictionary
     */
    @Suppress("FunctionName")
    private fun open_document_tree_response(code: Int, result: Int, data: Intent?) {
        val dictionary = Dictionary().code(code)
        if (result != RESULT_OK) return signalResult(dictionary.error("User cancelled operation"))

        val uri = data?.data ?: return signalResult(dictionary.error("Could not get Intent's Uri"))
        val document = DocumentFile.fromTreeUri(activity!!, uri)!!

        if (data.getBooleanExtra("PERSIST", false)) uri.persist(activity!!)
        signalResult(dictionary.data(document.toGodotDictionary(true)))
    }

    /**
     * Returns a code, then reads DocumentFile's children and signals with the code
     */
    @UsedByGodot
    @Suppress("FunctionName")
    fun list_files(uri: String): Int {
        val code = getCode(Activity.NONE)

        CoroutineScope(Dispatchers.IO).launch {
            val dictionary = Dictionary().code(code)
            val document = Uri.parse(uri).toDocumentFile(activity!!)
            lateinit var children: Array<DocumentFile>

            val error = when {
                !document.exists() -> "Directory doesn't exist"
                !document.canRead() -> "Directory can't be read"
                !document.isDirectory -> "Document is not a directory"
                !document.uri.isTree -> "Document is a single uri and not a tree uri"
                else -> {
                    try  {
                        // This call is the reason the entire function is async, listFiles is SLOW
                        children = document.listFiles()
                        null
                    } catch (e: Exception) {
                        e.message
                    }
                }
            }

            if (error != null) {
                delay(100)
                signalResult(dictionary.error(error))
                requests.remove(code)
                return@launch
            }

            val data = children.map { it.toGodotDictionary(true) }.toTypedArray()
            signalResult(dictionary.data(data.toDictionary()))

            requests.remove(code)
        }

        return code
    }

    /**
     * Returns children of a DocumentFile tree (not recursive), slow
     */
    @UsedByGodot
    @Suppress("FunctionName")
    fun list_files_sync(uri: String): Dictionary {
        val dictionary = Dictionary()
        val document = Uri.parse(uri).toDocumentFile(activity!!)
        lateinit var children: Array<DocumentFile>

        val error = when {
            !document.exists() -> "Directory doesn't exist"
            !document.canRead() -> "Directory can't be read"
            !document.isDirectory -> "Document is not a directory"
            !document.uri.isTree -> "Document is a single uri and not a tree uri"
            else -> {
                try  {
                    children = document.listFiles()
                    null
                } catch (e: Exception) {
                    e.message
                }
            }
        }

        if (error != null)
            return dictionary.error(error)
        val data = children.map { it.toGodotDictionary(true) }.toTypedArray()
        return dictionary.data(data.toDictionary())
    }
}

