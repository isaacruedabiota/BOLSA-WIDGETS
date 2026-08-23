package dev.isaacru.bolsawidgets.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.isaacru.bolsawidgets.ui.theme.Gain
import dev.isaacru.bolsawidgets.ui.theme.Loss
import dev.isaacru.bolsawidgets.ui.theme.Neutral

/** Green above zero, red below, grey when flat. The widgets reuse the same rule. */
fun changeColor(value: Double): Color = when {
    value > 0.0 -> Gain
    value < 0.0 -> Loss
    else -> Neutral
}

/** Percentage with a direction arrow, coloured by sign. */
@Composable
fun ChangeIndicator(
    percent: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    showArrow: Boolean = true,
) {
    val color = changeColor(percent)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (showArrow) {
            val icon = when {
                percent > 0.0 -> Icons.Filled.ArrowUpward
                percent < 0.0 -> Icons.Filled.ArrowDownward
                else -> Icons.Filled.Remove
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(text = Format.percent(percent), color = color, style = style)
    }
}
