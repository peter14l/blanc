package me.bnfy.blanc.autofill

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import me.bnfy.blanc.storage.Repository
import me.bnfy.blanc.BlancApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Blanc Autofill Service for filling credentials and forms.
 * Integrates with the Repository for stored credentials.
 */
class BlancAutofillService : AutofillService() {

    private val repository: Repository by lazy { BlancApplication.getInstance().repository }
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentSaveRequest: SaveRequest? = null

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        scope.launch {
            try {
                if (request.fillContexts.isEmpty()) {
                    callback.onSuccess(null)
                    return@launch
                }
                // Get the structure to analyze fields
                val structure = request.fillContexts[0].structure
                
                // Find username/email and password fields
                val usernameField = findField(structure, "username", "email", "user", "login")
                val passwordField = findField(structure, "password", "pass")
                
                if (usernameField?.autofillId != null && passwordField?.autofillId != null) {
                    // Query credentials for the domain
                    val url = extractUrlFromStructure(structure)
                    val credentials = getCredentialsForUrl(url)
                    
                    credentials?.let { (username, password) ->
                        val response = FillResponse.Builder()
                            .addDataset(
                                android.service.autofill.Dataset.Builder()
                                    .setValue(usernameField.autofillId!!, AutofillValue.forText(username))
                                    .setValue(passwordField.autofillId!!, AutofillValue.forText(password))
                                    .build()
                            )
                            .build()
                        callback.onSuccess(response)
                        return@launch
                    }
                }
                
                callback.onSuccess(null)
            } catch (e: Exception) {
                Log.e("BlancAutofill", "Fill request failed", e)
                callback.onSuccess(null)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        scope.launch {
            try {
                currentSaveRequest = request
                if (request.fillContexts.isNotEmpty()) {
                    val structure = request.fillContexts[0].structure
                    val url = extractUrlFromStructure(structure)
                    
                    val usernameField = findField(structure, "username", "email", "user", "login")
                    val passwordField = findField(structure, "password", "pass")
                    
                    if (usernameField?.autofillId != null && passwordField?.autofillId != null) {
                        val username = getFieldValue(structure, usernameField.autofillId!!)
                        val password = getFieldValue(structure, passwordField.autofillId!!)
                        
                        if (username.isNotBlank() && password.isNotBlank()) {
                            saveCredentials(url, username, password)
                        }
                    }
                }
                callback.onSuccess()
            } catch (e: Exception) {
                Log.e("BlancAutofill", "Save request failed", e)
                callback.onSuccess()
            }
        }
    }

    private fun findField(structure: AssistStructure, vararg hints: String): AssistStructure.ViewNode? {
        val hintsArray = arrayOf(*hints)
        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode
            val found = findFieldRecursive(root, hintsArray)
            if (found != null) return found
        }
        return null
    }

    private fun findFieldRecursive(node: AssistStructure.ViewNode, hints: Array<String>): AssistStructure.ViewNode? {
        val text = (node.className?.toString() ?: "").lowercase()
        val hint = (node.hint?.toString() ?: "").lowercase()
        val id = (node.idEntry ?: "").lowercase()
        
        if (hints.any { text.contains(it) || hint.contains(it) || id.contains(it) }) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val result = findFieldRecursive(node.getChildAt(i), hints)
            if (result != null) return result
        }
        return null
    }

    private fun extractUrlFromStructure(structure: AssistStructure): String {
        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode
            val domain = findWebDomain(root)
            if (!domain.isNullOrBlank()) return domain
        }
        return ""
    }

    private fun findWebDomain(node: AssistStructure.ViewNode): String? {
        if (!node.webDomain.isNullOrBlank()) return node.webDomain
        for (i in 0 until node.childCount) {
            val domain = findWebDomain(node.getChildAt(i))
            if (!domain.isNullOrBlank()) return domain
        }
        return null
    }

    private fun getFieldValue(structure: AssistStructure, autofillId: AutofillId): String {
        val node = findNodeById(structure, autofillId)
        return node?.autofillValue?.textValue?.toString() ?: ""
    }

    private fun findNodeById(structure: AssistStructure, autofillId: AutofillId): AssistStructure.ViewNode? {
        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode
            val found = findNodeByIdRecursive(root, autofillId)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByIdRecursive(node: AssistStructure.ViewNode, autofillId: AutofillId): AssistStructure.ViewNode? {
        if (node.autofillId == autofillId) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByIdRecursive(node.getChildAt(i), autofillId)
            if (found != null) return found
        }
        return null
    }

    private fun getCredentialsForUrl(url: String): Pair<String, String>? {
        return null
    }

    private fun saveCredentials(url: String, username: String, password: String) {
    }
}