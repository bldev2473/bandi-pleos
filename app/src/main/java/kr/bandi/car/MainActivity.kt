package kr.bandi.car

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.bandi.car.gleo.Gleo
import kr.bandi.car.ui.BandiScreen

/**
 * 하나뿐인 화면. 런처로 열리거나, Gleo AI가 App Schema 함수(SUGGEST_FESTIVAL_NOW 등)를 부를 때 인텐트로 열린다.
 * launchMode가 singleTop이라 이미 떠 있으면 onNewIntent로 받는다.
 */
class MainActivity : ComponentActivity() {
    private val controller by lazy { AppController.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller.start()
        setContent {
            val ui = controller.ui.collectAsStateWithLifecycle().value
            BandiScreen(ui, controller)
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == DEV_ACTION) return dev(intent)
        Gleo.parse(intent)?.let { controller.handle(it) }
    }

    /**
     * 시연 영상을 스크립트로 녹화할 때 화면을 누르지 않고 개발 도구를 켠다. debug 빌드에서만 받는다.
     * 예: adb shell am start -n kr.bandi.car/.MainActivity -a kr.bandi.car.DEV --ez demoClock true
     */
    private fun dev(intent: Intent) {
        // 시연 시각은 release에서도 받는다. 표본 행사가 7월에 몰려 있어 심사 때 이 시각으로 봐야 흐름이 보인다.
        if (intent.hasExtra("demoClock")) controller.setDemoClock(intent.getBooleanExtra("demoClock", false))
        if (!BuildConfig.DEBUG) return
        if (intent.hasExtra("rain")) controller.setRain(intent.getBooleanExtra("rain", false))
        if (intent.getBooleanExtra("homeHere", false)) controller.setHomeHere()
        if (intent.getBooleanExtra("arrive", false)) controller.devArrive()
    }

    private companion object {
        const val DEV_ACTION = "kr.bandi.car.DEV"
    }
}
