package com.campussync.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.campussync.app.data.model.SignupDraft
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.signupDraftDataStore by preferencesDataStore(name = "signup_draft")

/**
 * Tiny local hand-off between sign-up and onboarding.
 *
 * The profile document cannot exist until onboarding is finished, so the chosen
 * college is kept here in the meantime. Onboarding also has a fallback when this
 * draft is missing (for example, when the account is used on another device).
 */
@Singleton
class SignupDraftStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private object Keys {
        val uid = stringPreferencesKey("uid")
        val fullName = stringPreferencesKey("full_name")
        val collegeId = stringPreferencesKey("college_id")
    }

    suspend fun save(draft: SignupDraft) {
        context.signupDraftDataStore.edit { preferences ->
            preferences[Keys.uid] = draft.uid
            preferences[Keys.fullName] = draft.fullName
            preferences[Keys.collegeId] = draft.collegeId
        }
    }

    suspend fun get(uid: String): SignupDraft? {
        val preferences = context.signupDraftDataStore.data.first()
        if (preferences[Keys.uid] != uid) return null

        val fullName = preferences[Keys.fullName] ?: return null
        val collegeId = preferences[Keys.collegeId] ?: return null
        return SignupDraft(uid = uid, fullName = fullName, collegeId = collegeId)
    }

    suspend fun clear() {
        context.signupDraftDataStore.edit { preferences -> preferences.clear() }
    }
}
