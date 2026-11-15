package kr.bandi.car

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.bandi.car.data.FestivalRepository
import kr.bandi.car.engine.Rider
import kr.bandi.car.engine.CompanionType
import kr.bandi.car.engine.Festival
import kr.bandi.car.data.CarAsk
import kr.bandi.car.engine.FestivalText
import kr.bandi.car.engine.Geo
import kr.bandi.car.engine.Judge
import kr.bandi.car.engine.Theme
import kr.bandi.car.engine.Verdict
import kr.bandi.car.gleo.Gleo
import kr.bandi.car.navi.NaviBridge
import kr.bandi.car.vehicle.VehicleMonitor
import kr.bandi.car.voice.Speaker
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * 앱 전체 상태와 흐름.
 * Gleo Action 처리 → 판단 엔진 → Callback, NaviHelper 안내, Vehicle 상태(기어, 주행 가능 거리)에 따른 화면 전환.
 */
class AppController(private val context: Context) {

    enum class Mode { IDLE, SUGGEST, CHECK, NONE, NAV, ARRIVED, PARKED, HOME }

    data class Question(val text: String, val yes: String, val no: String, val onYes: () -> Unit, val onNo: () -> Unit)

    data class LogEntry(val time: String, val kind: String, val title: String, val body: String?)

    data class HomePlan(val driveMin: Int, val restSuggested: Boolean, val drivenMin: Int)

    /** Gleo로 받은 행사 질문과 답. 화면 아래에 잠깐 보여 준다. */
    data class InfoAnswer(val title: String, val question: String, val text: String, val at: Long = System.currentTimeMillis())

