package com.example.taskmanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanager.theme.TextMuted
import com.example.taskmanager.theme.TextPrimary

/**
 * A reusable card container for performance sections.
 */
@Composable
fun StatCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(com.example.taskmanager.theme.SurfaceContainer)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/**
 * Horizontal progress bar for RAM / Storage.
 */
@Composable
fun StatBar(
    label: String,
    value: Float, // 0f..1f
    usedLabel: String,
    totalLabel: String,
    color: Color,
    height: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "stat_bar_anim"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "$usedLabel / $totalLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(CircleShape)
                .background(com.example.taskmanager.theme.SurfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedValue)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.7f), color)
                        )
                    )
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${(value * 100).toInt()}% used",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f),
        )
    }
}

/**
 * Sparkline (line chart) for historical CPU / RAM / FPS data.
 * Includes inset bounds so 0% and 100% lines are never clipped by container edges.
 */
@Composable
fun SparklineChart(
    samples: List<Float>,   // 0f..1f values
    color: Color,
    fillColor: Color = color.copy(alpha = 0.15f),
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas

        val w = size.width
        val h = size.height
        val insetY = 8.dp.toPx()
        val usableH = h - (insetY * 2)
        val stepX = w / (samples.size - 1).toFloat()

        val path = Path()
        val fillPath = Path()

        samples.forEachIndexed { i, v ->
            val x = i * stepX
            val clampedV = v.coerceIn(0f, 1f)
            val y = (h - insetY) - (clampedV * usableH)
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo((samples.size - 1) * stepX, h)
        fillPath.close()

        drawPath(fillPath, fillColor)
        drawPath(
            path,
            color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
