package com.campussync.app.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Home(route = "home", label = "Home", icon = Icons.Outlined.Home),
    Events(route = "events", label = "Events", icon = Icons.Outlined.Event),
    Learn(route = "learn", label = "Learn", icon = Icons.Outlined.School),
    Market(route = "market", label = "Market", icon = Icons.Outlined.Storefront),
    Profile(route = "profile", label = "Profile", icon = Icons.Outlined.Person)
}
