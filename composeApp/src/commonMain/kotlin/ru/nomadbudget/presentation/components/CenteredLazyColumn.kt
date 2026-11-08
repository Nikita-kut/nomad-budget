package ru.nomadbudget.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CenteredLazyColumn(
    maxContentWidth: Dp = Ui.ContentMaxWidth,
    top: Dp = Ui.Gap,
    bottom: Dp = Ui.ScrollBottomPadding,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(aheadFraction = CACHE_FRACTION, behindFraction = CACHE_FRACTION))
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val side = centeredSide(maxWidth, maxContentWidth)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(start = side, end = side, top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(Ui.Gap),
            content = content,
        )
    }
}

private fun centeredSide(maxWidth: Dp, maxContentWidth: Dp): Dp = maxOf(Ui.SideGutter, (maxWidth - maxContentWidth) / 2)

private const val CACHE_FRACTION = 1f
