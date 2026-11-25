package dev.vicemaps.car

import androidx.car.app.CarContext
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.rallista.car.app.compose.ComposableScreen
import com.stadiamaps.ferrostar.car.app.navigation.NavigationManagerBridge
import com.stadiamaps.ferrostar.car.app.navigation.TurnByTurnNotificationManager
import com.stadiamaps.ferrostar.car.app.template.icons.InterfaceCarIcons
import com.stadiamaps.ferrostar.car.app.template.models.FerrostarRoutingInfo
import com.stadiamaps.ferrostar.car.app.template.models.toCarTravelEstimate
import com.stadiamaps.ferrostar.composeui.config.VisualNavigationViewConfig
import com.stadiamaps.ferrostar.composeui.config.withSpeedLimitStyle
import com.stadiamaps.ferrostar.composeui.views.components.speedlimit.SignageStyle
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.boundingBox
import com.stadiamaps.ferrostar.core.extensions.progress
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationCameraOptions
import com.stadiamaps.ferrostar.maplibreui.runtime.rememberNavigationMapState
import com.stadiamaps.ferrostar.ui.maplibre.car.app.CarAppNavigationView
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.SurfaceAreaTracker
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.screenSurfaceState
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.surfaceStableFractionalPadding
import dev.vicemaps.AppModule
import dev.vicemaps.R
import dev.vicemaps.Settings
import dev.vicemaps.ui.ViceRouteOverlay
import dev.vicemaps.ui.theme.Vice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The Android Auto map screen: the Vice map drawn on the car display, with Android Auto's own
 * turn card (tinted plum) and controls on top.
 */
class ViceCarNavigationScreen(carContext: CarContext) : ComposableScreen(carContext) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val viewModel = AppModule.viewModel
  private val icons = InterfaceCarIcons(carContext)

  private val notificationManager =
      TurnByTurnNotificationManager(context = carContext, smallIconRes = R.drawable.ic_stat_vice)

  private val navigationManagerBridge =
      NavigationManagerBridge(
          navigationManager = carContext.getCarService(NavigationManager::class.java),
          viewModel = viewModel,
          context = carContext,
          notificationManager = notificationManager,
          onStopNavigation = { viewModel.stopNavigation() },
          onAutoDriveEnabled = { viewModel.enableAutoDriveSimulation() },
          isCarForeground = { lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
      )

  private var uiState: NavigationUiState? by mutableStateOf(null)
  private var mapState: NavigationMapState? = null
  private var browsingPadding: PaddingValues = PaddingValues()
  private val surfaceAreaTracker = SurfaceAreaTracker { surfaceGestureCallback = it }

  init {
    navigationManagerBridge.start(scope)
    viewModel.navigationUiState
        .onEach {
          uiState = it
          invalidate()
        }
        .launchIn(scope)
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
          override fun onDestroy(owner: LifecycleOwner) {
            navigationManagerBridge.stop()
            scope.cancel()
          }
        }
    )
  }

  @Composable
  override fun content() {
    val surfaceArea by screenSurfaceState(surfaceAreaTracker)
    val normalPadding = surfaceStableFractionalPadding(surfaceArea?.compositeArea)
    val trackingPadding = surfaceStableFractionalPadding(surfaceArea?.compositeArea, top = 0.5f)
    val cameraOptions =
        navigationCameraOptions().copy(browsingPadding = normalPadding, navigationPadding = trackingPadding)
    val state = rememberNavigationMapState(navigationCameraOptions = cameraOptions)
    browsingPadding = normalPadding
    mapState = state

    LaunchedEffect(uiState?.isNavigating(), state) {
      if (uiState?.isNavigating() == true) state.recenter(isNavigating = true)
    }
    LaunchedEffect(state) { snapshotFlow { state.cameraMode }.collectLatest { invalidate() } }

    // Car headlights / day-night signal picks the look unless the user forced one on the phone.
    val styleUrl = Settings.mapLook(carContext).styleUrl(carContext.isDarkMode)

    CarAppNavigationView(
        modifier = Modifier.fillMaxSize(),
        styleUrl = styleUrl,
        navigationMapState = state,
        navigationCameraOptions = cameraOptions,
        viewModel = viewModel,
        config = VisualNavigationViewConfig.Default().withSpeedLimitStyle(SignageStyle.ViennaConvention),
        routeOverlayBuilder = ViceRouteOverlay,
        surfaceAreaTracker = surfaceAreaTracker,
    )
  }

  override fun onGetTemplate(): Template {
    val state = uiState
    val plum = CarColor.createCustom(Vice.Plum.toArgb(), Vice.PlumDeep.toArgb())
    val builder = NavigationTemplate.Builder().setBackgroundColor(plum)

    if (state != null && state.isNavigating()) {
      val trip = state.tripState
      trip?.let { t ->
        FerrostarRoutingInfo.Builder(carContext).setTripState(t).build()?.let { builder.setNavigationInfo(it) }
        t.progress()?.let { builder.setDestinationTravelEstimate(it.toCarTravelEstimate()) }
      }
      builder.setActionStrip(
          ActionStrip.Builder()
              .addAction(
                  Action.Builder().setTitle("Stop").setOnClickListener { viewModel.stopNavigation() }.build()
              )
              .build()
      )
      builder.setMapActionStrip(mapActions(navigating = true, muted = state.isMuted))
    } else {
      builder.setActionStrip(
          ActionStrip.Builder()
              .addAction(
                  Action.Builder()
                      .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_search)).build())
                      .setTitle("Search")
                      .setOnClickListener {
                        screenManager.push(ViceCarSearchScreen(carContext))
                      }
                      .build()
              )
              .build()
      )
      builder.setMapActionStrip(mapActions(navigating = false, muted = null))
    }
    return builder.build()
  }

  private fun mapActions(navigating: Boolean, muted: Boolean?): ActionStrip =
      ActionStrip.Builder()
          .addAction(Action.PAN)
          .apply {
            if (navigating) {
              addAction(Action.Builder().setIcon(icons.mute(muted == true)).setOnClickListener { viewModel.toggleMute() }.build())
            }
          }
          .addAction(Action.Builder().setIcon(icons.add).setOnClickListener { mapState?.zoomIn() }.build())
          .addAction(Action.Builder().setIcon(icons.remove).setOnClickListener { mapState?.zoomOut() }.build())
          .addAction(
              Action.Builder()
                  .setIcon(icons.camera(mapState?.isTrackingUser == true))
                  .setOnClickListener { cycleCamera(navigating) }
                  .build()
          )
          .build()

  private fun cycleCamera(navigating: Boolean) {
    val state = mapState ?: return
    if (navigating && state.isTrackingUser) {
      val bounds = viewModel.navigationUiState.value.routeGeometry?.boundingBox() ?: return
      state.showRouteOverview(boundingBox = bounds, paddingValues = browsingPadding)
    } else {
      state.recenter(isNavigating = navigating)
    }
    invalidate()
  }
}
