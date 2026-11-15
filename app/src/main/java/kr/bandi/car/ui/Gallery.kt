package kr.bandi.car.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.bandi.car.R
import kr.bandi.car.engine.Festival
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 행사 사진 목록. 트래블젠 웹의 공개 API(GET /api/events/{contentid}/images)를 그대로 쓴다.
 * 웹 축제 상세의 "추가 이미지"와 같은 출처(TourAPI detailImage2)다.
 * 받기 전이거나 실패하면 표본의 대표 사진(thumb) 한 장만 쓴다.
 */
object Photos {
    private const val API = "https://travelgen.kr/api/events/%s/images"
    private val cache = ConcurrentHashMap<String, List<String>>()

    fun cached(f: Festival): List<String> = cache[f.id] ?: listOfNotNull(f.thumb.ifBlank { null })

    suspend fun load(f: Festival): List<String> {
        cache[f.id]?.let { return it }
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(API.format(f.id)).openConnection() as HttpURLConnection
                c.connectTimeout = 5_000
                c.readTimeout = 8_000
                val body = c.inputStream.use { it.readBytes().decodeToString() }
                val arr = JSONObject(body).optJSONArray("images")
                (0 until (arr?.length() ?: 0)).map { arr!!.getString(it) }
            }.getOrDefault(emptyList())
        }
        // 대표 사진을 맨 앞에 두고, 주소가 같은 사진은 한 번만 쓴다.
        val merged = (listOf(f.thumb) + fetched).map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.replace("http://", "https://") }
        if (fetched.isNotEmpty()) cache[f.id] = merged
        return merged
    }
}

@Composable
fun rememberPhotos(f: Festival): List<String> =
    produceState(initialValue = Photos.cached(f), f.id) { value = Photos.load(f) }.value

/** 사진 상세 화면을 여는 데 필요한 것: 사진 목록과 처음 보여 줄 쪽 */
data class PhotoViewer(val title: String, val urls: List<String>, val start: Int)

/**
 * 큰 사진 캐러셀. 옆으로 넘기고, 누르면 사진 상세 화면을 연다.
 * 운전 중에는 넘기기와 열기를 막는다(enabled = false).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCarousel(f: Festival, enabled: Boolean, onOpen: (PhotoViewer) -> Unit, modifier: Modifier = Modifier, overlay: @Composable BoxScope.(page: Int, count: Int) -> Unit = { _, _ -> }) {
    val urls = rememberPhotos(f)
    val pager = rememberPagerState { urls.size.coerceAtLeast(1) }
    Box(modifier) {
        HorizontalPager(state = pager, userScrollEnabled = enabled, modifier = Modifier.fillMaxSize()) { page ->
            RemotePhoto(
                urls.getOrElse(page) { "" },
                Modifier.fillMaxSize().then(if (enabled) Modifier.clickable { onOpen(PhotoViewer(f.shortTitle, urls, page)) } else Modifier),
            )
        }
        overlay(pager.currentPage, urls.size)
    }
}

/** 쪽 표시: 점과 "3 / 9". 사진이 한 장이면 그리지 않는다. */
@Composable
fun PageDots(page: Int, count: Int, modifier: Modifier = Modifier, onDark: Boolean = true) {
    if (count <= 1) return
    Row(
        modifier.clip(Pill).background(Color(0x66000000)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shown = count.coerceAtMost(7)
        val first = (page - shown / 2).coerceIn(0, count - shown)
        for (i in first until first + shown) {
            Box(Modifier.size(if (i == page) 12.dp else 8.dp).clip(CircleShape).background(if (i == page) Color.White else Color(0x99FFFFFF)))
        }
        Spacer(Modifier.width(4.dp))
        Text("${page + 1} / $count", color = if (onDark) Color.White else T.ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 사진 상세 화면. 사진을 자르지 않고 전체로 보여 준다.
 * 왼쪽 위 "사진 닫기"와 안드로이드 뒤로가기 키로 닫는다. 기준: docs/pleos-design-system.md "사진 상세"
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(v: PhotoViewer, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = v.start) { v.urls.size }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(T.ink)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().padding(horizontal = 120.dp, vertical = 120.dp)) { page ->
            RemotePhoto(v.urls[page], Modifier.fillMaxSize(), fit = true, background = Color.Transparent)
        }
        // 닫기: 뒤로가기와 같은 자리, 같은 모양(어두운 바탕용)
        Row(
            Modifier.align(Alignment.TopStart).padding(40.dp).heightIn(min = 72.dp).clip(Pill)
                .background(Color(0x33FFFFFF)).border(1.dp, Color(0x55FFFFFF), Pill)
                .clickable(onClick = onClose).padding(start = 22.dp, end = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Lucide(R.drawable.lucide_arrow_left, Color.White, 28.dp)
            Spacer(Modifier.width(12.dp))
            Text("사진 닫기", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            v.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp),
        )
        if (v.urls.size > 1) {
            NavArrow(R.drawable.lucide_chevron_left, pager.currentPage > 0, Modifier.align(Alignment.CenterStart).padding(start = 32.dp)) {
                scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
            }
            NavArrow(R.drawable.lucide_chevron_right, pager.currentPage < v.urls.size - 1, Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) {
                scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
            PageDots(pager.currentPage, v.urls.size, Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp))
        }
    }
}

@Composable
private fun NavArrow(icon: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(88.dp).clip(CircleShape).background(Color(if (enabled) 0x33FFFFFF else 0x14FFFFFF))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Lucide(icon, Color.White.copy(alpha = if (enabled) 1f else 0.35f), 44.dp) }
}
