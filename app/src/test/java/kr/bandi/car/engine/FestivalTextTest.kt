package kr.bandi.car.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalTextTest {

    @Test
    fun 잘린_소개_글은_끝난_문장까지만_쓴다() {
        assertEquals("정원이다.", FestivalText.wholeSentences("정원이다. 연꽃문화제는 세미원의 대표 식"))
        assertEquals("끝이 없는 글…", FestivalText.wholeSentences("끝이 없는 글"))
    }

    @Test
    fun 프로그램_줄글을_항목으로_나눈다() {
        val items = FestivalText.programItems("- 주요 프로그램 : 남산 나이트 워킹 - 부대 행사 및 프로그램 : 워밍업 스트레칭 / 라이브 공연")
        assertEquals(listOf("주요 프로그램: 남산 나이트 워킹", "부대 행사 및 프로그램: 워밍업 스트레칭", "라이브 공연"), items)
    }

    @Test
    fun 주소를_짧게_줄인다() {
        assertEquals("서울 중구 백범광장", FestivalText.shortAddr("서울특별 중구 회현동1가 100-115 백범광장"))
    }

    @Test
    fun 앱_자료는_가정_데이터_줄에만_확인_전을_붙인다() {
        val e = Enrich(demo = true, kids = true, pet = false, indoor = true, rainOk = true, stroller = true, barrierFree = false,
            restroom = true, nursing = false, parking = listOf(Parking("p1", "가족 주차장", 5, true, false, Geo(0.0, 0.0), "")))
        val lines = FestivalText.appContext(fest(e)).lines()
        assertTrue(lines.contains("기간: 2026-06-26 ~ 2026-08-17"))
        assertTrue(lines.contains("운영 시간: 08:00~20:00"))
        assertTrue(lines.any { it.startsWith("주차장: 가족 주차장") && it.endsWith("(확인 전)") })
        assertTrue(lines.contains("편의시설: 유모차, 화장실, 실내 행사 (확인 전)"))
        assertFalse(lines.first { it.startsWith("기간") }.contains("확인 전"))
    }

    @Test
    fun 앱_자료는_2000자를_넘지_않는다() {
        assertTrue(FestivalText.appContext(fest(null).copy(program = "가 / ".repeat(3000))).length <= 2000)
    }

    private fun fest(e: Enrich?) = Festival(
        id = "2550263", title = "세미원 연꽃문화제", addr = "경기도 양평군 양서면 양수로 93 세미원", start = "2026-06-26", end = "2026-08-17",
        geo = Geo(0.0, 0.0), hours = "08:00~20:00", price = "유료", overview = "", program = "", thumb = "", enrich = e,
    )
}
