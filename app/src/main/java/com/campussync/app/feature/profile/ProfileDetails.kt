package com.campussync.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campussync.app.core.ui.components.ProfileAvatar
import com.campussync.app.core.ui.components.TagChip
import com.campussync.app.data.model.UserProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileDetails(
    profile: UserProfile,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProfileAvatar(
                name = profile.name,
                avatarUrl = profile.avatarUrl,
                size = 80.dp
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = profile.name.ifBlank { "Student" },
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = if (profile.username.isBlank()) {
                        "@username"
                    } else {
                        "@${profile.username}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        val studyInfo = listOf(profile.department, profile.year)
            .filter { it.isNotBlank() }
            .joinToString(separator = " • ")

        if (studyInfo.isNotBlank()) {
            Text(
                text = studyInfo,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (profile.graduationYear > 0) {
            Text(
                text = "Class of ${profile.graduationYear}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (profile.bio.isNotBlank()) {
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start
            )
        }

        if (profile.skills.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Skills",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.skills.forEach { skill ->
                        TagChip(text = skill)
                    }
                }
            }
        }

        if (profile.interests.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Interests",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.interests.forEach { interest ->
                        TagChip(text = interest)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            ProfileStat(label = "Karma", value = profile.karma.toString())
            ProfileStat(label = "Badges", value = profile.badges.size.toString())
            ProfileStat(
                label = "Role",
                value = profile.role.replaceFirstChar { it.uppercase() }
            )
        }
    }
}

@Composable
private fun ProfileStat(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
