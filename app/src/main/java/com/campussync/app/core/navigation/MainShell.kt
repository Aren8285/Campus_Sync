package com.campussync.app.core.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.campussync.app.core.notifications.RegisterForPushNotifications
import com.campussync.app.core.ui.components.PlaceholderScreen
import com.campussync.app.feature.boards.BoardThreadsScreen
import com.campussync.app.feature.boards.BoardsScreen
import com.campussync.app.feature.boards.CreateThreadScreen
import com.campussync.app.feature.boards.ThreadDetailScreen
import com.campussync.app.feature.chat.ChatListScreen
import com.campussync.app.feature.chat.ChatThreadScreen
import com.campussync.app.feature.chat.NewChatScreen
import com.campussync.app.feature.feed.CreatePostScreen
import com.campussync.app.feature.feed.CreateStoryScreen
import com.campussync.app.feature.feed.FeedScreen
import com.campussync.app.feature.feed.PostDetailScreen
import com.campussync.app.feature.feed.StoryViewerScreen
import com.campussync.app.feature.profile.EditProfileScreen
import com.campussync.app.feature.profile.ProfileScreen
import com.campussync.app.feature.profile.PublicProfileScreen

@Composable
fun MainShell(
    initialChatId: String? = null,
    onInitialChatHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    RegisterForPushNotifications()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    // A message notification opens the conversation it belongs to.
    LaunchedEffect(initialChatId) {
        val chatId = initialChatId
        if (!chatId.isNullOrBlank()) {
            navController.navigate(Routes.chatThread(chatId))
            onInitialChatHandled()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label
                                    )
                                },
                                label = { Text(text = destination.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(TopLevelDestination.Home.route) {
                FeedScreen(
                    onCreatePost = { navController.navigate(Routes.CreatePost) },
                    onOpenPost = { postId ->
                        navController.navigate(Routes.postDetail(postId))
                    },
                    onOpenProfile = { uid ->
                        navController.navigate(Routes.publicProfile(uid))
                    },
                    onAddStory = { navController.navigate(Routes.CreateStory) },
                    onOpenStories = { uid ->
                        navController.navigate(Routes.storyViewer(uid))
                    },
                    onOpenChats = { navController.navigate(Routes.Chats) },
                    onOpenBoards = { navController.navigate(Routes.Boards) }
                )
            }

            composable(TopLevelDestination.Events.route) {
                PlaceholderScreen(title = "Events")
            }

            composable(TopLevelDestination.Learn.route) {
                PlaceholderScreen(title = "Learn")
            }

            composable(TopLevelDestination.Market.route) {
                PlaceholderScreen(title = "Market")
            }

            composable(TopLevelDestination.Profile.route) {
                ProfileScreen(
                    onEdit = { navController.navigate(Routes.ProfileEdit) },
                    onViewPublicProfile = { uid ->
                        navController.navigate(Routes.publicProfile(uid))
                    },
                    onOpenChats = { navController.navigate(Routes.Chats) }
                )
            }

            composable(Routes.ProfileEdit) {
                EditProfileScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.PublicProfile,
                arguments = listOf(
                    navArgument(Routes.PublicProfileArg) {
                        type = NavType.StringType
                    }
                )
            ) {
                PublicProfileScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { chatId ->
                        navController.navigate(Routes.chatThread(chatId))
                    }
                )
            }

            composable(Routes.CreatePost) {
                CreatePostScreen(
                    onBack = { navController.popBackStack() },
                    onPosted = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.PostDetail,
                arguments = listOf(
                    navArgument(Routes.PostIdArg) {
                        type = NavType.StringType
                    }
                )
            ) {
                PostDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { uid ->
                        navController.navigate(Routes.publicProfile(uid))
                    }
                )
            }

            composable(Routes.CreateStory) {
                CreateStoryScreen(
                    onBack = { navController.popBackStack() },
                    onPosted = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.StoryViewer,
                arguments = listOf(
                    navArgument(Routes.StoryUidArg) {
                        type = NavType.StringType
                    }
                )
            ) {
                StoryViewerScreen(onClose = { navController.popBackStack() })
            }

            composable(Routes.Chats) {
                ChatListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { chatId ->
                        navController.navigate(Routes.chatThread(chatId))
                    },
                    onNewChat = { navController.navigate(Routes.NewChat) }
                )
            }

            composable(Routes.NewChat) {
                NewChatScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { chatId ->
                        navController.navigate(Routes.chatThread(chatId)) {
                            popUpTo(Routes.NewChat) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Routes.ChatThread,
                arguments = listOf(
                    navArgument(Routes.ChatThreadArg) {
                        type = NavType.StringType
                    }
                )
            ) {
                ChatThreadScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { uid ->
                        navController.navigate(Routes.publicProfile(uid))
                    }
                )
            }

            composable(Routes.Boards) {
                BoardsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBoard = { boardId ->
                        navController.navigate(Routes.boardThreads(boardId))
                    }
                )
            }

            composable(
                route = Routes.BoardThreads,
                arguments = listOf(
                    navArgument(Routes.BoardIdArg) {
                        type = NavType.StringType
                    }
                )
            ) { entry ->
                val boardId = entry.arguments?.getString(Routes.BoardIdArg).orEmpty()

                BoardThreadsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenThread = { threadId ->
                        navController.navigate(Routes.threadDetail(boardId, threadId))
                    },
                    onNewThread = {
                        navController.navigate(Routes.createThread(boardId))
                    }
                )
            }

            composable(
                route = Routes.CreateThread,
                arguments = listOf(
                    navArgument(Routes.BoardIdArg) {
                        type = NavType.StringType
                    }
                )
            ) { entry ->
                val boardId = entry.arguments?.getString(Routes.BoardIdArg).orEmpty()

                CreateThreadScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { threadId ->
                        navController.navigate(Routes.threadDetail(boardId, threadId)) {
                            popUpTo(Routes.CreateThread) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Routes.ThreadDetail,
                arguments = listOf(
                    navArgument(Routes.BoardIdArg) {
                        type = NavType.StringType
                    },
                    navArgument(Routes.ThreadIdArg) {
                        type = NavType.StringType
                    }
                )
            ) {
                ThreadDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { uid ->
                        navController.navigate(Routes.publicProfile(uid))
                    }
                )
            }
        }
    }
}
