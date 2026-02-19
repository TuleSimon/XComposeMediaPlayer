package com.anonymous.examples.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anonymous.xmedia.XMediaPlayer
import com.anonymous.xmedia.rememberXMediaState

/**
 * Demo: Video Pager (Pause when out of view)
 *
 * This example demonstrates:
 * - HorizontalPager with video content
 * - Auto-pause when page is swiped away
 * - Auto-play when page becomes current
 * - LaunchedEffect to react to page changes
 * - Page indicator UI
 */
@Composable
fun VideoPagerDemo() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Video Pager",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        Text(
            text = "Swipe horizontally - videos auto-pause when swiped away",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        val pagerState = rememberPagerState { SampleStreams.ALL_STREAMS.size }

        // Page indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(SampleStreams.ALL_STREAMS.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .width(if (pagerState.currentPage == index) 24.dp else 8.dp)
                        .height(8.dp)
                        .background(
                            color = if (pagerState.currentPage == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.small
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            VideoPagerItem(
                url = SampleStreams.ALL_STREAMS[page],
                isCurrentPage = pagerState.currentPage == page,
                pageNumber = page + 1
            )
        }
    }
}

@Composable
fun VideoPagerItem(
    url: String,
    isCurrentPage: Boolean,
    pageNumber: Int
) {
    val state = rememberXMediaState()

    // Auto play/pause based on page visibility
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            state.play()
        } else {
            state.pause()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            XMediaPlayer(
                state = state,
                url = url,
                autoPlay = false, // We control play/pause via LaunchedEffect
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Video $pageNumber of ${SampleStreams.ALL_STREAMS.size}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
