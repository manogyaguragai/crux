package com.crux.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow

/** The settings gear (sliders glyph). Lives on every tab so settings is always one tap away. */
@Composable
fun SettingsGear(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Icon(
        imageVector = CruxIcons.Settings,
        contentDescription = "settings",
        tint = InkLow,
        modifier = modifier
            .size(24.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    )
}

/**
 * The shared header for the titled tabs (stack, projects, review): the Display title on the left, an
 * optional [trailing] control (e.g. projects' edit toggle), then the settings gear on the far right.
 * Home has its own header (nudge count + gear) because it carries no title.
 */
@Composable
fun TabHeader(
    title: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = CruxType.Display, color = InkHi)
        Spacer(Modifier.weight(1f))
        trailing()
        Spacer(Modifier.width(Dimens.Unit * 2))
        SettingsGear(onClick = onOpenSettings)
    }
}
