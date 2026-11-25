package dev.vicemaps.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import uniffi.ferrostar.GeographicCoordinate

data class Place(
    val name: String,
    val detail: String,
    val coordinate: GeographicCoordinate,
)

/**
 * Keyless place search using Photon, an OpenStreetMap geocoder, at [endpoint].
 * Results are biased towards [near] when given.
 */
class PhotonSearch(private val client: OkHttpClient, private val endpoint: String) {
  private val json = Json { ignoreUnknownKeys = true }

  suspend fun search(query: String, near: GeographicCoordinate?, limit: Int = 8): List<Place> =
      withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url =
            endpoint.toHttpUrl().newBuilder().apply {
              addQueryParameter("q", query.trim())
              addQueryParameter("limit", limit.toString())
              addQueryParameter("lang", "en")
              near?.let {
                addQueryParameter("lat", "%.5f".format(java.util.Locale.US, it.lat))
                addQueryParameter("lon", "%.5f".format(java.util.Locale.US, it.lng))
              }
            }.build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
          if (!response.isSuccessful) error("Search failed (${response.code})")
          parse(response.body.string())
        }
      }

  internal fun parse(body: String): List<Place> =
      json.parseToJsonElement(body).jsonObject["features"]?.jsonArray.orEmpty().mapNotNull { f ->
        val obj = f.jsonObject
        val coords = obj["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
        val lng = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val p = obj["properties"]?.jsonObject ?: JsonObject(emptyMap())
        fun s(key: String) = p[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        val street = listOfNotNull(s("housenumber"), s("street")).joinToString(" ").ifBlank { null }
        val name = s("name") ?: street ?: s("city") ?: return@mapNotNull null
        val detail =
            listOfNotNull(street.takeIf { it != name }, s("district"), s("city"), s("state"), s("country"))
                .distinct()
                .filter { it != name }
                .joinToString(", ")
        Place(name, detail, GeographicCoordinate(lat, lng))
      }
}
