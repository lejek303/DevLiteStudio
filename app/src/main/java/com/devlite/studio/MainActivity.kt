package com.devlite.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.devlite.studio.ui.EditorScreen
import com.devlite.studio.ui.theme.DevLiteStudioTheme

/**
 * App entry point. Storage access is granted per-folder via the
 * Storage Access Framework (see the OpenDocumentTree launcher inside
 * EditorScreen) rather than a broad up-front storage permission, so
 * there's no permission-request boilerplate needed here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DevLiteStudioTheme {
                EditorScreen()
            }
        }
    }
}
