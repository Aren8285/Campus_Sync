package com.campussync.app.feature.boards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.campussync.app.core.ui.components.PlaceholderScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Composable
fun BoardsScreen(
    modifier: Modifier = Modifier,
    viewModel: BoardsViewModel = hiltViewModel()
) {
    PlaceholderScreen(
        title = viewModel.screenTitle,
        modifier = modifier
    )
}

@HiltViewModel
class BoardsViewModel @Inject constructor() : ViewModel() {
    val screenTitle: String = "Boards"
}
