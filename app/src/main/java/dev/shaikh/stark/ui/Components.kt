package dev.shaikh.stark.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.shaikh.stark.security.SecurityState

fun SecurityState.Level.color(): Color = when (this) {
    SecurityState.Level.OK -> Stark.Cyan
    SecurityState.Level.ADVISORY -> Stark.Amber
    SecurityState.Level.CRITICAL -> Stark.Alert
}

/** Translucent titanium glass with a chamfered silhouette and a lit leading edge. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color = Stark.Cyan,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(HudPanelShape)
            .background(Stark.Titanium.copy(alpha = 0.78f))
            .border(1.dp, Stark.Line, HudPanelShape)
            .drawWithContent {
                drawContent()
                val cut = 14.dp.toPx()
                drawLine(accent.copy(alpha = 0.8f), Offset(0f, cut), Offset(cut, 0f), 1.5.dp.toPx())
                drawLine(accent.copy(alpha = 0.5f), Offset(cut, 0f), Offset(cut + 28.dp.toPx(), 0f), 1.5.dp.toPx())
            }
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 10.dp, height = 2.dp).background(Stark.Cyan.copy(alpha = 0.7f)))
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            style = hudLabel(),
            color = Stark.TextDim,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .clip(HudButtonShape)
            .border(1.dp, color.copy(alpha = 0.55f), HudButtonShape)
            .background(color.copy(alpha = 0.08f))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color)
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = hudLabel(), color = color)
    }
}

enum class ButtonTone { Primary, Quiet, Warning }

/** Chamfered, uppercase action with press depth and a haptic tick. Minimum 48dp touch target. */
@Composable
fun StarkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Primary,
    enabled: Boolean = true,
) {
    val color = when (tone) {
        ButtonTone.Primary -> Stark.Cyan
        ButtonTone.Quiet -> Stark.TextDim
        ButtonTone.Warning -> Stark.Amber
    }.let { if (enabled) it else Stark.TextFaint }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "press")
    val view = LocalView.current
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .defaultMinSize(minHeight = 48.dp)
            .clip(HudButtonShape)
            .border(1.dp, color.copy(alpha = if (pressed) 1f else 0.6f), HudButtonShape)
            .background(color.copy(alpha = if (pressed) 0.22f else if (tone == ButtonTone.Primary && enabled) 0.10f else 0.03f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun KeyValueRow(label: String, value: String, level: SecurityState.Level?, detail: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(level?.color() ?: Stark.TextFaint)
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = level?.color() ?: Stark.TextDim,
            modifier = Modifier.padding(start = 18.dp, top = 2.dp),
        )
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = Stark.TextDim,
                modifier = Modifier.padding(start = 18.dp, top = 4.dp),
            )
        }
    }
}
