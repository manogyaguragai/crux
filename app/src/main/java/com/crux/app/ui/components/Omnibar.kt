package com.crux.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.crux.app.ui.Copy
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.Oxblood
import com.crux.app.ui.theme.Raised

/**
 * The omnibar: the single capture input, the centerpiece of home (ui-ux-decisions.md).
 * Phase 1 is capture only: submit turns raw text into a title-only task and clears the field.
 * Capture is never interrupted, so there is no dialog, sheet, or confirmation here.
 *
 * The bloom (an oxblood radiance) sits behind the shell, home and app icon only. The idle
 * breathing loop is a phase 4 motion item; this draws the static glow.
 */
@Composable
fun Omnibar(
    onCapture: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onCapture(text)
            text = ""
        }
    }

    Box(modifier) {
        // the bloom: radial oxblood behind the shell, per design-tokens.md.
        Spacer(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            0.00f to Oxblood.copy(alpha = 0.55f),
                            0.46f to Oxblood.copy(alpha = 0.18f),
                            1.00f to Color.Transparent,
                            center = Offset(size.width * 0.5f, size.height * 0.82f),
                            radius = 0.72f * maxOf(size.width, size.height),
                        ),
                    )
                },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(Dimens.RadiusShell))
                .background(Raised)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CruxIcons.Add,
                contentDescription = null,
                tint = Garnet,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(Copy.OMNIBAR_PLACEHOLDER, style = CruxType.Body, color = InkLow)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = CruxType.Body.copy(color = InkHi),
                    singleLine = true,
                    cursorBrush = SolidColor(Garnet),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
