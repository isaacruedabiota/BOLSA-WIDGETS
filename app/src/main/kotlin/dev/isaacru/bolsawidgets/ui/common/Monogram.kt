package dev.isaacru.bolsawidgets.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

/**
 * The stand-in for a company logo.
 *
 * No endpoint this app talks to serves logos, and fetching them from somewhere else would
 * mean a new dependency, an image request per row and a blank circle for everything not
 * on that host. Initials on a colour derived from the ticker cost nothing, work offline,
 * and are stable: SAN.MC is the same shade every time it is drawn.
 */
@Composable
fun SymbolMonogram(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    val background = Monogram.colorFor(symbol)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = Monogram.initialsOf(symbol),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size * 0.36f).sp,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** The pure half of the monogram, so the letters and the colour can be tested. */
object Monogram {

    /**
     * The palette. Deep enough that white letters stay readable on every one of them,
     * and varied enough that two symbols next to each other rarely collide.
     */
    private val Palette = listOf(
        Color(0xFF2F6F4E),
        Color(0xFF1F4E79),
        Color(0xFF6B3FA0),
        Color(0xFF8C4A2F),
        Color(0xFF2E6E70),
        Color(0xFF7A3B5E),
        Color(0xFF4A5A2B),
        Color(0xFF3C4A6B),
    )

    /**
     * Up to two letters, taken from the ticker without its market suffix: "SAN.MC" is
     * "SA", not "S.". A ticker that is all punctuation falls back to a dash rather than
     * to an empty circle.
     */
    fun initialsOf(symbol: String): String {
        val core = symbol.substringBefore('.').substringBefore('-').substringBefore('=')
        val letters = core.filter { it.isLetterOrDigit() }
        return when {
            letters.isEmpty() -> "-"
            letters.length == 1 -> letters.uppercase()
            else -> letters.take(2).uppercase()
        }
    }

    /**
     * Same symbol, same colour, always: the hash is taken over the normalised ticker so
     * the circle does not change shade when the same value is drawn on another screen.
     */
    fun colorFor(symbol: String): Color {
        val key = symbol.trim().uppercase()
        if (key.isEmpty()) return Palette.first()
        // String.hashCode is specified by the language, so this is stable across devices
        // and versions in a way that a JVM identity hash would not be.
        val index = key.hashCode().absoluteValue % Palette.size
        return Palette[index]
    }

    /** Exposed for the test: the palette has to stay in step with the index maths. */
    val paletteSize: Int get() = Palette.size
}
