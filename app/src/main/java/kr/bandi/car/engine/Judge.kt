package kr.bandi.car.engine

import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 판단 엔진. 실서비스에서는 TravelGen 서버가 맡을 로직을 앱 안에서 돌린다.
 * - 운영 시간 줄글 해석(신뢰도 포함)
 * - 도착 가능성(주행 가능 거리), 관람 가능성(도착 예정 시각과 운영 시각 비교)
 * - 시간 예산 역산, 동행 조건
 * 후보별 거리, 시간은 직선거리 x 1.3과 구간별 평균 속도로 어림한다.
 * 안내를 시작한 뒤에는 NaviHelper가 주는 남은 시간으로 다시 판단한다.
 */
object Judge {
    private const val DEFAULT_STAY_MIN = 90
    private const val MIN_STAY_MIN = 40
    private const val SESSION_MIN = 60
    private const val CHARGE_MIN = 20

    // ---------- 거리, 시간 ----------
    fun haversineKm(a: Geo, b: Geo): Double {
        val p = Math.PI / 180
        val dLat = (b.lat - a.lat) * p
        val dLon = (b.lon - a.lon) * p
        val x = sin(dLat / 2).let { it * it } + cos(a.lat * p) * cos(b.lat * p) * sin(dLon / 2).let { it * it }
        return 2 * 6371 * asin(sqrt(x))
    }

    fun roadKm(a: Geo, b: Geo) = haversineKm(a, b) * 1.3

    fun driveMin(km: Double): Int {
        val speed = when {
            km < 15 -> 26.0
            km < 50 -> 42.0
            else -> 68.0
        }
        return (km / speed * 60 + 4).roundToInt()
    }

    // ---------- 운영 시간 해석 ----------
    enum class HoursKind { RANGE, SESSIONS, UNKNOWN }

    data class Window(val start: Int, val end: Int)

    data class Hours(
        val kind: HoursKind,
        val windows: List<Window>,
        val closedToday: Boolean,
        val confidence: Double,
        val source: String,
    )

    private const val DAYS = "일월화수목금토"

    private fun dowIndex(d: DayOfWeek) = d.value % 7 // 일=0

    private fun toMin(h: String, m: String?) = h.toInt() * 60 + (m?.toIntOrNull() ?: 0)

    fun parseHours(text: String, now: LocalDateTime): Hours {
        val src = text.trim()
        var t = src
        val month = now.monthValue
        val dow = dowIndex(now.dayOfWeek)

        if (t.contains("하절기") && t.contains("동절기")) {
            val parts = t.split("동절기")
            t = if (month in 3..9) parts[0] else parts.getOrElse(1) { parts[0] }
        }

        var closedToday = false
        val closed = Regex("(?:매주\\s*)?([월화수목금토일])요일(?:은)?\\s*(?:휴무|휴관|공연\\s*없음)").find(t)
        if (closed != null && DAYS.indexOf(closed.groupValues[1]) == dow) closedToday = true

        val openDays = Regex("매주\\s*([월화수목금토일](?:요일)?(?:\\s*[,/]\\s*[월화수목금토일](?:요일)?)*)").find(t)
        if (openDays != null && closed == null) {
            val days = Regex("[월화수목금토일]").findAll(openDays.groupValues[1]).map { DAYS.indexOf(it.value) }.toList()
            if (dow !in days) closedToday = true
        } else {
            val slash = Regex("([월화수목금토일])요일\\s*/\\s*([월화수목금토일])요일").find(t)
            if (slash != null) {
                val days = listOf(DAYS.indexOf(slash.groupValues[1]), DAYS.indexOf(slash.groupValues[2]))
                if (dow !in days) closedToday = true
            }
        }
        Regex("([월화수목금토일])요일\\s*~\\s*([월화수목금토일])요일").find(t)?.let {
            val a = DAYS.indexOf(it.groupValues[1])
            val b = DAYS.indexOf(it.groupValues[2])
            val inRange = if (a <= b) dow in a..b else dow >= a || dow <= b
            if (!inRange) closedToday = true
        }

        val ranges = Regex("(\\d{1,2})\\s*:\\s*(\\d{2})\\s*~\\s*(\\d{1,2})\\s*:\\s*(\\d{2})").findAll(t)
            .map { Window(toMin(it.groupValues[1], it.groupValues[2]), toMin(it.groupValues[3], it.groupValues[4])) }
            .toList()
        if (ranges.isNotEmpty()) {
            return Hours(HoursKind.RANGE, ranges, closedToday, if (ranges.size > 2) 0.7 else 0.9, src)
        }

        val sessions = Regex("(\\d{1,2})\\s*:\\s*(\\d{2})").findAll(t)
            .map { toMin(it.groupValues[1], it.groupValues[2]) }
            .filter { it < 24 * 60 }
            .map { Window(it, it + SESSION_MIN) }
            .toList()
        if (sessions.isEmpty()) {
            Regex("(\\d{1,2})시\\s*~\\s*(\\d{1,2})시").find(t)?.let {
                return Hours(HoursKind.RANGE, listOf(Window(toMin(it.groupValues[1], null), toMin(it.groupValues[2], null))), closedToday, 0.8, src)
            }
        }
        if (sessions.isNotEmpty()) return Hours(HoursKind.SESSIONS, sessions, closedToday, 0.75, src)
        return Hours(HoursKind.UNKNOWN, emptyList(), closedToday, 0.2, src)
    }

