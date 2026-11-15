package kr.bandi.car.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import android.content.Context
import android.util.Log
import kr.bandi.car.BuildConfig
import java.util.Locale

/**
 * 앱이 먼저 말할 때(진입 구간 주차 안내, 플랜 B 질문, 도착 안내) 쓰는 음성.
 * Gleo가 부른 함수의 답은 Callback의 message를 Gleo가 읽으므로 여기서 말하지 않는다.
 *
 * Pleos TextToSpeech SDK를 먼저 쓴다. Client ID, Secret이 있으면 서버 모드로 등록하고,
 * 없거나 실패하면 안드로이드 기본 TextToSpeech로 대신한다.
 */
class Speaker(private val context: Context) {
    private var pleos: TextToSpeech? = null
    private var pleosReady = false
    private var fallback: android.speech.tts.TextToSpeech? = null
    private var fallbackReady = false
    var lastEngine: String = "없음"
        private set

    fun start() {
        val hasKeys = BuildConfig.PLEOS_CLIENT_ID.isNotBlank() && BuildConfig.PLEOS_CLIENT_SECRET.isNotBlank()
        runCatching {
            val tts = TextToSpeech(context.applicationContext, if (hasKeys) Mode.SERVER else Mode.ON_DEVICE)
            pleos = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.setLocale(Locale.KOREAN)
                    if (hasKeys) {
                        tts.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET, object : OnServerConnectionListener {
                            override fun onConnected() {
                                pleosReady = true
                            }

                            override fun onFailed(type: ErrorType) {
                                Log.w(TAG, "Pleos TTS 서버 등록 실패: $type")
                            }
                        })
                    } else {
                        pleosReady = true
                    }
                }

                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "Pleos TTS 초기화 실패: $type")
                }
            })
        }.onFailure { Log.w(TAG, "Pleos TTS 생성 실패: ${it.message}") }

        fallback = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
            fallbackReady = status == android.speech.tts.TextToSpeech.SUCCESS
            if (fallbackReady) fallback?.language = Locale.KOREAN
        }
    }

    fun speak(text: String) {
        if (pleosReady) {
            val ok = runCatching { pleos?.speak(text, true) }.isSuccess
            if (ok) {
                lastEngine = "Pleos TextToSpeech"
                return
            }
        }
        if (fallbackReady) {
            fallback?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "travelgen")
            lastEngine = "Android TTS"
        }
    }

    fun stop() {
        runCatching { pleos?.stop() }
        fallback?.stop()
    }

    fun release() {
        runCatching { pleos?.release() }
        fallback?.shutdown()
    }

    companion object {
        private const val TAG = "Bandi/Speaker"
    }
}
