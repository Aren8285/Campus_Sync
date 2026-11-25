package com.campussync.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.ReportDialog
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.Post

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onCreatePost: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onAddStory: () -> Unit,
    onOpenStories: (String) -> Unit,
    onOpenChats: () -> Unit,
    onOpenBoards: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var reportTarget by remember { mutableStateOf<Post?>(null) }
    var hasLoadedOnce by remember { mutableStateOf(false) }

    // Refresh when the student returns from the composer so a new post appears.
    LifecycleResumeEffect(Unit) {
        if (hasLoadedOnce) {
            viewModel.refresh()
        } else {
            hasLoadedOnce = true
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(message) {
        val pending = message
        if (pending != null) {
            snackbarHostState.showSnackbar(pending)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScreenHeader(
                title = "Campus feed",
                actions = {
                    IconButton(onClick = onOpenBoards) {
                        Icon(
                            imageVector = Icons.Outlined.Forum,
                            contentDescription = "Message boards"
                        )
                    }

                    IconButton(onClick = onOpenChats) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = "Messages"
                        )
                    }
                }
            )

            when (val feedState = state) {
                UiState.Loading -> LoadingView(
                    modifier = Modifier.weight(1f),
                    message = "Loading your college feed..."
                )

                is UiState.Error -> ErrorView(
                    modifier = Modifier.weight(1f),
                    message = feedState.message,
                    onRetry = viewModel::refresh
                )

                UiState.Empty -> EmptyView(
                    modifier = Modifier.weight(1f),
                    message = "No posts from your college yet."
                )

                is UiState.Success -> {
                    val content = feedState.data

                    PullToRefreshBox(
                        isRefreshing = content.isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(key = "stories") {
                                StoryBar(
                                    groups = content.storyGroups,
                                    currentUid = content.currentProfile?.uid.orEmpty(),
                                    onAddStory = onAddStory,
                                    onOpenStories = onOpenStories
                                )
                            }

                            item(key = "compose") {
                                CreatePostRow(onClick = onCreatePost)
                            }

                            if (content.posts.isEmpty()) {
                                item(key = "empty") {
                                    EmptyView(
                                        modifier = Modifier.height(220.dp),
                                        message = "Nothing here yet. Start the conversation with your first post."
                                    )
                                }
                            }

                            items(content.posts, key = { it.id }) { post ->
                                PostCard(
                                    post = post,
                                    currentUid = content.currentProfile?.uid.orEmpty(),
                                    onOpen = { onOpenPost(post.id) },
                                    onAuthorClick = { onOpenProfile(post.authorId) },
                                    onLike = { viewModel.toggleLike(post) },
                                    onComment = { onOpenPost(post.id) },
                                    onShare = { sharePost(context, post) },
                                    onDelete = { viewModel.deletePost(post.id) },
                                    onBlock = {
                                        viewModel.blockUser(
                                            targetUid = post.authorId,
                                            name = post.authorName
                                        )
                                    },
                                    onReport = { reportTarget = post },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            if (content.hasMore) {
                                item(key = "load-more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (content.isLoadingMore) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            OutlinedButton(onClick = viewModel::loadMore) {
                                                Text(text = "Load more")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val target = reportTarget
    if (target != null) {
        ReportDialog(
            title = "Report post",
            onDismiss = { reportTarget = null },
            onSubmit = { reason, details ->
                viewModel.reportPost(target, reason, details)
                reportTarget = null
            }
        )
    }
}

@Composable
private fun CreatePostRow(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(text = "Share something with your college")
    }
}
