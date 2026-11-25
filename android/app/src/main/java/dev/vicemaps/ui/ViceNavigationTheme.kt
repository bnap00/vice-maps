package dev.vicemaps.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.stadiamaps.ferrostar.composeui.theme.InstructionRowTheme
import com.stadiamaps.ferrostar.composeui.theme.NavigationUITheme
import com.stadiamaps.ferrostar.composeui.theme.RoadNameViewTheme
import com.stadiamaps.ferrostar.composeui.theme.TripProgressViewStyle
import com.stadiamaps.ferrostar.composeui.theme.TripProgressViewTheme
import com.stadiamaps.ferrostar.maplibreui.NavigationMapPuckStyle
import com.stadiamaps.ferrostar.maplibreui.routeline.BorderedPolyline
import com.stadiamaps.ferrostar.maplibreui.routeline.RouteOverlayBuilder
import dev.vicemaps.ui.theme.Vice

/** Ferrostar's turn banner, road-name pill and trip bar in Vice colours. */
object ViceNavigationUITheme : NavigationUITheme {
  override val instructionRowTheme: InstructionRowTheme
    @Composable
    get() =
        object : InstructionRowTheme {
          override val distanceTextStyle: TextStyle
            @Composable get() = MaterialTheme.typography.titleLarge.copy(color = Vice.Cream)

          override val instructionTextStyle: TextStyle
            @Composable get() = MaterialTheme.typography.headlineSmall.copy(color = Vice.White)

          override val iconTintColor: Color
            @Composable get() = Vice.Peach

          override val backgroundColor: Color
            @Composable get() = Vice.Plum
        }

  override val roadNameViewTheme: RoadNameViewTheme
    @Composable
    get() =
        object : RoadNameViewTheme {
          override val textStyle: TextStyle
            @Composable get() = MaterialTheme.typography.labelLarge.copy(color = Vice.Ink)

          override val backgroundColor: Color
            @Composable get() = Vice.Pink

          override val borderColor: Color
            @Composable get() = Vice.PlumDeep
        }

  override val tripProgressViewTheme: TripProgressViewTheme
    @Composable
    get() =
        object : TripProgressViewTheme {
          override val style: TripProgressViewStyle
            @Composable get() = TripProgressViewStyle.SIMPLIFIED

          override val measurementTextStyle: TextStyle
            @Composable get() = MaterialTheme.typography.titleMedium.copy(color = Vice.Cream)

          override val secondaryTextStyle: TextStyle
            @Composable get() = MaterialTheme.typography.labelSmall.copy(color = Vice.Blush)

          override val exitIconColor: Color
            @Composable get() = Vice.Ink

          override val exitButtonBackgroundColor: Color
            @Composable get() = Vice.Pink

          override val backgroundColor: Color
            @Composable get() = Vice.Plum
        }

  override val buttonSize: DpSize
    @Composable get() = DpSize(56.dp, 56.dp)
}

val VicePuck =
    NavigationMapPuckStyle(
        dotFillColorCurrentLocation = Vice.Pink,
        dotFillColorOldLocation = Vice.PinkHover,
        dotStrokeColor = Vice.Ink,
        accuracyStrokeColor = Vice.Pink,
        accuracyFillColor = Vice.Pink.copy(alpha = 0.18f),
        bearingColor = Vice.Cream,
    )

val ViceRouteOverlay =
    RouteOverlayBuilder(
        { uiState ->
          uiState.routeGeometry?.let { geometry ->
            BorderedPolyline(
                points = geometry,
                color = Vice.Route,
                borderColor = Vice.RouteBorder,
                lineWidth = 9f,
                borderWidth = 3f,
            )
          }
        }
    )
