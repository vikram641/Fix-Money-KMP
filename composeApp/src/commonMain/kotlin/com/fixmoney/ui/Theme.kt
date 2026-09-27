package com.fixmoney.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The purple accent tokens carried over from the old app's colors.xml
// (progress_start / accent / progress_end). Dark theme + the full palette
// come later, when the design pass happens.
private val FixMoneyColors = lightColorScheme(
    primary = Color(0xFF6C4DF6),
    onPrimary = Color.White,
    secondary = Color(0xFF9B6DFF),
    background = Color(0xFFF7F6FB),
    surface = Color.White
)

@Composable
fun FixMoneyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FixMoneyColors, content = content)
}
