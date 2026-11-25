package com.campussync.app.data.repository

import com.campussync.app.data.model.College
import com.campussync.app.domain.repository.CollegeRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class FirestoreCollegeRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : CollegeRepository {

    override suspend fun getColleges(): List<College> {
        try {
            val snapshot = firestore.collection(COLLEGES).get().awaitResult()
            return snapshot.documents
                .mapNotNull { document -> document.toCollege() }
                .sortedBy { college -> college.name.lowercase() }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException(
                "We couldn't load the college list. Please try again."
            )
        }
    }

    private fun DocumentSnapshot.toCollege(): College? {
        val collegeName = getString("name") ?: return null
        return College(
            id = id,
            name = collegeName,
            emailDomains = (get("emailDomains") as? List<*>)
                ?.filterIsInstance<String>()
                .orEmpty(),
            createdAt = getTimestamp("createdAt")?.toDate()?.time
        )
    }

    private companion object {
        const val COLLEGES = "colleges"
    }
}
