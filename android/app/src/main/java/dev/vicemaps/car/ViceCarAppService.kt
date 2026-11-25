package dev.vicemaps.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.lifecycleScope
import com.stadiamaps.ferrostar.car.app.intent.CarAppMapIntent
import com.stadiamaps.ferrostar.car.app.intent.CarAppMapIntentParser
import dev.vicemaps.AppModule
import dev.vicemaps.BuildConfig
import kotlinx.coroutines.launch

class ViceCarAppService : CarAppService() {
  // Debug builds accept any host, so the Desktop Head Unit works. Release builds only talk to
  // the Android Auto / Automotive hosts in the Car App Library's signed allowlist.
  override fun createHostValidator(): HostValidator =
      if (BuildConfig.DEBUG) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
      else
          HostValidator.Builder(applicationContext)
              .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
              .build()

  override fun onCreateSession(sessionInfo: SessionInfo): Session = ViceCarSession()
}

class ViceCarSession : Session() {
  override fun onCreateScreen(intent: Intent): Screen {
    AppModule.init(carContext)
    AppModule.ferrostarCore.spokenInstructionObserver = AppModule.ttsObserver
    AppModule.ttsObserver.start()
    val screen = ViceCarNavigationScreen(carContext)
    handleIntent(intent)
    return screen
  }

  override fun onNewIntent(intent: Intent) = handleIntent(intent)

  /** "Navigate to X" from Assistant / other apps. */
  private fun handleIntent(intent: Intent) {
    val vm = AppModule.viewModel
    when (val mapIntent = CarAppMapIntentParser.parse(intent)) {
      is CarAppMapIntent.Navigate ->
          when (val d = mapIntent.destination) {
            is CarAppMapIntent.Navigate.Destination.Coordinate -> vm.startNavigation(d.location, d.label)
            is CarAppMapIntent.Navigate.Destination.Query ->
                lifecycleScope.launch {
                  vm.runSearch(d.query).firstOrNull()?.let { vm.startNavigation(it.coordinate, it.name) }
                }
          }
      is CarAppMapIntent.Pin -> vm.startNavigation(mapIntent.location, mapIntent.label)
      is CarAppMapIntent.Search ->
          carContext.getCarService(ScreenManager::class.java).push(ViceCarSearchScreen(carContext))
      else -> Unit
    }
  }
}