    fun isRunningOn(f: Festival, now: LocalDateTime): Boolean {
        val d = now.toLocalDate().toString()
        return f.start <= d && d <= f.end
    }

    fun themesOf(f: Festival): Set<Theme> {
        val text = "${f.title} ${f.overview} ${f.program} ${f.hours}"
        val out = mutableSetOf<Theme>()
        if (Regex("불꽃|드론|라이트쇼|레이저|빛|나이트|야경|야간|해상.?쇼|나오라").containsMatchIn(text)) out += Theme.NIGHT
        if (Regex("불꽃|드론").containsMatchIn(text)) out += Theme.FIREWORKS
        if (Regex("공연|콘서트|음악|버스킹|라이브|연극|국악").containsMatchIn(text)) out += Theme.MUSIC
        if (Regex("전통|수문장|파수|봉수|풍물|국악|한옥|연희").containsMatchIn(text)) out += Theme.TRADITIONAL
        if (Regex("체험|만들기|키즈|어린이").containsMatchIn(text)) out += Theme.EXPERIENCE
        if (Regex("꽃|연꽃|정원").containsMatchIn(text)) out += Theme.FLOWER
        if (Regex("맥주|먹거리|음식|푸드|꿀맥|치맥").containsMatchIn(text)) out += Theme.FOOD
        if (Regex("전시|아트|미술|갤러리|아시아프").containsMatchIn(text)) out += Theme.EXHIBIT
        return out
    }

    // ---------- 판단 ----------
    data class Context(
        val now: LocalDateTime,
        val origin: Geo,
        val home: Geo,
        val rangeKm: Double,
        val companions: List<Rider> = emptyList(),
        val rain: Boolean = false,
        val theme: Theme? = null,
        val timeBudgetMin: Int? = null,
        val mustEndByMin: Int? = null,
    )

    data class Slot(val wait: Int, val left: Int)

    data class Result(
        val festival: Festival,
        val km: Double,
        val driveMin: Int,
        val backDriveMin: Int,
        val arrivalMin: Int,
        val hours: Hours,
        val themes: Set<Theme>,
        val verdict: Verdict,
        val slot: Slot?,
        val needsCharging: Boolean,
        val stayMin: Int,
        val homeAtMin: Int,
        val reasons: List<String>,
        val blockers: List<String>,
        val score: Double,
    ) {
        val ok: Boolean
            get() = blockers.isEmpty() && (verdict == Verdict.REACHABLE || verdict == Verdict.NEEDS_CHARGING)
    }

