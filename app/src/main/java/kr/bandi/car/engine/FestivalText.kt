package kr.bandi.car.engine

/**
 * TourAPI 줄글(소개, 프로그램)을 화면과 서버 질문에 맞게 다듬는다.
 * 소리로 읽을 문장은 만들지 않는다. Gleo(LLM)와 트래블젠 서버가 근거를 보고 답을 만든다.
 */
object FestivalText {

    /** 표본 소개 글은 220자에서 잘린 것이 많다. 마지막으로 끝난 문장까지만 쓴다. 끝난 문장이 없으면 원문에 줄임표를 붙인다. */
    fun wholeSentences(text: String): String {
        val t = text.trim()
        if (t.isEmpty() || t.last() in ".!?") return t
        val cut = t.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (cut > 0) t.substring(0, cut + 1) else "$t…"
    }

    /** 소개 글 앞부분. 첫 두 문장까지만 쓴다. */
    fun intro(overview: String): String? =
        wholeSentences(overview).split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            .take(2).joinToString(" ").ifBlank { null }

    /** TourAPI 프로그램 줄글을 항목으로 나눈다: "- 주요 프로그램 : A / B" → [주요 프로그램: A, B] */
    fun programItems(program: String): List<String> {
        if (program.isBlank()) return emptyList()
        return program
            .split(Regex("\\s+/\\s+|\\s+-\\s+|^-\\s+|\\s*\\d\\.\\s+"))
            .map { it.trim().replace(Regex("\\s*:\\s*"), ": ") }
            .filter { it.length >= 2 }
            .take(8)
    }

    /** "서울특별 중구 회현동1가 100-115 백범광장" → "서울 중구 백범광장" */
    fun shortAddr(addr: String): String {
        val parts = addr.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return addr
        val city = parts[0].replace(Regex("(특별자치도|특별자치시|특별시|광역시|특별|도)$"), "")
        val district = parts.getOrNull(1)
        val place = parts.drop(2).lastOrNull { !it.any(Char::isDigit) && !it.endsWith("동") && !it.endsWith("로") && !it.endsWith("길") && !it.endsWith("면") && !it.endsWith("읍") }
        return listOfNotNull(city, district, place).joinToString(" ")
    }

    /**
     * 서버(/api/car/ask)에 함께 보낼 앱 자료. 서버의 웹 상세 정보에 빠진 것(기간 등)과 앱에만 있는 것(주차장, 편의시설)을 채운다.
     * 가정 데이터(enrich.demo)에서 온 줄에는 "(확인 전)"을 붙인다. 서버는 그 줄을 쓸 때 확인 전이라고 말한다.
     */
    fun appContext(f: Festival, maxLength: Int = 2000): String {
        val e = f.enrich
        val unverified = if (e?.demo == true) " (확인 전)" else ""
        val lines = buildList {
            add("행사: ${f.title}")
            add("장소: ${f.addr}")
            if (f.start.isNotBlank() || f.end.isNotBlank()) add("기간: ${f.start} ~ ${f.end}")
            if (f.hours.isNotBlank()) add("운영 시간: ${f.hours}")
            if (f.price.isNotBlank()) add("요금: ${f.price}")
            programItems(f.program).takeIf { it.isNotEmpty() }?.let { add("프로그램: ${it.joinToString(" / ")}") }
            e?.parking?.takeIf { it.isNotEmpty() }?.let { list ->
                add("주차장: " + list.joinToString(" / ") { "${it.name}, 행사장까지 걸어서 ${it.walkMin}분" + if (it.stroller) ", 유모차 이동 가능" else "" } + unverified)
            }
            e?.let {
                val items = listOfNotNull(
                    "유모차".takeIf { _ -> it.stroller }, "수유실".takeIf { _ -> it.nursing }, "화장실".takeIf { _ -> it.restroom },
                    "휠체어".takeIf { _ -> it.barrierFree }, "반려동물 동반".takeIf { _ -> it.pet }, "실내 행사".takeIf { _ -> it.indoor },
                )
                if (items.isNotEmpty()) add("편의시설: ${items.joinToString(", ")}$unverified")
            }
        }
        return lines.joinToString("\n").take(maxLength)
    }
}
