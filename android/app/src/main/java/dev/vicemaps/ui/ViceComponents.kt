package dev.vicemaps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.vicemaps.ui.theme.Vice

/** Rounded plum gradient panel — the MapGenie-style "window". */
@Composable
fun VicePanel(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(radius)
  Column(
      modifier
          .shadow(18.dp, shape, ambientColor = Vice.Ink, spotColor = Vice.Ink)
          .clip(shape)
          .background(Vice.Window)
          .border(1.dp, Vice.Hairline, shape)
          .padding(contentPadding),
      content = content,
  )
}

/** Condensed deco caps heading, e.g. "MIAMI" or "POINTS OF INTEREST". */
@Composable
fun ViceHeading(text: String, modifier: Modifier = Modifier, color: Color = Vice.Cream, big: Boolean = true) {
  Text(
      text.uppercase(),
      modifier = modifier,
      color = color,
      style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleSmall,
  )
}

enum class PillKind { Primary, Secondary }

@Composable
fun VicePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: PillKind = PillKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
  val bg = if (kind == PillKind.Primary) Vice.Pink else Vice.Ink.copy(alpha = 0.35f)
  val fg = if (kind == PillKind.Primary) Vice.Ink else Vice.Blush
  Row(
      modifier
          .defaultMinSize(minHeight = 52.dp)
          .clip(CircleShape)
          .background(if (enabled) bg else bg.copy(alpha = 0.5f))
          .then(if (kind == PillKind.Secondary) Modifier.border(1.dp, Vice.FieldBorder, CircleShape) else Modifier)
          .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
          .padding(horizontal = 22.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
      Box(Modifier.size(8.dp))
    }
    Text(text.uppercase(), color = fg, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
  }
}

/** Round map control (recenter, look switch...). */
@Composable
fun ViceRoundButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
      modifier
          .size(52.dp)
          .shadow(10.dp, CircleShape, ambientColor = Vice.Ink, spotColor = Vice.Ink)
          .clip(CircleShape)
          .background(Vice.Plum)
          .border(1.dp, Vice.Hairline, CircleShape)
          .clickable(role = Role.Button, onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = contentDescription, tint = Vice.White)
  }
}
