package dev.vicemaps.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.stadiamaps.ferrostar.composeui.config.NavigationViewComponentBuilder
import com.stadiamaps.ferrostar.composeui.config.VisualNavigationViewConfig
import com.stadiamaps.ferrostar.composeui.config.withCustomOverlayView
import com.stadiamaps.ferrostar.composeui.config.withSpeedLimitStyle
import com.stadiamaps.ferrostar.composeui.runtime.KeepScreenOnDisposableEffect
import com.stadiamaps.ferrostar.composeui.views.components.speedlimit.SignageStyle
import com.stadiamaps.ferrostar.maplibreui.NavigationMapClickResult
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.stadiamaps.ferrostar.maplibreui.runtime.rememberNavigationMapState
import com.stadiamaps.ferrostar.maplibreui.views.DynamicallyOrientingNavigationView
import dev.vicemaps.MapLook
import dev.vicemaps.Settings
import dev.vicemaps.ViceNavigationViewModel
import dev.vicemaps.search.Place
import dev.vicemaps.ui.theme.Vice
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import androidx.compose.foundation.isSystemInDarkTheme
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import uniffi.ferrostar.GeographicCoordinate

@Composable
fun MapScreen(viewModel: ViceNavigationViewModel) {
  val context = LocalContext.current
  val uiState by viewModel.navigationUiState.collectAsState()
  val scene by viewModel.scene.collectAsState()
  val navigating = uiState.isNavigating()

  if (navigating) KeepScreenOnDisposableEffect()

  // ---- permissions ----
  val permissions =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
          arrayOf(
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION,
              Manifest.permission.POST_NOTIFICATIONS,
              Manifest.permission.FOREGROUND_SERVICE_LOCATION,
          )
      else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
  val launcher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        viewModel.setLocationPermission(granted[Manifest.permission.ACCESS_FINE_LOCATION] == true)
      }
  LaunchedEffect(Unit) {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    if (fine == PackageManager.PERMISSION_GRANTED) viewModel.setLocationPermission(true)
    else launcher.launch(permissions)
  }

  // ---- map look (day / night) ----
  var look by remember { mutableStateOf(Settings.mapLook(context)) }
  val styleUrl = look.styleUrl(isSystemInDarkTheme())

  val mapState = rememberNavigationMapState()

  // Fly to a selected destination.
  LaunchedEffect(scene.selected) {
    val place = scene.selected ?: return@LaunchedEffect
    mapState.cameraMode = NavigationCameraMode.FREE
    mapState.cameraState.animateTo(
        CameraPosition(
            target = Position(longitude = place.coordinate.lng, latitude = place.coordinate.lat),
            zoom = 15.0,
            padding = PaddingValues(bottom = 260.dp, top = 120.dp),
        )
    )
  }

  DynamicallyOrientingNavigationView(
      modifier = Modifier.fillMaxSize(),
      baseStyle = BaseStyle.Uri(styleUrl),
      navigationMapState = mapState,
      viewModel = viewModel,
      locationPuckStyle = VicePuck,
      theme = ViceNavigationUITheme,
      config = VisualNavigationViewConfig.Default().withSpeedLimitStyle(SignageStyle.ViennaConvention),
      views =
          NavigationViewComponentBuilder.Default(ViceNavigationUITheme).withCustomOverlayView { modifier ->
            if (!navigating) {
              IdleOverlay(
                  modifier = modifier,
                  viewModel = viewModel,
                  mapState = mapState,
                  look = look,
                  onCycleLook = {
                    look = MapLook.entries[(look.ordinal + 1) % MapLook.entries.size]
                    Settings.setMapLook(context, look)
                  },
              )
            }
          },
      routeOverlayBuilder = ViceRouteOverlay,
      onTapExit = { viewModel.stopNavigation() },
      onMapLongClick = { position, _ ->
        viewModel.selectPoint(GeographicCoordinate(position.lat, position.lng))
        NavigationMapClickResult.Consume
      },
      mapOptions =
          MapOptions(ornamentOptions = OrnamentOptions(isCompassEnabled = false, isScaleBarEnabled = false)),
  ) {
    SelectedPin(scene.selected?.coordinate)
  }

  if (!navigating) {
    scene.selected?.let { place ->
      DestinationSheet(
          place = place,
          routing = scene.routing,
          onDrive = { viewModel.startSelected() },
          onDemo = { viewModel.startSelected(demo = true) },
          onClose = { viewModel.clearSelection() },
      )
    }
  }

  scene.message?.let { msg -> MessageBanner(msg, onDismiss = viewModel::dismissMessage) }
}

