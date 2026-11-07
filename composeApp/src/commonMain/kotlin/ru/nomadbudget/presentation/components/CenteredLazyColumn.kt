package ru.nomadbudget.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CenteredLazyColumn(
    maxContentWidth: Dp = 960.dp,
    top: Dp = 12.dp,
    bottom: Dp = 96.dp,
    content: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val side = maxOf(12.dp, (maxWidth - maxContentWidth) / 2)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

fun centeredSide(maxWidth: Dp, maxContentWidth: Dp): Dp = maxOf(12.dp, (maxWidth - maxContentWidth) / 2)
