package kr.bandi.car.data

import android.content.Context
import kr.bandi.car.engine.Enrich
import kr.bandi.car.engine.Festival
import kr.bandi.car.engine.Geo
import kr.bandi.car.engine.Parking
import org.json.JSONArray
import org.json.JSONObject

/**
 * 축제 데이터. 지금은 앱에 넣은 TourAPI 축제공연행사 표본(assets/festivals.json)을 읽는다.
 * 다음 단계에서 TravelGen 서버 API로 바꾼다.
 */
object FestivalRepository {
    @Volatile
    private var cache: List<Festival>? = null

    fun load(context: Context): List<Festival> {
        cache?.let { return it }
        val text = context.assets.open("festivals.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(text).also { cache = it }
    }

    fun parse(text: String): List<Festival> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i -> festival(arr.getJSONObject(i)) }
    }

    private fun festival(o: JSONObject) = Festival(
        id = o.getString("id"),
        title = o.optString("title"),
        addr = o.optString("addr"),
        start = o.optString("start"),
        end = o.optString("end"),
        geo = Geo(o.getDouble("lat"), o.getDouble("lon")),
        hours = o.optString("hours"),
        price = o.optString("price"),
        overview = o.optString("overview"),
        program = o.optString("program"),
        thumb = o.optString("thumb"),
        enrich = o.optJSONObject("enrich")?.let { enrich(it) },
    )

    private fun enrich(o: JSONObject): Enrich {
        val parking = o.optJSONArray("parking") ?: JSONArray()
        return Enrich(
            demo = o.optBoolean("demo", true),
            kids = o.optBoolean("kids"),
            pet = o.optBoolean("pet"),
            indoor = o.optBoolean("indoor"),
            rainOk = o.optBoolean("rainOk"),
            stroller = o.optBoolean("stroller"),
            barrierFree = o.optBoolean("barrierFree"),
            restroom = o.optBoolean("restroom"),
            nursing = o.optBoolean("nursing"),
            parking = (0 until parking.length()).map { i ->
                val p = parking.getJSONObject(i)
                Parking(
                    id = p.getString("id"),
                    name = p.optString("name"),
                    walkMin = p.optInt("walkMin", 5),
                    stroller = p.optBoolean("stroller"),
                    shuttle = p.optBoolean("shuttle"),
                    geo = Geo(p.getDouble("lat"), p.getDouble("lon")),
                    note = p.optString("note"),
                )
            },
        )
    }
}
