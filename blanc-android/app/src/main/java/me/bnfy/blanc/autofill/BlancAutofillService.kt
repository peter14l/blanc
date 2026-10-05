package me.bnfy.blanc.autofill

import android.app.assist.AssistStructure
import android.os.Bundle
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

    override fun onFillRequest(request: FillRequest, callback: FillCallback) {
        scope.launch {
            try {
                // Get the structure to analyze fields
                val structure = request.fillContexts[0].structure
                
                // Find username/email and password fields
                val usernameField = findField(structure, "username", "email", "user", "login")
                val passwordField = findField(structure, "password", "pass")
                
                if (usernameField != null && passwordField != null) {
                    // Query credentials for the domain
                    val url = extractUrlFromStructure(structure)
                    val credentials = getCredentialsForUrl(url)
                    
                    credentials?.let { (username, password) ->
                        val response = FillResponse.Builder()
                            .addDataset(
                                android.service.autofill.Dataset.Builder()
                                    .setValue(usernameField.autofillId, AutofillValue.forText(username))
                                    .setValue(passwordField.autofillId, AutofillValue.forText(password))
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
                // Store the save request for later processing
                currentSaveRequest = request
                
                // Extract form data
                val structure = request.fillContexts[0].structure
                val url = extractUrlFromStructure(structure)
                
                val usernameField = findField(structure, "username", "email", "user", "login")
                val passwordField = findField(structure, "password", "pass")
                
                if (usernameField != null && passwordField != null) {
                    val username = getFieldValue(request, usernameField.autofillId)
                    val password = getFieldValue(request, passwordField.autofillId)
                    
                    if (username.isNotBlank() && password.isNotBlank()) {
                        // Save credentials
                        saveCredentials(url, username, password)
                    }
                }
                
                callback.onSuccess(null)
            } catch (e: Exception) {
                Log.e("BlancAutofill", "Save request failed", e)
                callback.onSuccess(null)
            }
        }
    }

    private fun findField(structure: AssistStructure, vararg hints: String): AssistStructure.ViewNode? {
        // Recursively search for fields matching hints
        return findFieldRecursive(structure, hints)
    }

    private fun findFieldRecursive(node: AssistStructure.ViewNode, hints: Array<String>): AssistStructure.ViewNode? {
        val text = node.className.toString().lowercase()
        val hint = node.hints?.joinToString(" ").lowercase() ?: ""
        val id = node.idEntry?.lowercase() ?: ""
        
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
        // Try to get URL from web domain
        for (i in 0 until structure.childCount) {
            val child = structure.getChildAt(i)
            val webDomain = child.webDomain
            if (webDomain.isNotBlank()) {
                return webDomain
            }
        }
        return ""
    }

    private fun getFieldValue(request: FillRequest, autofillId: AutofillId): String {
        return request.fillContexts[0].structure.findViewNodeByAutofillId(autofillId)
            ?.autofillValue?.textValue?.toString() ?: ""
    }

    private fun getCredentialsForUrl(url: String): Pair<String, String>? {
        // Query repository for saved credentials
        // This would need a credentials DAO in the repository
        return null // Placeholder
    }

    private fun saveCredentials(url: String, username: String, password: String) {
        // Save to repository
        // This would need a credentials DAO in the repository
    }
}