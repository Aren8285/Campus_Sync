package com.campussync.app.feature.knowledge

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.campussync.app.core.ui.components.PlaceholderScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Composable
fun KnowledgeScreen(
    modifier: Modifier = Modifier,
    viewModel: KnowledgeViewModel = hiltViewModel()
) {
    PlaceholderScreen(
        title = viewModel.screenTitle,
        modifier = modifier
    )
}

@HiltViewModel
class KnowledgeViewModel @Inject constructor() : ViewModel() {
    val screenTitle: String = "Knowledge"
}
