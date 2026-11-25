package com.campussync.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.core.util.ProfileTextParser
import com.campussync.app.core.util.UsernameValidator
import com.campussync.app.data.model.ProfileUpdate
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val profileState: UiState<UserProfile> = UiState.Loading,
    val name: String = "",
    val username: String = "",
    val department: String = "",
    val year: String = "",
    val graduationYear: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val skillsText: String = "",
    val selectedInterests: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        val uid = authRepository.currentUser()?.uid
        if (uid == null) {
            _uiState.update {
                it.copy(profileState = UiState.Error("You're signed out. Please log in again."))
            }
            return
        }

        _uiState.update { it.copy(profileState = UiState.Loading, error = null) }

        viewModelScope.launch {
            try {
                val profile = userRepository.getUser(uid)
                if (profile == null) {
                    _uiState.update { it.copy(profileState = UiState.Empty) }
                } else {
                    _uiState.update {
                        it.copy(
                            profileState = UiState.Success(profile),
                            name = profile.name,
                            username = profile.username,
                            department = profile.department,
                            year = profile.year,
                            graduationYear = profile.graduationYear.toString(),
                            bio = profile.bio,
                            avatarUrl = profile.avatarUrl,
                            skillsText = profile.skills.joinToString(", "),
                            selectedInterests = profile.interests.toSet(),
                            error = null
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        profileState = UiState.Error(
                            error.message ?: "We couldn't load your profile."
                        )
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, error = null) }
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

    fun onAvatarUrlChange(value: String) {
        if (value.length > MAX_AVATAR_URL_LENGTH) return
        _uiState.update { it.copy(avatarUrl = value, error = null) }
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

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val current = (state.profileState as? UiState.Success<UserProfile>)?.data
        if (current == null) {
            _uiState.update { it.copy(error = "Your profile isn't loaded yet.") }
            return
        }

        val name = state.name.trim()
        val username = UsernameValidator.normalize(state.username)
        val usernameError = UsernameValidator.validationError(state.username)
        val graduationYear = state.graduationYear.toIntOrNull()
        val avatarUrl = state.avatarUrl.trim()

        val validationError = when {
            name.isBlank() -> "Enter your full name."
            usernameError != null -> usernameError
            state.department.isBlank() -> "Enter your department."
            state.year.isBlank() -> "Select your year of study."
            graduationYear == null || graduationYear !in 2000..2100 ->
                "Enter a valid four-digit graduation year."

            avatarUrl.isNotEmpty() && !avatarUrl.startsWith("https://") ->
                "Profile photo links must start with https://."

            else -> null
        }

        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        val validGraduationYear = graduationYear ?: return
        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                if (username != current.username &&
                    !userRepository.isUsernameAvailable(username)
                ) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = "That username is already taken. Please choose another."
                        )
                    }
                    return@launch
                }

                userRepository.updateProfile(
                    current = current,
                    update = ProfileUpdate(
                        name = name,
                        username = username,
                        department = state.department.trim(),
                        year = state.year,
                        graduationYear = validGraduationYear,
                        bio = state.bio.trim(),
                        avatarUrl = avatarUrl,
                        skills = ProfileTextParser.parseSkills(state.skillsText),
                        interests = state.selectedInterests.sorted()
                    )
                )

                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = error.message
                            ?: "We couldn't save your changes. Please try again."
                    )
                }
            }
        }
    }
}
