package com.campussync.app.feature.collab

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.campussync.app.core.ui.components.PlaceholderScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Composable
fun CollabScreen(
    modifier: Modifier = Modifier,
    viewModel: CollabViewModel = hiltViewModel()
) {
    PlaceholderScreen(
        title = viewModel.screenTitle,
        modifier = modifier
    )
}

@HiltViewModel
class CollabViewModel @Inject constructor() : ViewModel() {
    val screenTitle: String = "Collab"
}