    fun minuteOfDay(t: LocalDateTime) = t.hour * 60 + t.minute

    /** 도착 시각(하루 중 분)에서 볼 수 있는 시간대를 찾는다. */
    fun slotAt(hours: Hours, arrival: Int): Slot? {
        for (w in hours.windows) {
            if (hours.kind == HoursKind.RANGE) {
                if (arrival <= w.end - 30) return Slot(max(0, w.start - arrival), w.end - max(arrival, w.start))
            } else if (w.start >= arrival - 5) {
                return Slot(max(0, w.start - arrival), SESSION_MIN)
            }
        }
        return null
    }

    fun evaluate(f: Festival, ctx: Context): Result {
        val nowMin = minuteOfDay(ctx.now)
        val km = roadKm(ctx.origin, f.geo)
        val backKm = roadKm(f.geo, ctx.home)
        var drive = driveMin(km)
        val backDrive = driveMin(backKm)
        val hours = parseHours(f.hours, ctx.now)
        val e = f.enrich
        val has = { type: CompanionType -> ctx.companions.any { it.type == type } }
        val themes = themesOf(f)
        val blockers = mutableListOf<String>()

        val runningToday = isRunningOn(f, ctx.now)
        if (!runningToday) blockers += "오늘은 열리지 않아요"
        if (hours.closedToday) blockers += "오늘은 쉬는 날이에요"
        val openToday = runningToday && !hours.closedToday
        if (has(CompanionType.PET) && e?.pet != true) blockers += "반려동물 동반 정보가 없어요"
        if (has(CompanionType.WHEELCHAIR) && e != null && !e.barrierFree) blockers += "휠체어 이동이 어려워요"
        if (has(CompanionType.STROLLER) && e != null && !e.stroller) blockers += "유모차로 다니기 어려워요"
        if (ctx.rain && e?.rainOk != true) blockers += "비가 오면 즐기기 어려워요"
        if (ctx.theme != null && ctx.theme !in themes) blockers += "찾는 분위기와 달라요"

        val needsCharging = ctx.rangeKm < (km + backKm) * 1.1
        if (needsCharging) drive += CHARGE_MIN
        val arrival = nowMin + drive

        val slot = if (!openToday || hours.kind == HoursKind.UNKNOWN) null else slotAt(hours, arrival)
        val verdict = when {
            !openToday -> Verdict.NOT_TODAY
            hours.kind == HoursKind.UNKNOWN -> Verdict.TIME_UNKNOWN
            slot == null -> Verdict.ENDS_BEFORE_ARRIVAL
            needsCharging -> Verdict.NEEDS_CHARGING
            else -> Verdict.REACHABLE
        }

        val stay = max(MIN_STAY_MIN, slot?.let { min(DEFAULT_STAY_MIN, it.wait + it.left) } ?: DEFAULT_STAY_MIN)
        val homeAt = arrival + stay + backDrive
        if (ctx.timeBudgetMin != null && drive + stay + backDrive > ctx.timeBudgetMin + 10) blockers += "남은 시간 안에 다녀오기 빠듯해요"
        if (ctx.mustEndByMin != null && homeAt > ctx.mustEndByMin) blockers += "정한 시각까지 돌아오기 어려워요"
        if (slot != null && slot.wait > 120) blockers += "도착하고 너무 오래 기다려야 해요"
        if (km * 1.1 > ctx.rangeKm + 120) blockers += "너무 멀어요"

        val reasons = mutableListOf<String>()
        if (has(CompanionType.CHILD) && e?.kids == true) reasons += "아이가 즐길 체험이 있어요"
        if (has(CompanionType.SENIOR) && e?.barrierFree == true) reasons += "어르신도 걷기 편해요"
        if (has(CompanionType.PET) && e?.pet == true) reasons += "강아지와 함께 들어갈 수 있어요"
        if (ctx.rain && e?.rainOk == true) reasons += "실내라 비가 와도 괜찮아요"
        if (f.price.contains("무료")) reasons += "입장료가 없어요"
        e?.parking?.firstOrNull()?.let { reasons += "가족 주차장에서 걸어서 ${it.walkMin}분이에요" }

        var score = 100 - km * 0.6
        if (has(CompanionType.CHILD) && e?.kids == true) score += 25
        if (ctx.theme != null && ctx.theme in themes) score += 30
        if (e != null) score += 15
        if (slot != null) score += min(30.0, slot.left / 6.0) - slot.wait / 4.0
        if (needsCharging) score -= 12
        if (hours.confidence < 0.6) score -= 20

        return Result(f, km, drive, backDrive, arrival, hours, themes, verdict, slot, needsCharging, stay, homeAt, reasons, blockers, score)
    }

