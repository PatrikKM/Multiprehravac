package com.example.multiprehrava

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NativeSubtitleOverlay(
    subtitleLines: List<SubtitleLine>,
    currentTimeMs: Long,
    modifier: Modifier = Modifier
) {
    if (subtitleLines.isEmpty()) return

    val currentLine = subtitleLines.firstOrNull { currentTimeMs in it.startMs..it.endMs }

    if (currentLine != null && currentLine.text.isNotBlank()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = currentLine.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}
