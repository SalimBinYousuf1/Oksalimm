package com.example.data

import android.util.Log
import com.example.SalimApplication
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SignalingRepository {
    companion object {
        private const val TAG = "SignalingRepository"
        private const val COLLECTION_SESSIONS = "sessions"
        private const val COLLECTION_SIGNALING = "signaling"
        private const val SUBCOLLECTION_CALLER_CANDIDATES = "callerCandidates"
        private const val SUBCOLLECTION_CALLEE_CANDIDATES = "calleeCandidates"
    }

    private var activeDocRef: DocumentReference? = null
    private val activeRegistrations = mutableListOf<ListenerRegistration>()

    private fun getFirestore(): FirebaseFirestore? {
        return try {
            if (SalimApplication.isFirebaseConfigured) {
                FirebaseFirestore.getInstance()
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get Firestore instance: ${e.message}", e)
            null
        }
    }

    fun getSessionDoc(hostId: String): DocumentReference? {
        val firestore = getFirestore() ?: return null
        return firestore.collection(COLLECTION_SESSIONS).document(hostId)
    }

    suspend fun sendControllerPresence(hostId: String, online: Boolean) {
        try {
            val firestore = getFirestore() ?: return
            val data = hashMapOf<String, Any>(
                "controllerOnline" to online,
                "lastSeenController" to FieldValue.serverTimestamp()
            )
            firestore.collection(COLLECTION_SESSIONS).document(hostId).set(data, com.google.firebase.firestore.SetOptions.merge())
            firestore.collection(COLLECTION_SIGNALING).document(hostId).set(data, com.google.firebase.firestore.SetOptions.merge())
        } catch (e: Exception) {
            Log.w(TAG, "sendControllerPresence failed: ${e.message}")
        }
    }

    suspend fun sendOffer(hostId: String, sdp: SessionDescription) {
        try {
            val firestore = getFirestore() ?: return
            val offerMap = hashMapOf(
                "type" to sdp.type.canonicalForm(),
                "sdp" to sdp.description,
                "createdAt" to System.currentTimeMillis()
            )
            val sessionData = hashMapOf<String, Any>(
                "offer" to offerMap,
                "status" to "offer_created",
                "controllerOnline" to true,
                "lastSeenController" to FieldValue.serverTimestamp()
            )
            firestore.collection(COLLECTION_SESSIONS).document(hostId).set(sessionData, com.google.firebase.firestore.SetOptions.merge())
            firestore.collection(COLLECTION_SIGNALING).document(hostId).set(sessionData, com.google.firebase.firestore.SetOptions.merge())
            Log.i(TAG, "Sent offer to Firestore session $hostId")
        } catch (e: Exception) {
            Log.e(TAG, "sendOffer error: ${e.message}", e)
        }
    }

    suspend fun sendIceCandidate(hostId: String, candidate: IceCandidate) {
        try {
            val firestore = getFirestore() ?: return
            val candidateMap = hashMapOf(
                "sdpMid" to (candidate.sdpMid ?: ""),
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "sdp" to candidate.sdp,
                "serverUrl" to (candidate.serverUrl ?: "")
            )

            // Write to both subcollection and array for broad host compatibility
            firestore.collection(COLLECTION_SESSIONS).document(hostId)
                .collection(SUBCOLLECTION_CALLER_CANDIDATES)
                .add(candidateMap)

            firestore.collection(COLLECTION_SESSIONS).document(hostId)
                .update("controllerCandidates", FieldValue.arrayUnion(candidateMap))
        } catch (e: Exception) {
            Log.w(TAG, "sendIceCandidate error: ${e.message}")
        }
    }

    fun observeSession(hostId: String): Flow<Map<String, Any?>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }

        val mergedData = mutableMapOf<String, Any?>()

        val listener1 = firestore.collection(COLLECTION_SESSIONS).document(hostId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "observeSession (sessions) error: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                synchronized(mergedData) {
                    snapshot.data?.let { mergedData.putAll(it) }
                    trySend(mergedData.toMap())
                }
            }
        }
        activeRegistrations.add(listener1)

        val listener2 = firestore.collection(COLLECTION_SIGNALING).document(hostId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "observeSession (signaling) error: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                synchronized(mergedData) {
                    snapshot.data?.let { mergedData.putAll(it) }
                    trySend(mergedData.toMap())
                }
            }
        }
        activeRegistrations.add(listener2)

        awaitClose {
            listener1.remove()
            listener2.remove()
            activeRegistrations.remove(listener1)
            activeRegistrations.remove(listener2)
        }
    }

    fun observeHostCandidates(hostId: String): Flow<IceCandidate> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            close()
            return@callbackFlow
        }

        fun registerSubcollectionListener(collectionName: String): ListenerRegistration {
            return firestore.collection(collectionName).document(hostId)
                .collection(SUBCOLLECTION_CALLEE_CANDIDATES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.documentChanges?.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val data = change.document.data
                            val sdpMid = data["sdpMid"] as? String ?: ""
                            val sdpMLineIndex = (data["sdpMLineIndex"] as? Number)?.toInt() ?: 0
                            val sdp = data["sdp"] as? String ?: ""
                            if (sdp.isNotEmpty()) {
                                trySend(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                            }
                        }
                    }
                }
        }

        val reg1 = registerSubcollectionListener(COLLECTION_SESSIONS)
        val reg2 = registerSubcollectionListener(COLLECTION_SIGNALING)
        activeRegistrations.add(reg1)
        activeRegistrations.add(reg2)

        awaitClose {
            reg1.remove()
            reg2.remove()
            activeRegistrations.remove(reg1)
            activeRegistrations.remove(reg2)
        }
    }

    fun cleanup() {
        for (reg in activeRegistrations) {
            try {
                reg.remove()
            } catch (_: Exception) {}
        }
        activeRegistrations.clear()
        activeDocRef = null
    }
}
