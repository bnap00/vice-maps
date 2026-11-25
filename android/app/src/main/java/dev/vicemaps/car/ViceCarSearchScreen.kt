package dev.vicemaps.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import dev.vicemaps.AppModule
import dev.vicemaps.search.Place
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Car search: type (when parked) or use the car keyboard/voice, pick a result to drive there. */
class ViceCarSearchScreen(carContext: CarContext) : Screen(carContext) {
  private var results: List<Place> = emptyList()
  private var loading = false
  private var job: Job? = null

  override fun onGetTemplate(): Template {
    val list =
        ItemList.Builder()
            .apply {
              if (!loading && results.isEmpty()) setNoItemsMessage("Search for a place or address")
              results.take(6).forEach { place ->
                addItem(
                    Row.Builder()
                        .setTitle(place.name)
                        .apply { if (place.detail.isNotBlank()) addText(place.detail) }
                        .setOnClickListener {
                          AppModule.viewModel.startNavigation(place.coordinate, place.name)
                          screenManager.pop()
                        }
                        .build()
                )
              }
            }
            .build()

    return SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
              override fun onSearchTextChanged(searchText: String) = search(searchText, debounce = true)

              override fun onSearchSubmitted(searchText: String) = search(searchText, debounce = false)
            }
        )
        .setHeaderAction(Action.BACK)
        .setSearchHint("Where to?")
        .setShowKeyboardByDefault(true)
        .setLoading(loading)
        .apply { if (!loading) setItemList(list) }
        .build()
  }

  private fun search(text: String, debounce: Boolean) {
    job?.cancel()
    if (text.trim().length < 2) {
      results = emptyList()
      loading = false
      invalidate()
      return
    }
    job =
        lifecycleScope.launch {
          if (debounce) delay(500)
          loading = true
          invalidate()
          results = AppModule.viewModel.runSearch(text)
          loading = false
          invalidate()
        }
  }
}
