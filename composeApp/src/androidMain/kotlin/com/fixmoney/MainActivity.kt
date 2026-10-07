package com.fixmoney

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fixmoney.ui.App
import com.fixmoney.ui.LaunchTarget

/** Thin Android shell - all UI lives in the shared [App] composable. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Debug builds only: `--es worker_url ...` points AI calls at a local Worker.
        DebugWorker.fromIntent(this, intent)
        // Restored activities replay their old intent; only a fresh launch asks for a screen.
        if (savedInstanceState == null) LaunchTarget.request(intent?.getStringExtra(LaunchTarget.EXTRA))
        setContent { App() }
    }

    // singleTask delivers later launches here instead of creating an activity.
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DebugWorker.fromIntent(this, intent)
        LaunchTarget.request(intent.getStringExtra(LaunchTarget.EXTRA))
    }
}
