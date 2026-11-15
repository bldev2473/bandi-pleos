package kr.bandi.car.engine

import kr.bandi.car.data.FestivalRepository
import kr.bandi.car.gleo.Gleo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class JudgeTest {
    private val festivals = FestivalRepository.parse(File("src/main/assets/festivals.json").readText())
    private val hanam = Geo(37.5393, 127.2148)
    private val sat = LocalDateTime.of(2026, 7, 11, 16, 30)
    private fun ctx(now: LocalDateTime = sat, range: Double = 288.0, companions: List<Rider> = listOf(Rider(CompanionType.CHILD, 7))) =
        Judge.Context(now = now, origin = hanam, home = hanam, rangeKm = range, companions = companions)

    @Test
    fun parsesRangeHours() {
        val h = Judge.parseHours("08:00~20:00", sat)
        assertEquals(Judge.HoursKind.RANGE, h.kind)
        assertEquals(480, h.windows[0].start)
        assertEquals(1200, h.windows[0].end)
    }

    @Test
    fun parsesSessionsAndClosedDay() {
        val h = Judge.parseHours("11:00 / 14:00※ 매주 월요일 휴무", LocalDateTime.of(2026, 7, 13, 10, 0)) // 월요일
        assertEquals(Judge.HoursKind.SESSIONS, h.kind)
        assertTrue(h.closedToday)
    }

    @Test
    fun unknownHoursAreNotGuessed() {
        assertEquals(Judge.HoursKind.UNKNOWN, Judge.parseHours("공연 별 상이", sat).kind)
    }

    @Test
    fun suggestsFamilyFestivalOnDemoDay() {
        val res = Judge.suggest(festivals, ctx())
        assertNotNull(res.best)
        assertEquals("세미원 연꽃문화제", res.best!!.festival.title)
        assertEquals(Verdict.REACHABLE, res.best!!.verdict)
    }

    @Test
    fun lowRangeNeedsCharging() {
        val r = Judge.evaluate(festivals.first { it.title == "세미원 연꽃문화제" }, ctx(range = 20.0))
        assertEquals(Verdict.NEEDS_CHARGING, r.verdict)
    }

    @Test
    fun lateArrivalEndsBeforeShow() {
        val ok = Judge.findByName(festivals, "나오라쇼", ctx(now = LocalDateTime.of(2026, 7, 11, 18, 10), companions = emptyList()))
        assertEquals(Verdict.REACHABLE, ok!!.verdict)
        val late = Judge.findByName(festivals, "나오라쇼", ctx(now = LocalDateTime.of(2026, 7, 11, 19, 40), companions = emptyList()))
        assertEquals(Verdict.ENDS_BEFORE_ARRIVAL, late!!.verdict)
        assertNull(late.slot)
    }

    @Test
    fun outsideFestivalPeriodIsNotToday() {
        // 시각만 보면 저녁 회차에 맞출 수 있어도, 기간 밖이면 볼 수 없다.
        val r = Judge.findByName(festivals, "나오라쇼", ctx(now = LocalDateTime.of(2026, 11, 9, 14, 30), companions = emptyList()))
        assertEquals(Verdict.NOT_TODAY, r!!.verdict)
        assertNull(r.slot)
    }

    @Test
    fun closedDayIsNotToday() {
        val f = festivals.first { Judge.parseHours(it.hours, sat).kind != Judge.HoursKind.UNKNOWN }
        val monday = LocalDateTime.of(2026, 7, 13, 10, 0)
        val closed = f.copy(hours = "11:00 / 14:00※ 매주 월요일 휴무", start = "2026-07-01", end = "2026-07-31")
        assertEquals(Verdict.NOT_TODAY, Judge.evaluate(closed, ctx(now = monday)).verdict)
    }

    @Test
    fun nameMatchIgnoresYearWords() {
        val r = Judge.findByName(festivals, "나오라쇼", ctx(companions = emptyList()))
        assertEquals("나오라쇼", r!!.festival.title)
    }

    @Test
    fun parsesGleoActionParamWithExtraQuotes() {
        // 공식 문서 예시처럼 바깥에 따옴표가 한 번 더 붙은 경우
        val p = Gleo.parseParams("\"{\"timeBudgetMinutes\":180, \"withChildAge\":7, \"theme\":\"flower\"}\"")
        assertEquals(180, Gleo.intOrNull(p, "timeBudgetMinutes"))
        assertEquals(CompanionType.CHILD, Gleo.companions(p)!!.first().type)
        assertEquals(Theme.FLOWER, Gleo.theme(p))
    }

    @Test
    fun josaPicksParticle() {
        assertEquals("나오라쇼는", Judge.josa("나오라쇼", "은", "는"))
        assertEquals("연꽃문화제는", Judge.josa("연꽃문화제", "은", "는"))
        assertEquals("주차장으로", Judge.josa("주차장", "으로", "로"))
    }
}

