package com.fixmoney.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Root composable, shared by both platforms. Android's MainActivity and iOS's
 * MainViewController both do nothing but host this.
 *
 * Placeholder for now - the real screens get built here one at a time, using
 * the old XML/Fragment app (~/Downloads/Expense) as the reference for what
 * each screen should look like and do.
 */
@Composable
fun App() {
    FixMoneyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "FixMoney",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Kotlin Multiplatform + Compose",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Running on ${platformName()}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
    }
}

/** Proves at a glance which platform the shared UI is actually running on. */
expect fun platformName(): String
