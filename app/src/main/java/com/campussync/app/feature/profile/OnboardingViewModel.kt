package com.campussync.app.feature.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.core.util.EmailDomainValidator
import com.campussync.app.core.util.ProfileTextParser
import com.campussync.app.core.util.UsernameValidator
import com.campussync.app.data.local.SignupDraftStore
import com.campussync.app.data.model.College
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.CollegeRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val colleges: UiState<List<College>> = UiState.Loading,
    val name: String = "",
    val collegeId: String = "",
    val collegeName: String = "",
    val canChooseCollege: Boolean = false,
    val username: String = "",
    val department: String = "",
    val year: String = "",
    val graduationYear: String = "",
    val bio: String = "",
    val skillsText: String = "",
    val selectedInterests: Set<String> = emptySet(),
    val isSubmitting: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val collegeRepository: CollegeRepository,
    private val userRepository: UserRepository,
    private val signupDraftStore: SignupDraftStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val user = authRepository.currentUser()
            if (user == null) {
                _uiState.update {
                    it.copy(
                        colleges = UiState.Error("You're signed out. Please log in again."),
                        error = "You're signed out. Please log in again."
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(colleges = UiState.Loading, error = null) }

            val draft = try {
                signupDraftStore.get(user.uid)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Could not read the local sign-up draft", error)
                null
            }

            val name = draft?.fullName?.takeIf { it.isNotBlank() }
                ?: user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@").orEmpty()

            try {
                val colleges = collegeRepository.getColleges()
                val draftCollege = draft?.collegeId
                    ?.let { id -> colleges.firstOrNull { it.id == id } }
                val draftMatchesEmail = draftCollege != null &&
                    user.email != null &&
                    EmailDomainValidator.hasValidDomain(
                        email = user.email,
                        allowedDomains = draftCollege.emailDomains
                    )
                val inferredCollege = inferCollegeFromEmail(user.email, colleges)
                val matchedCollege = if (draftMatchesEmail) draftCollege else inferredCollege

                _uiState.update {
                    it.copy(
                        colleges = if (colleges.isEmpty()) {
                            UiState.Empty
                        } else {
                            UiState.Success(colleges)
                        },
                        name = name,
                        collegeId = matchedCollege?.id.orEmpty(),
                        collegeName = matchedCollege?.name.orEmpty(),
                        canChooseCollege = matchedCollege == null,
                        error = null
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        colleges = UiState.Error(
                            error.message ?: "We couldn't load colleges. Please try again."
                        ),
                        name = name
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, error = null) }
    }

    fun selectCollege(college: College) {
        _uiState.update {
            it.copy(
                collegeId = college.id,
                collegeName = college.name,
                error = null
            )
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, error = null) }
    }

    fun onDepartmentChange(value: String) {
        _uiState.update { it.copy(department = value, error = null) }
    }

    fun onYearChange(value: String) {
        _uiState.update { it.copy(year = value, error = null) }
    }

    fun onGraduationYearChange(value: String) {
        if (value.all { it.isDigit() } && value.length <= 4) {
            _uiState.update { it.copy(graduationYear = value, error = null) }
        }
    }

    fun onBioChange(value: String) {
        if (value.length > MAX_BIO_LENGTH) return
        _uiState.update { it.copy(bio = value, error = null) }
    }

    fun onSkillsChange(value: String) {
        _uiState.update { it.copy(skillsText = value, error = null) }
    }

    fun toggleInterest(interest: String) {
        _uiState.update { state ->
            val selected = state.selectedInterests.toMutableSet()
            if (!selected.add(interest)) {
                selected.remove(interest)
            }
            state.copy(selectedInterests = selected, error = null)
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        val name = state.name.trim()
        val username = UsernameValidator.normalize(state.username)
        val usernameError = UsernameValidator.validationError(state.username)
        val graduationYear = state.graduationYear.toIntOrNull()
        val user = authRepository.currentUser()
        val email = user?.email
        val colleges = (state.colleges as? UiState.Success<List<College>>)?.data.orEmpty()
        val selectedCollege = colleges.firstOrNull { it.id == state.collegeId }

        val validationError = when {
            name.isBlank() -> "Enter your full name."
            usernameError != null -> usernameError
            user == null -> "You're signed out. Please log in again."
            selectedCollege == null -> "Select your college."
            selectedCollege.emailDomains.isEmpty() ->
                "This college isn't accepting sign-ups yet."

            email == null || !EmailDomainValidator.hasValidDomain(
                email = email,
                allowedDomains = selectedCollege.emailDomains
            ) -> "Use the college email address you signed up with."

            state.department.isBlank() -> "Enter your department."
            state.year.isBlank() -> "Select your year of study."
            graduationYear == null || graduationYear !in 2000..2100 ->
                "Enter a valid four-digit graduation year."

            else -> null
        }

        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        val validGraduationYear = graduationYear ?: return
        val signedInUser = user ?: return
        _uiState.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            try {
                if (!userRepository.isUsernameAvailable(username)) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = "That username is already taken. Please choose another."
                        )
                    }
                    return@launch
                }

                userRepository.createProfile(
                    UserProfile(
                        uid = signedInUser.uid,
                        name = name,
                        username = username,
                        collegeId = state.collegeId,
                        department = state.department.trim(),
                        year = state.year,
                        graduationYear = validGraduationYear,
                        bio = state.bio.trim(),
                        avatarUrl = "",
                        skills = ProfileTextParser.parseSkills(state.skillsText),
                        interests = state.selectedInterests.sorted(),
                        role = "student",
                        badges = emptyList(),
                        karma = 0L
                    )
                )

                try {
                    signupDraftStore.clear()
                } catch (draftError: Exception) {
                    if (draftError is CancellationException) throw draftError
                    Log.w(TAG, "Could not clear the local sign-up draft", draftError)
                }

                _uiState.update { it.copy(isSubmitting = false, isComplete = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        error = error.message
                            ?: "We couldn't save your profile. Please try again."
                    )
                }
            }
        }
    }

    private fun inferCollegeFromEmail(
        email: String?,
        colleges: List<College>
    ): College? {
        val domain = email?.substringAfterLast("@", "")?.lowercase().orEmpty()
        if (domain.isBlank()) return null

        return colleges
            .filter { college ->
                college.emailDomains.any { allowed ->
                    EmailDomainValidator.normalizeDomain(allowed) == domain
                }
            }
            .singleOrNull()
    }

    private companion object {
        const val TAG = "OnboardingViewModel"
    }
}