    data class Ui(
        val mode: Mode = Mode.IDLE,
        val current: Judge.Result? = null,
        val candidates: List<Judge.Result> = emptyList(),
        val index: Int = 0,
        val alternative: Judge.Result? = null,
        val fromProfile: Boolean = false,
        val profile: List<Rider> = listOf(Rider(CompanionType.CHILD, 7)),
        val stats: Judge.Stats? = null,
        val vehicle: VehicleMonitor.State = VehicleMonitor.State(),
        val navi: NaviBridge.State = NaviBridge.State(),
        val liveLine: String? = null,
        val question: Question? = null,
        val homePlan: HomePlan? = null,
        val navTarget: String? = null,
        val parkingLine: String? = null,
        val log: List<LogEntry> = emptyList(),
        val now: LocalDateTime = LocalDateTime.now(),
        val demoClock: Boolean = false,
        val rain: Boolean = false,
        val useDemoParking: Boolean = false,
        val ttsEngine: String = "-",
        val toast: String? = null,
        val info: InfoAnswer? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val vehicle = VehicleMonitor(context)
    val navi = NaviBridge(context)
    private val speaker = Speaker(context)
    private val festivals: List<Festival> by lazy { FestivalRepository.load(context) }

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    // 시각: 기본은 실제 시각. 시연 때는 표본 데이터가 열리는 날짜로 옮길 수 있다.
    private var demoBase: LocalDateTime? = null
    private var demoSetAt: Long = 0L
    private var home: Geo = DEFAULT_HOME
    private var homeFixed = false
    private var overrides = mutableMapOf<String, String>()
    private var approachSaid = false
    private var planBAsked = false
    private var deferredQuestion: Question? = null
    private var started = false

    fun now(): LocalDateTime = demoBase?.plusSeconds((System.currentTimeMillis() - demoSetAt) / 1000) ?: LocalDateTime.now()

    fun start() {
        if (started) return
        started = true
        vehicle.start()
        navi.start()
        speaker.start()
        scope.launch { vehicle.state.collect { onVehicle(it) } }
        scope.launch { navi.state.collect { onNavi(it) } }
        scope.launch { navi.events.collect { onNaviEvent(it) } }
        scope.launch {
            while (true) {
                _ui.update { it.copy(now = now(), ttsEngine = speaker.lastEngine) }
                navi.refresh()
                delay(5_000)
            }
        }
        log("SYSTEM", "앱 시작", "축제 ${festivals.size}건 로드")
    }

    fun stop() {
        vehicle.stop()
        navi.stop()
        speaker.release()
        started = false
    }

    // ---------- 판단 문맥 ----------
    private fun origin(): Geo = navi.state.value.location ?: home

    private fun rangeKm(): Double = _ui.value.vehicle.rangeKm
        ?: _ui.value.vehicle.socPercent?.let { it * 4.0 }
        ?: 300.0

    private fun data(): List<Festival> = festivals.map { f -> overrides[f.id]?.let { f.copy(hours = it) } ?: f }

    private fun ctx(
        companions: List<Rider>? = null,
        theme: Theme? = null,
        rain: Boolean? = null,
        timeBudget: Int? = null,
        mustEndBy: Int? = null,
        origin: Geo = origin(),
    ) = Judge.Context(
        now = now(),
        origin = origin,
        home = home,
        rangeKm = rangeKm(),
        companions = companions ?: _ui.value.profile,
        rain = rain ?: _ui.value.rain,
        theme = theme,
        timeBudgetMin = timeBudget,
        mustEndByMin = mustEndBy,
    )

    // ---------- Gleo Action ----------
    fun handle(action: Gleo.Action) {
        log("ACTION", action.name, action.params.toString(1))
        when (action.name) {
            Gleo.SUGGEST -> suggest(action.params, viaGleo = true)
            Gleo.CHECK -> check(action.params.optString("festivalName"), viaGleo = true, question = action.params.optString("question"))
            Gleo.ROUTE -> route(action.params, viaGleo = true)
        }
    }

    private fun reply(viaGleo: Boolean, fn: String, status: Gleo.Status, message: String, data: JSONObject? = null) {
        if (viaGleo) {
            val json = Gleo.respond(context, fn, status, message, data)
            log("CALLBACK", "$fn ${status.key}", json.toString(1))
        } else {
            speaker.speak(message)
        }
    }

    fun suggest(p: JSONObject, viaGleo: Boolean) {
        val companions = Gleo.companions(p)
        val fromProfile = companions == null && _ui.value.profile.isNotEmpty()
        val exclude = Gleo.stringList(p, "excludeContentIds").toMutableSet()
        val another = p.optBoolean("anotherOption", false)
        val s = _ui.value
        if (another && s.candidates.isNotEmpty() && s.index + 1 < s.candidates.size) {
            val next = s.candidates[s.index + 1]
            _ui.update { it.copy(mode = Mode.SUGGEST, current = next, index = s.index + 1) }
            reply(viaGleo, Gleo.SUGGEST, Gleo.Status.ASKING, "그럼 ${sentence(next, false)}", resultData(next))
            return
        }
        if (another) s.candidates.take(s.index + 1).forEach { exclude += it.festival.id }
        val c = ctx(
            companions = companions,
            theme = Gleo.theme(p),
            rain = if (p.optBoolean("rainSafe", false)) true else null,
            timeBudget = Gleo.intOrNull(p, "timeBudgetMinutes"),
            mustEndBy = Gleo.clockMin(p, "returnByTime"),
        )
        val res = Judge.suggest(data(), c, exclude)
        val best = res.best
        if (best == null) {
            _ui.update { it.copy(mode = Mode.NONE, stats = res.stats, current = null, candidates = emptyList()) }
            reply(viaGleo, Gleo.SUGGEST, Gleo.Status.FAIL, "지금 조건에 맞는 축제가 근처에 없어요.", JSONObject().put("stats", statsJson(res.stats)))
            return
        }
        _ui.update {
            it.copy(mode = Mode.SUGGEST, current = best, candidates = listOf(best) + res.rest, index = 0, stats = res.stats, fromProfile = fromProfile, alternative = null)
        }
        val prefix = if (fromProfile) "${profileText()} 기준으로 골랐어요. " else ""
        reply(viaGleo, Gleo.SUGGEST, Gleo.Status.ASKING, prefix + sentence(best, true), resultData(best).put("stats", statsJson(res.stats)))
    }

    fun check(name: String, viaGleo: Boolean, question: String = "") {
        val r = Judge.findByName(data(), name, ctx(companions = emptyList()))
        if (r == null) {
            reply(viaGleo, Gleo.CHECK, Gleo.Status.FAIL, "그 행사를 찾지 못했어요.")
            return
        }
        if (question.isNotBlank()) {
            askAbout(r, question.trim(), viaGleo)
            return
        }
        val title = r.festival.shortTitle
        var alt: Judge.Result? = null
        val (status, msg) = when {
            r.verdict == Verdict.NOT_TODAY || r.verdict == Verdict.ENDS_BEFORE_ARRIVAL -> {
                alt = Judge.suggest(data(), ctx(companions = emptyList(), theme = if (Theme.NIGHT in r.themes) Theme.NIGHT else null), setOf(r.festival.id)).best
                val base = when {
                    r.verdict == Verdict.ENDS_BEFORE_ARRIVAL ->
                        "아쉽지만 늦어요. 지금 출발하면 ${Judge.josa(title, "이", "가")} 끝난 뒤에 도착해요."
                    r.hours.closedToday && Judge.isRunningOn(r.festival, now()) ->
                        "${Judge.josa(title, "은", "는")} 오늘 쉬는 날이에요."
                    else ->
                        "${Judge.josa(title, "은", "는")} 오늘 열리지 않아요. 기간은 ${r.festival.start}부터 ${r.festival.end}까지예요."
                }
                if (alt != null) Gleo.Status.ASKING to "$base 대신 ${Judge.josa(alt.festival.shortTitle, "은", "는")} 아직 볼 수 있어요. 그쪽으로 갈까요?"
                else Gleo.Status.SUCCESS to base
            }
            r.verdict == Verdict.TIME_UNKNOWN ->
                Gleo.Status.SUCCESS to "${Judge.josa(title, "은", "는")} 운영 시각이 프로그램마다 달라서 지금은 판단하기 어려워요."
            r.slot != null && r.slot.wait > 0 ->
                Gleo.Status.ASKING to "네, 볼 수 있어요. 시작 ${Judge.fmtDur(r.slot.wait)} 전에 도착해요." +
                    (if (r.needsCharging) " 다만 가는 길에 충전이 한 번 필요해요." else "") + " 갈까요?"
            else ->
                Gleo.Status.ASKING to "네, 볼 수 있어요. 도착하면 끝나기까지 ${Judge.fmtDur(r.slot?.left ?: 0)} 남아요. 갈까요?"
        }
        _ui.update { it.copy(mode = Mode.CHECK, current = r, candidates = listOf(r), index = 0, alternative = alt, fromProfile = false) }
        val data = resultData(r)
        alt?.let { data.put("alternative", resultData(it)) }
        reply(viaGleo, Gleo.CHECK, status, msg, data)
    }

    /**
     * 행사에 대해 묻는 말(입장료, 프로그램, 주차, 편의시설 등)에 답한다.
     * 트래블젠 서버 RAG(/api/car/ask)에 행사 id, 질문, 앱 자료를 보내고, 받은 답을 Gleo에 돌려준다.
     * 서버를 쓸 수 없으면 앱 자료를 응답 data에 담아 Gleo(LLM)가 그걸로 답하게 한다.
     */
    private fun askAbout(r: Judge.Result, question: String, viaGleo: Boolean) {
        val f = r.festival
        _ui.update { it.copy(mode = Mode.CHECK, current = r, candidates = listOf(r), index = 0, alternative = null, fromProfile = false) }
        val appContext = FestivalText.appContext(f)
        log("SYSTEM", "행사 질문", "${f.shortTitle}: $question")
        scope.launch {
            val answer = CarAsk.ask(f.id, question, appContext)
            val data = resultData(r).put("question", question).put("appContext", appContext)
            if (answer != null) {
                data.put("answer", answer.text).put("grounded", answer.grounded).put("answerSource", "travelgen_rag")
                showInfo(f.shortTitle, question, answer.text)
                reply(viaGleo, Gleo.CHECK, Gleo.Status.SUCCESS, answer.text, data)
            } else {
                // 서버를 못 쓰면 문장은 짧게 두고 근거(appContext)를 data로 넘긴다. Gleo가 그걸로 답을 만든다.
                val msg = "${f.shortTitle}에 대해 앱이 가진 자료를 보내요."
                data.put("answerSource", "app_data")
                showInfo(f.shortTitle, question, "서버에 물어볼 수 없어 앱이 가진 자료로 답해요.")
                reply(viaGleo, Gleo.CHECK, Gleo.Status.SUCCESS, msg, data)
            }
        }
    }

    private fun showInfo(title: String, question: String, text: String) {
        val answer = InfoAnswer(title, question, text)
        _ui.update { it.copy(info = answer) }
        scope.launch {
            delay(INFO_SHOW_MS)
            _ui.update { if (it.info == answer) it.copy(info = null) else it }
        }
    }

    fun clearInfo() = _ui.update { it.copy(info = null) }

    fun route(p: JSONObject, viaGleo: Boolean) {
        when (p.optString("target", "recommended")) {
            "home" -> planHome(viaGleo)
            "named" -> {
                val r = Judge.findByName(data(), p.optString("festivalName"), ctx()) ?: run {
                    reply(viaGleo, Gleo.ROUTE, Gleo.Status.FAIL, "그 행사를 찾지 못했어요.")
                    return
                }
                startFestivalRoute(r, viaGleo)
            }
            else -> {
                val id = p.optString("contentId")
                val s = _ui.value
                val r = when {
                    id.isNotBlank() -> data().firstOrNull { it.id == id }?.let { Judge.evaluate(it, ctx()) }
                    s.mode == Mode.CHECK && s.alternative != null &&
                        (s.current?.verdict == Verdict.ENDS_BEFORE_ARRIVAL || s.current?.verdict == Verdict.NOT_TODAY) -> s.alternative
                    else -> s.current
                }
                if (r == null) {
                    reply(viaGleo, Gleo.ROUTE, Gleo.Status.FAIL, "먼저 갈 축제를 골라 주세요.")
                    return
                }
                startFestivalRoute(r, viaGleo)
            }
        }
    }

    /** 첫 화면, 카드 버튼에서 쓰는 조작(Gleo를 거치지 않을 때). */
    fun accept() = route(JSONObject().put("target", "recommended"), viaGleo = false)

    fun another() = suggest(JSONObject().put("anotherOption", true), viaGleo = false)

    fun goHome() = route(JSONObject().put("target", "home"), viaGleo = false)

    fun dismiss() = _ui.update { it.copy(mode = Mode.IDLE, current = null, candidates = emptyList(), alternative = null) }

    /**
     * 뒤로가기. 어느 화면이든 대기 화면으로 돌아간다.
     * 안내 중, 귀갓길에도 내비 안내는 끝내지 않는다. NaviHelper.cancelRoute()를 부르면 Pleos 내비가 자기 화면을 앞으로 띄우고,
     * 안드로이드가 이 앱이 다시 앞으로 오는 것을 막아(BAL_BLOCK) 뒤로가기가 앱을 빠져나가게 되기 때문이다.
     * 안내는 내비 화면이나 Gleo 음성으로 끝낸다.
     */
    fun back() {
        if (_ui.value.mode != Mode.IDLE) dismiss()
    }

    // ---------- 안내 ----------
    private fun startFestivalRoute(r: Judge.Result, viaGleo: Boolean) {
        val f = r.festival
        if (r.verdict == Verdict.ENDS_BEFORE_ARRIVAL) {
            reply(viaGleo, Gleo.ROUTE, Gleo.Status.FAIL, "지금 가면 ${Judge.josa(f.shortTitle, "이", "가")} 끝나 있어요.")
            return
        }
        if (r.verdict == Verdict.NOT_TODAY) {
            reply(viaGleo, Gleo.ROUTE, Gleo.Status.FAIL, "${Judge.josa(f.shortTitle, "은", "는")} 오늘 열리지 않아요.")
            return
        }
        // 주차장 정보가 검수된 데이터일 때만 주차장으로 안내한다. 지금 데이터는 시연용 가정값이라 기본은 행사장으로 안내한다.
        val parking = f.enrich?.parking?.firstOrNull()?.takeIf { _ui.value.useDemoParking || f.enrich.demo.not() }
        val dest = parking?.let { NaviBridge.Stop("${f.shortTitle} ${it.name}", it.geo, f.addr) } ?: NaviBridge.Stop(f.shortTitle, f.geo, f.addr)
        val ok = navi.startRoute(dest, emptyList())
        approachSaid = false
        planBAsked = false
        deferredQuestion = null
        val parkingLine = parking?.let { "${it.name}, 행사장까지 걸어서 ${it.walkMin}분" }
            ?: f.enrich?.parking?.firstOrNull()?.let { "주차장 정보를 확인하는 중이라 행사장 입구로 안내해요" }
        _ui.update {
            it.copy(mode = Mode.NAV, current = r, navTarget = dest.name, parkingLine = parkingLine, question = null, homePlan = null, liveLine = liveLine(r.slot))
        }
        log("SYSTEM", "NaviHelper.requestRoute", "목적지: ${dest.name} (${dest.geo.lat}, ${dest.geo.lon})\n결과: ${if (ok) "요청함" else "실패(NaviHelper 연결 안 됨)"}")
        val msg = buildString {
            append("${Judge.josa(dest.name, "으로", "로")} 안내할게요.")
            if (r.needsCharging) append(" 가는 길에 충전이 한 번 필요해요. 내비의 충전소 추천을 따라가 주세요.")
        }
        val status = if (ok) Gleo.Status.SUCCESS else Gleo.Status.FAIL
        reply(viaGleo, Gleo.ROUTE, status, if (ok) msg else "내비게이션에 연결하지 못했어요.", resultData(r))
    }

    private fun planHome(viaGleo: Boolean) {
        val o = origin()
        val km = Judge.roadKm(o, home)
        val drive = Judge.driveMin(km)
        val driven = _ui.value.vehicle.tripDrivingMin ?: 0
        val late = Judge.minuteOfDay(now()) >= 21 * 60
        val rest = drive >= 60 && (driven + drive >= 100 || late)
        val ok = navi.startRoute(NaviBridge.Stop("집", home), emptyList())
        approachSaid = true
        _ui.update { it.copy(mode = Mode.HOME, homePlan = HomePlan(drive, rest, driven), navTarget = "집", question = null, liveLine = null) }
        log("SYSTEM", "NaviHelper.requestRoute", "목적지: 집\n결과: ${if (ok) "요청함" else "실패"}")
        var msg = "집까지 ${Judge.fmtDur(drive)} 걸려요."
        if (rest) msg += " 오늘 운전을 오래 하셨으니 중간에 휴게소나 졸음쉼터에서 한 번 쉬어 가세요."
        reply(viaGleo, Gleo.ROUTE, if (ok) Gleo.Status.SUCCESS else Gleo.Status.FAIL, msg, JSONObject().put("driveMinutes", drive).put("restSuggested", rest))
    }

    // ---------- 차량, 내비 상태 ----------
    private fun onVehicle(v: VehicleMonitor.State) {
        val prev = _ui.value.vehicle.gear
        _ui.update { it.copy(vehicle = v) }
        if (prev != v.gear && v.gear == VehicleMonitor.Gear.PARK) {
            log("SYSTEM", "기어 P 감지(Vehicle SDK)", null)
            if (_ui.value.mode == Mode.ARRIVED) {
                _ui.update { it.copy(mode = Mode.PARKED) }
                speaker.speak("휴대폰으로 QR 코드를 찍으면 행사장 안내가 열려요. 즐거운 시간 보내세요.")
            }
        }
    }

    private fun onNavi(n: NaviBridge.State) {
        val first = n.location != null && !homeFixed
        if (first) {
            home = n.location!!
            homeFixed = true
        }
        _ui.update { it.copy(navi = n) }
        val s = _ui.value
        if (s.mode != Mode.NAV) return
        val r = s.current ?: return
        val remainSec = n.remainSec ?: return
        val arrival = Judge.minuteOfDay(now()) + remainSec / 60
        val hours = Judge.parseHours(data().first { it.id == r.festival.id }.hours, now())
        val slot = Judge.slotAt(hours, arrival)
        _ui.update { it.copy(liveLine = liveLine(slot)) }

        val meters = n.remainMeters
        if (meters != null && meters in 1..APPROACH_M && !approachSaid) {
            approachSaid = true
            speaker.speak(s.parkingLine?.let { "곧 도착해요. $it." } ?: "곧 도착해요.")
        }
        if (slot == null && hours.kind != Judge.HoursKind.UNKNOWN && !planBAsked) {
            planBAsked = true
            askEarlyEnd(r)
        }
    }

    private fun onNaviEvent(e: NaviBridge.Event) {
        when (e) {
            is NaviBridge.Event.DestinationArrived -> {
                log("SYSTEM", "onDestinationArrived", e.name)
                if (_ui.value.mode == Mode.NAV) {
                    _ui.update { it.copy(mode = if (_ui.value.vehicle.gear == VehicleMonitor.Gear.PARK) Mode.PARKED else Mode.ARRIVED) }
                    val q = deferredQuestion
                    deferredQuestion = null
                    if (q != null) showQuestion(q) else speaker.speak("도착했어요. 기어를 P로 두면 행사장 안내를 휴대폰으로 넘겨 드려요.")
                } else if (_ui.value.mode == Mode.HOME) {
                    _ui.update { it.copy(mode = Mode.IDLE) }
                    speaker.speak("집에 도착했어요. 오늘도 수고 많으셨어요.")
                }
            }
            is NaviBridge.Event.WaypointArrived -> log("SYSTEM", "onWayPointArrived", e.name)
            NaviBridge.Event.RouteCancelled -> log("SYSTEM", "onRouteCancelled", null)
            NaviBridge.Event.RouteEnded -> log("SYSTEM", "onRouteEnded", null)
        }
    }

    // ---------- 플랜 B ----------
    private fun askEarlyEnd(r: Judge.Result) {
        val alt = Judge.suggest(data(), ctx(), setOf(r.festival.id)).best
        val title = r.festival.shortTitle
        val q = if (alt != null) {
            Question("지금 가면 ${Judge.josa(title, "이", "가")} 끝난 뒤에 도착해요. ${Judge.josa(alt.festival.shortTitle, "으로", "로")} 바꿀까요?", "바꿀게요", "그대로 갈게요",
                onYes = { startFestivalRoute(alt, viaGleo = false) }, onNo = { clearQuestion() })
        } else {
            Question("지금 가면 ${Judge.josa(title, "이", "가")} 끝난 뒤에 도착해요. 근처에 대신 갈 곳이 없어요. 집으로 돌아갈까요?", "집으로 안내", "그대로 갈게요",
                onYes = { planHome(viaGleo = false) }, onNo = { clearQuestion() })
        }
        val meters = navi.state.value.remainMeters
        if (meters != null && meters in 1..APPROACH_M) {
            deferredQuestion = q
            log("SYSTEM", "진입 구간: 재안내를 도착 뒤로 미룸", null)
            return
        }
        showQuestion(q)
    }

    private fun showQuestion(q: Question) {
        _ui.update { it.copy(question = q) }
        speaker.speak(q.text)
    }

    fun answer(yes: Boolean) {
        val q = _ui.value.question ?: return
        _ui.update { it.copy(question = null) }
        if (yes) q.onYes() else q.onNo()
    }

    private fun clearQuestion() = _ui.update { it.copy(question = null) }

    // ---------- 시연, 개발 도구 ----------
    fun setDemoClock(enabled: Boolean) {
        demoBase = if (enabled) DEMO_TIME else null
        demoSetAt = System.currentTimeMillis()
        _ui.update { it.copy(demoClock = enabled, now = now()) }
        log("SYSTEM", if (enabled) "시연 시각 사용: 2026-07-11 16:30" else "실제 시각 사용", null)
    }

    fun setRain(rain: Boolean) = _ui.update { it.copy(rain = rain) }

    fun setUseDemoParking(v: Boolean) = _ui.update { it.copy(useDemoParking = v) }

    fun toggleProfile(type: CompanionType) = _ui.update {
        val has = it.profile.any { c -> c.type == type }
        it.copy(profile = if (has) it.profile.filter { c -> c.type != type } else it.profile + Rider(type, if (type == CompanionType.CHILD) 7 else null))
    }

    fun setHomeHere() {
        navi.state.value.location?.let { home = it; homeFixed = true }
        log("SYSTEM", "현재 위치를 집으로 저장", home.toString())
    }

    /**
     * 시연용: 도착 신호를 개발 도구로 넣는다.
     * Pleos Connect Emulator는 내비 위치가 움직이지 않아 실제 onDestinationArrived가 오지 않는다.
     * 실제 신호와 헷갈리지 않게 기록에 따로 남긴다. 이후 P단 전환은 실제 Vehicle SDK 신호로 처리한다.
     */
    fun devArrive() {
        val name = _ui.value.current?.festival?.shortTitle ?: "목적지"
        log("SYSTEM", "개발 도구로 넣은 도착 신호", "에뮬레이터는 내비 위치가 움직이지 않아 onDestinationArrived를 대신 보냄")
        onNaviEvent(NaviBridge.Event.DestinationArrived(name))
    }

    /** 시연용: 안내 중인 행사가 일찍 끝난다고 가정한다(주최 측 공지 연동 전). */
    fun demoEarlyEnd() {
        val r = _ui.value.current ?: return
        val remain = (navi.state.value.remainSec ?: (r.driveMin * 60)) / 60
        val end = Judge.minuteOfDay(now()) + max(10, remain - 5)
        overrides[r.festival.id] = "00:00~%02d:%02d".format(end / 60, end % 60)
        planBAsked = false
        log("SYSTEM", "시연 이벤트: 행사 일찍 끝남", overrides[r.festival.id])
        navi.refresh()
        onNavi(navi.state.value)
    }

    // ---------- 표현 ----------
    fun profileText(): String = _ui.value.profile.joinToString(", ") {
        when (it.type) {
            CompanionType.CHILD -> it.age?.let { a -> "${a}살 아이" } ?: "아이"
            CompanionType.SENIOR -> "어르신"
            CompanionType.PET -> "강아지"
            CompanionType.STROLLER -> "유모차"
            CompanionType.WHEELCHAIR -> "휠체어"
        }
    }

    private fun sentence(r: Judge.Result, first: Boolean): String {
        val name = r.festival.shortTitle
        val parts = mutableListOf("${Judge.josa(name, "은", "는")} 어때요?")
        when {
            r.needsCharging -> parts += "가는 길에 충전을 한 번 하면 돼요."
            r.slot != null && r.slot.wait > 0 -> parts += "도착하고 ${Judge.fmtDur(r.slot.wait)} 뒤에 시작해요."
            r.slot != null -> parts += "도착하면 ${Judge.fmtDur(r.slot.left)} 동안 볼 수 있어요."
        }
        parts += "갈까요?"
        return parts.joinToString(" ")
    }

    /** 화면의 판단 문장. 계산 결과("마감까지 5시간 51분") 대신 도착 시각과 마감 시각을 그대로 말한다. */
    fun keyLine(r: Judge.Result): String {
        val arrive = "${Judge.fmtClock(r.arrivalMin)}에 도착해요."
        val lastEnd = r.hours.windows.maxOfOrNull { it.end }
        return when {
            r.verdict == Verdict.NOT_TODAY -> "오늘은 열리지 않는 날이에요."
            r.verdict == Verdict.TIME_UNKNOWN -> "$arrive 운영 시각은 공지되지 않았어요."
            r.verdict == Verdict.ENDS_BEFORE_ARRIVAL -> "$arrive " + (lastEnd?.let { "${Judge.fmtClock(it)}에 끝나요." } ?: "그 전에 끝나요.")
            r.slot != null && r.slot.wait > 0 -> "$arrive ${Judge.fmtClock(r.arrivalMin + r.slot.wait)}에 시작해요."
            r.slot != null -> "$arrive ${Judge.fmtClock(r.arrivalMin + r.slot.left)}까지 열려 있어요."
            else -> arrive
        }
    }

    private fun liveLine(slot: Judge.Slot?): String = when {
        slot == null -> "도착하기 전에 끝나요"
        slot.wait > 0 -> "도착하고 ${Judge.fmtDur(slot.wait)} 뒤에 시작해요"
        else -> "도착하면 ${Judge.fmtDur(slot.left)} 동안 볼 수 있어요"
    }

    private fun resultData(r: Judge.Result) = JSONObject().apply {
        put("contentId", r.festival.id)
        put("title", r.festival.title)
        put("verdict", r.verdict.key)
        put("etaMinutes", r.driveMin)
        r.slot?.let { put("waitMinutes", it.wait); put("minutesLeft", it.left) }
        put("hoursConfidence", r.hours.confidence)
        r.reasons.firstOrNull()?.let { put("reason", it) }
        // 이어지는 질문(입장료, 주차 등)에 Gleo가 앱을 다시 부르지 않고도 답할 수 있게 행사 자료를 함께 넘긴다.
        put("details", FestivalText.appContext(r.festival))
    }

    private fun statsJson(s: Judge.Stats) = JSONObject()
        .put("considered", s.considered).put("endsBeforeArrival", s.endsBeforeArrival).put("timeUnknown", s.timeUnknown)

    private fun log(kind: String, title: String, body: String?) {
        val t = now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        _ui.update { it.copy(log = (listOf(LogEntry(t, kind, title, body)) + it.log).take(40)) }
    }

    companion object {
        /** Gleo 답을 화면에 보여 주는 시간 */
        private const val INFO_SHOW_MS = 12_000L
        const val APPROACH_M = 1500
        val DEFAULT_HOME = Geo(37.5393, 127.2148) // 경기 하남
        val DEMO_TIME: LocalDateTime = LocalDateTime.of(2026, 7, 11, 16, 30)

        @Volatile
        private var instance: AppController? = null

        fun get(context: Context): AppController =
            instance ?: synchronized(this) { instance ?: AppController(context.applicationContext).also { instance = it } }
    }
}
