package dev.vicemaps

import android.content.Context
import com.stadiamaps.ferrostar.composeui.notification.DefaultForegroundNotificationBuilder
import com.stadiamaps.ferrostar.core.AlternativeRouteProcessor
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.CorrectiveAction
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.RouteDeviationHandler
import com.stadiamaps.ferrostar.core.http.HttpClientProvider
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.SimulatedLocationProvider
import com.stadiamaps.ferrostar.core.location.toAndroidLocation
import com.stadiamaps.ferrostar.core.service.FerrostarForegroundServiceManager
import com.stadiamaps.ferrostar.core.service.ForegroundServiceManager
import com.stadiamaps.ferrostar.core.withJsonOptions
import com.stadiamaps.ferrostar.googleplayservices.FusedNavigationLocationProvider
import dev.vicemaps.search.PhotonSearch
import java.time.Duration
import java.time.Instant
import okhttp3.OkHttpClient
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WellKnownRouteProvider
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

/**
 * App-wide singletons shared by the phone UI and the Android Auto session, so a route started on
 * one screen shows on the other. Everything here is keyless:
 * - map tiles, glyphs, sprites: OpenFreeMap (styles bundled in assets/)
 * - routing: a Valhalla server, by default the public FOSSGIS one (fair use only)
 * - search: a Photon server, by default the public one run by Komoot (fair use only)
 *
 * Both endpoints are build settings (see README), so you can point them at your own servers.
 */
object AppModule {
  /** Valhalla `/route` endpoint. Default: the public instance run by FOSSGIS e.V. */
  const val VALHALLA_URL = BuildConfig.VALHALLA_URL

  /** Photon `/api/` endpoint. Default: the public instance run by Komoot. */
  const val PHOTON_URL = BuildConfig.PHOTON_URL

  const val STYLE_DAY = "asset://vice-day.json"
  const val STYLE_NIGHT = "asset://vice-night.json"

  private lateinit var appContext: Context

  fun init(context: Context) {
    if (!::appContext.isInitialized) appContext = context.applicationContext
  }

  /** Used before the first GPS fix (and for demo drives): Miami, to match the Vice theme. */
  val fallbackLocation =
      UserLocation(GeographicCoordinate(25.7617, -80.1918), 20.0, null, Instant.now(), null)

  val locationProvider: NavigationLocationProvider by lazy {
    NavigationLocationProvider(
        liveProviding = FusedNavigationLocationProvider(appContext),
        simulatedProvider =
            SimulatedLocationProvider(
                warpFactor = 2u,
                initialLocation = fallbackLocation.toAndroidLocation(),
            ),
    )
  }

  val okHttp: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(20))
        .addInterceptor { chain ->
          // Public OSM services ask apps to identify themselves; the FOSSGIS Valhalla
          // server asks for an X-Client-Id header too.
          chain.proceed(
              chain.request()
                  .newBuilder()
                  .header("User-Agent", BuildConfig.USER_AGENT)
                  .header("X-Client-Id", "vice-maps")
                  .build()
          )
        }
        .build()
  }

  private val httpClient: HttpClientProvider by lazy { okHttp.toOkHttpClientProvider() }

  val search: PhotonSearch by lazy { PhotonSearch(okHttp, PHOTON_URL) }

  private val foregroundServiceManager: ForegroundServiceManager by lazy {
    FerrostarForegroundServiceManager(appContext, DefaultForegroundNotificationBuilder(appContext))
  }

  val ferrostarCore: FerrostarCore by lazy {
    val options =
        mapOf(
            "units" to Settings.units(appContext).valhallaUnits,
            "language" to java.util.Locale.getDefault().toLanguageTag(),
        )
    val core =
        FerrostarCore(
            WellKnownRouteProvider.Valhalla(VALHALLA_URL, "auto").withJsonOptions(options),
            httpClient = httpClient,
            locationProvider = locationProvider,
            foregroundServiceManager = foregroundServiceManager,
            navigationControllerConfig =
                NavigationControllerConfig(
                    WaypointAdvanceMode.WaypointWithinRange(100.0),
                    stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
                    stepAdvanceDistanceToEndOfStep(10u, 32u),
                    RouteDeviationTracking.StaticThreshold(15U, 50.0),
                    CourseFiltering.SNAP_TO_ROUTE,
                ),
        )
    // Off route: fetch a new route to the remaining waypoints.
    core.deviationHandler = RouteDeviationHandler { _, _, remainingWaypoints ->
      CorrectiveAction.GetNewRoutes(remainingWaypoints)
    }
    core.alternativeRouteProcessor = AlternativeRouteProcessor { it, routes ->
      routes.firstOrNull()?.let { route -> it.replaceRoute(route) }
    }
    core
  }

  val ttsObserver: AndroidTtsObserver by lazy { AndroidTtsObserver(appContext) }

  val viewModel: ViceNavigationViewModel by lazy { ViceNavigationViewModel() }
}
