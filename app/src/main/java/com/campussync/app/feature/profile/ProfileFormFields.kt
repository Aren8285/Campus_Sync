package com.campussync.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.campussync.app.core.ui.components.DropdownField
import com.campussync.app.core.ui.components.TagChip
import com.campussync.app.core.util.ProfileTextParser

const val MAX_BIO_LENGTH = 300
const val MAX_AVATAR_URL_LENGTH = 500

private val StudyYears = listOf(
    "1st year",
    "2nd year",
    "3rd year",
    "4th year",
    "5th year",
    "Graduate"
)

private val InterestOptions = listOf(
    "Sports",
    "Music",
    "Technology",
    "Art",
    "Gaming",
    "Entrepreneurship",
    "Volunteering",
    "Travel",
    "Reading",
    "Film",
    "Fitness",
    "Cooking"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileFormFields(
    name: String,
    onNameChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    department: String,
    onDepartmentChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit,
    graduationYear: String,
    onGraduationYearChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit,
    skillsText: String,
    onSkillsChange: (String) -> Unit,
    selectedInterests: Set<String>,
    onToggleInterest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val yearOptions = (StudyYears + year).filter { it.isNotBlank() }.distinct()
    val interestOptions = (InterestOptions + selectedInterests).distinct()
    val parsedSkills = ProfileTextParser.parseSkills(skillsText)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(text = "Full name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(text = "Username") },
            supportingText = {
                Text(text = "3-20 lowercase letters, numbers, or underscores.")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = department,
            onValueChange = onDepartmentChange,
            label = { Text(text = "Department") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        DropdownField(
            label = "Year of study",
            placeholder = "Select your year",
            options = yearOptions,
            selected = year.takeIf { it.isNotBlank() },
            optionLabel = { it },
            onSelected = onYearChange
        )

        OutlinedTextField(
            value = graduationYear,
            onValueChange = onGraduationYearChange,
            label = { Text(text = "Graduation year") },
            supportingText = { Text(text = "For example, 2028.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = bio,
            onValueChange = onBioChange,
            label = { Text(text = "Bio (optional)") },
            supportingText = { Text(text = "${bio.length}/$MAX_BIO_LENGTH characters") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5
        )

        OutlinedTextField(
            value = skillsText,
            onValueChange = onSkillsChange,
            label = { Text(text = "Skills") },
            supportingText = { Text(text = "Separate skills with commas.") },
            modifier = Modifier.fillMaxWidth()
        )

        if (parsedSkills.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                parsedSkills.forEach { skill ->
                    TagChip(text = skill)
                }
            }
        }

        Text(
            text = "Interests",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            interestOptions.forEach { interest ->
                FilterChip(
                    selected = interest in selectedInterests,
                    onClick = { onToggleInterest(interest) },
                    label = { Text(text = interest) }
                )
            }
        }
    }
}
