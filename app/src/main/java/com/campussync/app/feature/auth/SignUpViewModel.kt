package com.campussync.app.feature.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.core.util.EmailDomainValidator
import com.campussync.app.data.local.SignupDraftStore
import com.campussync.app.data.model.College
import com.campussync.app.data.model.SignupDraft
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.CollegeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignUpUiState(
    val colleges: UiState<List<College>> = UiState.Loading,
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val selectedCollegeId: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null
) {
    val selectedCollege: College?
        get() = (colleges as? UiState.Success<List<College>>)
            ?.data
            ?.firstOrNull { it.id == selectedCollegeId }
}

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val collegeRepository: CollegeRepository,
    private val signupDraftStore: SignupDraftStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    init {
        loadColleges()
    }

    fun loadColleges() {
        viewModelScope.launch {
            _uiState.update { it.copy(colleges = UiState.Loading, error = null) }

            try {
                val colleges = collegeRepository.getColleges()
                _uiState.update { state ->
                    state.copy(
                        colleges = if (colleges.isEmpty()) {
                            UiState.Empty
                        } else {
                            UiState.Success(colleges)
                        },
                        selectedCollegeId = state.selectedCollegeId?.takeIf { selectedId ->
                            colleges.any { it.id == selectedId }
                        }
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        colleges = UiState.Error(
                            error.message ?: "We couldn't load colleges. Please try again."
                        )
                    )
                }
            }
        }
    }

    fun onFullNameChange(value: String) {
        _uiState.update { it.copy(fullName = value, error = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onCollegeSelected(college: College) {
        _uiState.update { it.copy(selectedCollegeId = college.id, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        val fullName = state.fullName.trim()
        val email = state.email.trim()
        val college = state.selectedCollege

        val validationError = when {
            fullName.isBlank() -> "Enter your full name."
            !email.contains("@") -> "Enter a valid email address."
            state.password.length < 6 -> "Use a password with at least 6 characters."
            college == null -> "Select your college."
            college.emailDomains.isEmpty() -> "This college isn't accepting sign-ups yet."
            !EmailDomainValidator.hasValidDomain(email, college.emailDomains) ->
                "Use your ${college.name} email address. Allowed domain(s): " +
                    college.emailDomains.joinToString()

            else -> null
        }

        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        val selectedCollege = college ?: return
        _uiState.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            try {
                val user = authRepository.signUp(fullName, email, state.password)

                try {
                    signupDraftStore.save(
                        SignupDraft(
                            uid = user.uid,
                            fullName = fullName,
                            collegeId = selectedCollege.id
                        )
                    )
                } catch (draftError: Exception) {
                    if (draftError is CancellationException) throw draftError
                    // Onboarding can ask for the college again if this local draft is lost.
                    Log.w(TAG, "Could not save the local sign-up draft", draftError)
                }

                _uiState.update { it.copy(isSubmitting = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        error = error.message
                            ?: "We couldn't create your account. Please try again."
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "SignUpViewModel"
    }
}
