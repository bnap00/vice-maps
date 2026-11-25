package dev.vicemaps

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.stadiamaps.ferrostar.core.DefaultNavigationViewModel
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.annotation.valhalla.valhallaExtendedOSRMAnnotationPublisher
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.toUserLocation
import dev.vicemaps.search.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind

data class SearchState(
    val query: String = "",
    val results: List<Place> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class SceneState(
    val selected: Place? = null,
    val routing: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ViceNavigationViewModel(
    val ferrostarCore: FerrostarCore = AppModule.ferrostarCore,
    private val locationProvider: NavigationLocationProvider = AppModule.locationProvider,
) : DefaultNavigationViewModel(ferrostarCore, valhallaExtendedOSRMAnnotationPublisher()) {

  private val hasLocationPermission = MutableStateFlow(false)

  private val _simulated = MutableStateFlow(false)
  val simulated = _simulated.asStateFlow()

  private val _location = MutableStateFlow<UserLocation?>(null)
  val location = _location.asStateFlow()

  private val _search = MutableStateFlow(SearchState())
  val search = _search.asStateFlow()

  private val _scene = MutableStateFlow(SceneState())
  val scene = _scene.asStateFlow()

  private var searchJob: Job? = null

  /** Show the user's position on the map even when not navigating. */
  override val navigationUiState: StateFlow<NavigationUiState> =
      combine(super.navigationUiState, _location) { ui, loc ->
            if (ui.isNavigating()) ui else ui.copy(location = loc)
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), NavigationUiState.empty())

  init {
    viewModelScope.launch {
      hasLocationPermission
          .flatMapLatest { permitted ->
            if (!permitted) flowOf(AppModule.fallbackLocation)
            else locationProvider.locationUpdates(2000L).map { it.toUserLocation() }
          }
          .collect { _location.value = it }
    }
  }

  fun setLocationPermission(permitted: Boolean) {
    hasLocationPermission.value = permitted
  }

  // ---- search ----

  fun onQueryChange(query: String) {
    _search.update { it.copy(query = query, error = null) }
    searchJob?.cancel()
    if (query.trim().length < 2) {
      _search.update { it.copy(results = emptyList(), loading = false) }
      return
    }
    searchJob =
        viewModelScope.launch {
          delay(350) // debounce typing
          runSearch(query)
        }
  }

  /** Runs a search immediately; returns the results (also used by the car screen). */
  suspend fun runSearch(query: String): List<Place> {
    _search.update { it.copy(loading = true, error = null) }
    return try {
      val near = _location.value?.coordinates
      val results = AppModule.search.search(query, near)
      _search.update { it.copy(results = results, loading = false) }
      results
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      Log.w(TAG, "search failed", e)
      _search.update { it.copy(loading = false, error = "Search is unavailable. Check your connection.") }
      emptyList()
    }
  }

  fun clearSearch() {
    searchJob?.cancel()
    _search.value = SearchState()
  }

  // ---- destination ----

  fun select(place: Place) {
    _scene.value = SceneState(selected = place)
    clearSearch()
  }

  fun selectPoint(coordinate: GeographicCoordinate) =
      select(Place("Dropped pin", "%.5f, %.5f".format(coordinate.lat, coordinate.lng), coordinate))

  fun clearSelection() {
    _scene.value = SceneState()
  }

  fun dismissMessage() = _scene.update { it.copy(message = null) }

  fun toggleSimulation() {
    _simulated.value = !_simulated.value
    if (!_simulated.value) locationProvider.disableSimulation()
  }

  fun enableAutoDriveSimulation() {
    _simulated.value = true
  }

  fun startSelected(demo: Boolean = false) {
    val place = _scene.value.selected ?: return
    if (demo) _simulated.value = true
    startNavigation(place.coordinate, place.name)
  }

  fun startNavigation(destination: GeographicCoordinate, name: String? = null) {
    _scene.update { it.copy(routing = true, message = null) }
    viewModelScope.launch(Dispatchers.IO) {
      val origin = _location.value ?: AppModule.fallbackLocation
      try {
        Log.d(TAG, "routing to $name $destination")
        val routes =
            ferrostarCore.getRoutes(
                origin, listOf(Waypoint(coordinate = destination, kind = WaypointKind.BREAK)))
        val route = routes.firstOrNull() ?: error("No route found")
        if (_simulated.value) locationProvider.enableSimulationOn(route)
        if (navigationUiState.value.isNavigating()) ferrostarCore.replaceRoute(route = route)
        else ferrostarCore.startNavigation(route = route)
        _scene.value = SceneState()
      } catch (e: Exception) {
        Log.w(TAG, "routing failed", e)
        _scene.update {
          it.copy(routing = false, message = "Couldn't find a route. Try again in a moment.")
        }
      }
    }
  }

  override fun toggleMute() {
    val observer = ferrostarCore.spokenInstructionObserver ?: return
    observer.setMuted(!observer.isMuted)
  }

  override fun stopNavigation() {
    locationProvider.disableSimulation()
    _simulated.value = false
    ferrostarCore.stopNavigation()
  }

  companion object {
    private const val TAG = "ViceNavVM"
  }
}