@Composable
private fun IdleOverlay(
    modifier: Modifier,
    viewModel: ViceNavigationViewModel,
    mapState: NavigationMapState,
    look: MapLook,
    onCycleLook: () -> Unit,
) {
  val search by viewModel.search.collectAsState()
  val focus = LocalFocusManager.current

  Box(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
    Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
      SearchField(
          query = search.query,
          loading = search.loading,
          onQueryChange = viewModel::onQueryChange,
          onClear = {
            viewModel.clearSearch()
            focus.clearFocus()
          },
          onSubmit = { focus.clearFocus() },
      )
      AnimatedVisibility(search.results.isNotEmpty() || search.error != null) {
        VicePanel(
            Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(max = 420.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
          search.error?.let {
            Text(it, color = Vice.Blush, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
          }
          LazyColumn {
            items(search.results) { place ->
              ResultRow(place) {
                focus.clearFocus()
                viewModel.select(place)
              }
              HorizontalDivider(color = Vice.Hairline, modifier = Modifier.padding(horizontal = 20.dp))
            }
          }
        }
      }
    }

    Column(
        Modifier.align(Alignment.CenterEnd),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      ViceRoundButton(
          icon =
              when (look) {
                MapLook.AUTO -> Icons.Rounded.Contrast
                MapLook.DAY -> Icons.Rounded.LightMode
                MapLook.NIGHT -> Icons.Rounded.DarkMode
              },
          contentDescription = "Map look: ${look.name.lowercase()}",
          onClick = onCycleLook,
      )
      ViceRoundButton(
          icon = Icons.Rounded.MyLocation,
          contentDescription = "Center on my location",
          onClick = { mapState.recenter(isNavigating = false) },
      )
    }
  }
}

@Composable
private fun SearchField(
    query: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
  Row(
      Modifier.fillMaxWidth()
          .heightIn(min = 56.dp)
          .background(Vice.PlumDeep.copy(alpha = 0.96f), CircleShape)
          .border(1.dp, Vice.FieldBorder, CircleShape)
          .padding(start = 20.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Rounded.Search, contentDescription = null, tint = Vice.Pink)
    Spacer(Modifier.size(12.dp))
    Box(Modifier.weight(1f)) {
      if (query.isEmpty()) {
        Text("Where to?", color = Vice.Blush.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyLarge)
      }
      BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = Vice.White),
          cursorBrush = SolidColor(Vice.Pink),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
          modifier = Modifier.fillMaxWidth(),
      )
    }
    when {
      loading -> CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), color = Vice.Pink, strokeWidth = 2.dp)
      query.isNotEmpty() ->
          Box(Modifier.size(44.dp).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = Vice.Blush)
          }
    }
    Spacer(Modifier.size(8.dp))
  }
}

@Composable
private fun ResultRow(place: Place, onClick: () -> Unit) {
  Row(
      Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Rounded.Place, contentDescription = null, tint = Vice.Peach)
    Spacer(Modifier.size(14.dp))
    Column(Modifier.weight(1f)) {
      Text(place.name, color = Vice.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (place.detail.isNotBlank()) {
        Text(place.detail, color = Vice.Blush.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun DestinationSheet(
    place: Place,
    routing: Boolean,
    onDrive: () -> Unit,
    onDemo: () -> Unit,
    onClose: () -> Unit,
) {
  Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
    VicePanel(Modifier.fillMaxWidth(), radius = 28.dp, contentPadding = PaddingValues(22.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
          ViceHeading(place.name)
          if (place.detail.isNotBlank()) {
            Text(
                place.detail,
                color = Vice.Peach,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Box(Modifier.size(44.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
          Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Vice.Blush)
        }
      }
      Spacer(Modifier.size(18.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VicePill(
            text = if (routing) "Finding route…" else "Drive",
            onClick = onDrive,
            icon = Icons.Rounded.DirectionsCar,
            enabled = !routing,
            modifier = Modifier.weight(1f),
        )
        VicePill(
            text = "Demo",
            onClick = onDemo,
            kind = PillKind.Secondary,
            icon = Icons.Rounded.SmartToy,
            enabled = !routing,
        )
      }
    }
  }
}

@Composable
private fun MessageBanner(message: String, onDismiss: () -> Unit) {
  Box(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).padding(top = 72.dp), contentAlignment = Alignment.TopCenter) {
    VicePanel(Modifier.fillMaxWidth().clickable(onClick = onDismiss), contentPadding = PaddingValues(18.dp)) {
      Text(message, color = Vice.White, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
@MaplibreComposable
private fun SelectedPin(coordinate: GeographicCoordinate?) {
  val c = coordinate ?: return
  val source =
      rememberGeoJsonSource(
          GeoJsonData.Features(
              FeatureCollection(Feature(geometry = Point(longitude = c.lng, latitude = c.lat), properties = buildJsonObject {}))
          )
      )
  CircleLayer(
      id = "vice-selected-pin",
      source = source,
      color = const(Vice.Pink),
      radius = const(11.dp),
      strokeColor = const(Vice.Ink),
      strokeWidth = const(3.dp),
  )
}
