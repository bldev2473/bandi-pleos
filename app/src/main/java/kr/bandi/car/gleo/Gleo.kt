package kr.bandi.car.gleo

import android.content.Context
import android.content.Intent
import android.util.Log
import kr.bandi.car.engine.Rider
import kr.bandi.car.engine.CompanionType
import kr.bandi.car.engine.Theme
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gleo Interface 연동.
 * - 앱 함수는 assets/schema.json(App Schema)에 정의한다. 최대 3개.
 * - Gleo AI가 함수를 부르면 action 이름이 함수 이름인 인텐트가 온다. 인자는 ACTION_PARAM(JSON 문자열)이다.
 * - 처리 결과는 7초 안에 APP_ACTION_RESPONSE 브로드캐스트로 돌려준다.
 */
object Gleo {
    const val SUGGEST = "SUGGEST_FESTIVAL_NOW"
    const val CHECK = "CHECK_FESTIVAL_ARRIVAL"
    const val ROUTE = "START_FESTIVAL_ROUTE"
    val FUNCTIONS = setOf(SUGGEST, CHECK, ROUTE)

    const val EXTRA_PARAM = "ACTION_PARAM"
    const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    private const val TAG = "Bandi/Gleo"

    data class Action(val name: String, val params: JSONObject, val raw: String)

    /** 인텐트에서 Gleo Action을 꺼낸다. 함수 이름이 아니면 null. */
    fun parse(intent: Intent?): Action? {
        val name = intent?.action ?: return null
        if (name !in FUNCTIONS) return null
        val raw = intent.getStringExtra(EXTRA_PARAM) ?: "{}"
        Log.d(TAG, "Gleo Action 수신: $name $raw")
        return Action(name, parseParams(raw), raw)
    }

    /**
     * 공식 예시의 ACTION_PARAM은 바깥에 따옴표가 한 번 더 붙어 있다("\"{...}\"").
     * 따옴표로 감싼 문자열, 이스케이프된 문자열, 일반 JSON을 모두 받는다.
     */
    fun parseParams(raw: String): JSONObject {
        var s = raw.trim()
        repeat(2) {
            if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
                s = s.substring(1, s.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
            }
        }
        return runCatching { JSONObject(s) }.getOrElse {
            Log.w(TAG, "ACTION_PARAM 해석 실패: $raw")
            JSONObject()
        }
    }

    enum class Status(val key: String) { SUCCESS("success"), ASKING("asking"), FAIL("fail") }

    /** Gleo에 Callback을 보낸다. message는 Gleo가 사용자에게 읽어 준다. */
    fun respond(context: Context, functionName: String, status: Status, message: String, data: JSONObject? = null): JSONObject {
        val json = JSONObject().apply {
            put("functionName", functionName)
            put("status", status.key)
            put("message", message)
            data?.let { put("data", it.toString()) }
        }
        runCatching {
            context.sendBroadcast(Intent(RESPONSE_ACTION).apply {
                putExtra("packageName", context.packageName)
                putExtra("response", json.toString())
            })
            Log.d(TAG, "Callback 전송: $json")
        }.onFailure { Log.e(TAG, "Callback 전송 실패: $it") }
        return json
    }

    // ---------- 인자 읽기 ----------
    fun companions(p: JSONObject): List<Rider>? {
        val list = mutableListOf<Rider>()
        val childAge = p.optDouble("withChildAge", Double.NaN)
        if (!childAge.isNaN() && childAge > 0) list += Rider(CompanionType.CHILD, childAge.toInt())
        else if (p.optBoolean("withChild", false)) list += Rider(CompanionType.CHILD)
        if (p.optBoolean("withSenior", false)) list += Rider(CompanionType.SENIOR)
        if (p.optBoolean("withPet", false)) list += Rider(CompanionType.PET)
        if (p.optBoolean("withStroller", false)) list += Rider(CompanionType.STROLLER)
        if (p.optBoolean("withWheelchair", false)) list += Rider(CompanionType.WHEELCHAIR)
        return list.ifEmpty { null }
    }

    fun theme(p: JSONObject): Theme? = Theme.of(p.optString("theme").takeIf { it.isNotBlank() })

    fun intOrNull(p: JSONObject, key: String): Int? = p.optDouble(key, Double.NaN).takeIf { !it.isNaN() && it > 0 }?.toInt()

    /** "21:00" 같은 시각을 하루 중 분으로 바꾼다. */
    fun clockMin(p: JSONObject, key: String): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(p.optString(key).trim()) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    fun stringList(p: JSONObject, key: String): Set<String> {
        val a: JSONArray = p.optJSONArray(key) ?: return emptySet()
        return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.toSet()
    }
}
