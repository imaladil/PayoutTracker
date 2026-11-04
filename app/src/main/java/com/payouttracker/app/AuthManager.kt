package com.payouttracker.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class AuthUser(val uid: String, val email: String, val name: String) {
    val isAdmin: Boolean get() = email.equals(ADMIN_EMAIL, ignoreCase = true)
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun FirebaseUser.toAuthUser(): AuthUser {
    val mail = email ?: ""
    return AuthUser(uid, mail, displayName ?: mail.substringBefore("@"))
}

/**
 * Gmail (Google) sign-in with one active device per account.
 *
 * Every sign-in writes a new random "session id" to users/{uid}.activeSession. Each device keeps
 * the id it created and listens to that field; when another device signs in, the field changes
 * and this device signs itself out. An offline device is signed out as soon as it is online again.
 */
class AuthManager(private val ctx: Context, private val scope: CoroutineScope) {

    /** False when google-services.json was not part of the build. */
    val firebaseReady: Boolean = FirebaseApp.getApps(ctx).isNotEmpty()

    var user by mutableStateOf<AuthUser?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    /** Subscription facts about the signed-in person (trial start, paid period, termination). */
    var profile by mutableStateOf(Profile())
        private set

    /** Error shown on the login page. */
    var error by mutableStateOf<String?>(null)

    /** True for a few seconds after a fresh sign-in; the home screen shows a welcome strip. */
    var welcome by mutableStateOf(false)

    /** One-time notice shown in a dialog, for example "signed in on another device". */
    var notice by mutableStateOf<String?>(null)

    private val prefs = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private var registration: ListenerRegistration? = null

    init {
        if (firebaseReady) {
            val current = FirebaseAuth.getInstance().currentUser
            val session = prefs.getString("session", null)
            if (current != null && session != null) {
                user = current.toAuthUser()
                watchSession(current.uid, session)
            } else if (current != null) {
                FirebaseAuth.getInstance().signOut()
            }
        }
    }

    private fun watchSession(uid: String, session: String) {
        registration?.remove()
        registration = FirebaseFirestore.getInstance().collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener
                profile = profileOf(snap)
                if (snap.metadata.hasPendingWrites()) return@addSnapshotListener
                val active = snap.getString("activeSession")
                if (active != null && active != session) {
                    notice = "You were signed out because this account was opened on another device."
                    clearLocal()
                }
            }
    }

    private fun clearLocal() {
        registration?.remove()
        registration = null
        prefs.edit().remove("session").apply()
        if (firebaseReady) FirebaseAuth.getInstance().signOut()
        user = null
        welcome = false
        profile = Profile()
        scope.launch {
            try {
                CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                // nothing to clear
            }
        }
    }

    fun logout() {
        clearLocal()
    }

    fun signIn(activity: Activity) {
        if (busy) return
        error = null
        if (!firebaseReady) {
            error = "Online features are not set up in this build yet."
            return
        }
        val resId = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        if (resId == 0) {
            error = "Google sign-in is not configured. Enable Google sign-in in Firebase, then download google-services.json again."
            return
        }
        busy = true
        scope.launch {
            try {
                // The "Sign in with Google" flow always shows the account picker (with "add account"),
                // for new and returning people alike.
                val option = GetSignInWithGoogleOption.Builder(ctx.getString(resId)).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = CredentialManager.create(activity).getCredential(activity, request)
                val cred = result.credential
                if (cred !is CustomCredential ||
                    cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    throw IllegalStateException("Unexpected sign-in response.")
                }
                val google = GoogleIdTokenCredential.createFrom(cred.data)
                val auth = FirebaseAuth.getInstance()
                val fbUser = auth
                    .signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
                    .await().user ?: throw IllegalStateException("Sign-in failed.")
                val session = UUID.randomUUID().toString()
                withTimeout(20000) { registerSession(fbUser, session) }
                prefs.edit().putString("session", session).apply()
                user = fbUser.toAuthUser()
                welcome = true
                watchSession(fbUser.uid, session)
            } catch (e: GetCredentialCancellationException) {
                // the person closed the account picker
            } catch (e: GetCredentialException) {
                error = "Google sign-in failed. " + (e.message ?: "")
            } catch (e: Exception) {
                if (firebaseReady) FirebaseAuth.getInstance().signOut()
                error = "Could not sign in. Check your internet connection and try again."
            } finally {
                busy = false
            }
        }
    }

    private suspend fun registerSession(u: FirebaseUser, session: String) {
        val ref = FirebaseFirestore.getInstance().collection("users").document(u.uid)
        val existing = ref.get().await()
        val mail = u.email ?: ""
        val name = u.displayName ?: mail.substringBefore("@")
        val data = hashMapOf<String, Any>(
            "email" to mail,
            "name" to name,
            "activeSession" to session,
            "lastLoginAt" to FieldValue.serverTimestamp(),
            "device" to (Build.MODEL ?: "")
        )
        if (!existing.exists()) {
            data["createdAt"] = FieldValue.serverTimestamp()
            ref.set(data).await()
        } else {
            ref.update(data).await()
        }
    }
}
