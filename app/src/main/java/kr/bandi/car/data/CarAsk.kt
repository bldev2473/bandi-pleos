package kr.bandi.car.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.bandi.car.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 트래블젠 서버의 차량 전용 행사 질문(POST /api/car/ask, RAG).
 * 서버가 행사 문서와 앱 자료를 근거로 짧은 답을 만든다. Gleo는 이 답을 바탕으로 최종 답을 말한다.
 * 키(local.properties의 travelgen.carKey)가 없거나 실패하면 null을 돌려준다. 그때는 앱 안 데이터로 답한다.
 */
object CarAsk {
    private const val TAG = "Bandi/CarAsk"
    private const val TIMEOUT_MS = 8_000

    data class Answer(val text: String, val grounded: Boolean, val sources: List<String>)

    val configured: Boolean get() = BuildConfig.TRAVELGEN_CAR_KEY.isNotBlank()

    suspend fun ask(contentId: String, question: String, appContext: String): Answer? {
        if (!configured) {
            Log.i(TAG, "키가 없어 서버에 묻지 않음")
            return null
        }
        Log.i(TAG, "서버에 질문: contentId=$contentId")
        return withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(BuildConfig.TRAVELGEN_API_BASE.trimEnd('/') + "/api/car/ask").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS
                c.doOutput = true
                c.setRequestProperty("content-type", "application/json")
                c.setRequestProperty("authorization", "Bearer ${BuildConfig.TRAVELGEN_CAR_KEY}")
                val body = JSONObject().put("contentId", contentId).put("question", question.take(300)).put("appContext", appContext)
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                if (code != 200) {
                    Log.w(TAG, "서버 응답 $code")
                    return@runCatching null
                }
                val json = JSONObject(c.inputStream.use { it.readBytes().decodeToString() })
                val sources = json.optJSONArray("sources")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("title") } } ?: emptyList()
                Answer(json.optString("answer").trim(), json.optBoolean("grounded"), sources).takeIf { it.text.isNotEmpty() }
                    .also { Log.i(TAG, "서버 답 받음: grounded=${it?.grounded}") }
            }.onFailure { Log.w(TAG, "질문 실패: ${it.message}") }.getOrNull()
        }
    }
}
