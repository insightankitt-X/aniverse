package app.aniverse.local.ui.components

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Renders the annotated composable in the Crimson dark theme. */
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class DarkPreview

/** Renders the annotated composable in the Crimson light theme. */
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
annotation class LightPreview