    data class Stats(val considered: Int, val endsBeforeArrival: Int, val timeUnknown: Int, val blocked: Int)

    data class Suggestion(val best: Result?, val rest: List<Result>, val stats: Stats)

    fun suggest(festivals: List<Festival>, ctx: Context, exclude: Set<String> = emptySet()): Suggestion {
        val near = festivals.asSequence()
            .filter { it.id !in exclude }
            .map { evaluate(it, ctx) }
            .filter { it.km < 180 && isRunningOn(it.festival, ctx.now) }
            .toList()
        val ok = near.filter { it.ok }.sortedByDescending { it.score }
        val stats = Stats(
            considered = near.size,
            endsBeforeArrival = near.count { it.verdict == Verdict.ENDS_BEFORE_ARRIVAL },
            timeUnknown = near.count { it.verdict == Verdict.TIME_UNKNOWN },
            blocked = near.count { it.blockers.isNotEmpty() },
        )
        return Suggestion(ok.firstOrNull(), ok.drop(1).take(3), stats)
    }

    private fun normalize(s: String) = s.replace(Regex("[^\\p{L}\\p{N}]"), "").replace(Regex("^20\\d\\d"), "").lowercase()

    fun findByName(festivals: List<Festival>, name: String, ctx: Context): Result? {
        val n = normalize(name)
        if (n.isEmpty()) return null
        // 1) 제목 전체가 겹치는 행사를 먼저 찾는다.
        var hits = festivals.filter {
            val t = normalize(it.title)
            t.isNotEmpty() && (t.contains(n) || n.contains(t))
        }
        // 2) 없으면 제목의 핵심 단어(3자 이상, 숫자나 일반 단어 제외)로 찾는다.
        if (hits.isEmpty()) {
            hits = festivals.filter {
                it.shortTitle.split(Regex("\\s+"))
                    .map { w -> normalize(w) }
                    .filter { w -> w.length >= 3 && !w.all(Char::isDigit) && !Regex("축제|행사|문화|페스티벌|공연|전통").containsMatchIn(w) }
                    .any { w -> n.contains(w) }
            }
        }
        return hits.map { evaluate(it, ctx) }.minByOrNull { it.km }
    }

    // ---------- 표현 ----------
    fun fmtDur(minutes: Int): String {
        val m = max(0, minutes)
        if (m < 60) return "${m}분"
        val h = m / 60
        val r = m % 60
        return when {
            r < 10 -> "${h}시간"
            r in 25..35 -> "${h}시간 반"
            else -> "${h}시간 ${r}분"
        }
    }

    fun fmtClock(minOfDay: Int): String {
        val h = (minOfDay / 60) % 24
        val m = minOfDay % 60
        val ap = if (h < 12) "오전" else "오후"
        val hh = if (h % 12 == 0) 12 else h % 12
        return "$ap ${hh}시" + if (m > 0) " ${m}분" else ""
    }

    /** 받침 유무로 조사를 고른다. */
    fun josa(word: String, withBatchim: String, without: String): String {
        val c = word.lastOrNull() ?: return word + without
        if (c.code !in 0xAC00..0xD7A3) return word + without
        return word + if ((c.code - 0xAC00) % 28 != 0) withBatchim else without
    }
}
