package kr.bandi.car.engine

/** 위도, 경도 */
data class Geo(val lat: Double, val lon: Double)

data class Parking(
    val id: String,
    val name: String,
    val walkMin: Int,
    val stroller: Boolean,
    val shuttle: Boolean,
    val geo: Geo,
    val note: String,
)

/**
 * TourAPI에 없는 편의 정보. 실서비스에서는 LLM 추출과 주최 측 검수로 채운다.
 * 지금 들어 있는 값은 시연용 가정 데이터다(demo = true).
 */
data class Enrich(
    val demo: Boolean,
    val kids: Boolean,
    val pet: Boolean,
    val indoor: Boolean,
    val rainOk: Boolean,
    val stroller: Boolean,
    val barrierFree: Boolean,
    val restroom: Boolean,
    val nursing: Boolean,
    val parking: List<Parking>,
)

data class Festival(
    val id: String,
    val title: String,
    val addr: String,
    val start: String, // yyyy-MM-dd
    val end: String,
    val geo: Geo,
    val hours: String,
    val price: String,
    val overview: String,
    val program: String,
    val thumb: String,
    val enrich: Enrich?,
) {
    val shortTitle: String
        get() = title.replace(Regex("^20\\d\\d\\s*"), "").replace(Regex("\\s*\\(.*?\\)\\s*"), " ").trim()
}

enum class CompanionType { CHILD, SENIOR, PET, STROLLER, WHEELCHAIR }

data class Rider(val type: CompanionType, val age: Int? = null)

enum class Theme(val key: String) {
    FIREWORKS("fireworks"), NIGHT("night"), FLOWER("flower"), FOOD("food"),
    MUSIC("music"), TRADITIONAL("traditional"), EXPERIENCE("experience"), EXHIBIT("exhibit");

    companion object {
        fun of(key: String?): Theme? = entries.firstOrNull { it.key == key }
    }
}

enum class Verdict(val key: String) {
    REACHABLE("reachable"),
    NEEDS_CHARGING("needs_charging"),
    ENDS_BEFORE_ARRIVAL("ends_before_arrival"),
    TIME_UNKNOWN("time_unknown"),

    /** 오늘이 기간 밖이거나 쉬는 날. 시각과 관계없이 볼 수 없다. */
    NOT_TODAY("not_today"),
}
