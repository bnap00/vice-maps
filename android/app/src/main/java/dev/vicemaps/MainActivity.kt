package dev.vicemaps

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import dev.vicemaps.ui.MapScreen
import dev.vicemaps.ui.theme.ViceTheme
import java.util.Locale
import uniffi.ferrostar.createFerrostarLogger

class MainActivity : ComponentActivity(), AndroidTtsStatusListener {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppModule.init(this)

    // Spoken turn instructions.
    AppModule.ttsObserver.statusObserver = this
    AppModule.ferrostarCore.spokenInstructionObserver = AppModule.ttsObserver
    createFerrostarLogger()

    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    window.isNavigationBarContrastEnforced = false

    setContent { ViceTheme { MapScreen(AppModule.viewModel) } }
  }

  override fun onStart() {
    super.onStart()
    AppModule.ttsObserver.start()
  }

  override fun onDestroy() {
    super.onDestroy()
    // Keep speaking if Android Auto is still navigating; otherwise release the engine.
    if (!AppModule.viewModel.navigationUiState.value.isNavigating()) AppModule.ttsObserver.shutdown()
  }

  override fun onTtsInitialized(tts: TextToSpeech?, status: Int) {
    if (tts != null) {
      val result = tts.setLanguage(Locale.getDefault())
      if (result < TextToSpeech.LANG_AVAILABLE) tts.language = Locale.US
    } else {
      Log.e(TAG, "TTS setup failed: $status")
    }
  }

  override fun onTtsSpeakError(utteranceId: String, status: Int) {
    Log.e(TAG, "TTS error for $utteranceId: $status")
  }

  override fun onTtsShutdownAndRelease() = Unit

  companion object {
    private const val TAG = "ViceMain"
  }
}
