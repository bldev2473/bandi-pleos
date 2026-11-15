package kr.bandi.car.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.bandi.car.R
import kr.bandi.car.engine.Judge
import kr.bandi.car.engine.Verdict
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 트래블젠 웹앱(travelgen.kr) 토큰. 기준: docs/pleos-design-system.md
 * 웹의 app/app/globals.css 값을 그대로 쓰고, 차량에서 명암비가 모자란 글자색만 더했다.
 */
object T {
    val ink = Color(0xFF111111)
    val slate = Color(0xFF6B7280)
    val mist = Color(0xFFF6F6F6)
    val clay = Color(0xFFEEF2F1)
    val white = Color(0xFFFFFFFF)
    val moss = Color(0xFF6EC2BF)
    val river = Color(0xFF5CA19F)
    val ember = Color(0xFFD07A5C)
    val line = Color(0x1A111111)
    val lineStrong = Color(0x26111111)

    // 차량 앱에서 더한 글자색
    val mossDeep = Color(0xFF2F7F7C)
    val emberDeep = Color(0xFFB85A3C)
    val mossTint = Color(0x2E6EC2BF)
}

val CardShape = RoundedCornerShape(24.dp)
val Pill = RoundedCornerShape(999.dp)

// ---------- 글자 ----------
@Composable
fun Display(text: String, color: Color = T.ink, size: Int = 44, maxLines: Int = 2) = Text(
    text, color = color, fontSize = size.sp, lineHeight = (size * 1.25).sp,
    fontWeight = FontWeight.Bold, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
)

@Composable
fun Lead(text: String, color: Color = T.ink) =
    Text(text, color = color, fontSize = 36.sp, lineHeight = 50.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun Body(text: String, color: Color = T.ink, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) = Text(
    text, color = color, fontSize = 26.sp, lineHeight = 38.sp, modifier = modifier,
    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
)

@Composable
fun Caption(text: String, color: Color = T.slate, modifier: Modifier = Modifier) =
    Text(text, color = color, fontSize = 20.sp, lineHeight = 28.sp, modifier = modifier)

// ---------- 아이콘과 그림 ----------
@Composable
fun Lucide(@DrawableRes id: Int, tint: Color = T.ink, size: Dp = 28.dp) =
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = Modifier.size(size))

@Composable
fun Fluent(@DrawableRes id: Int, size: Dp, modifier: Modifier = Modifier) =
    Image(painterResource(id), contentDescription = null, modifier = modifier.size(size))

// ---------- 버튼 ----------
@Composable
fun RowScope.PrimaryButton(label: String, @DrawableRes icon: Int? = null, sub: String? = null, weight: Float = 1.5f, onClick: () -> Unit) =
    ButtonBase(label, icon, sub, weight, primary = true, onClick = onClick)

@Composable
fun RowScope.SecondaryButton(label: String, @DrawableRes icon: Int? = null, sub: String? = null, weight: Float = 1f, onClick: () -> Unit) =
    ButtonBase(label, icon, sub, weight, primary = false, onClick = onClick)

@Composable
private fun RowScope.ButtonBase(label: String, icon: Int?, sub: String?, weight: Float, primary: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg = when {
        primary && pressed -> T.river
        primary -> T.moss
        pressed -> T.mist
        else -> T.white
    }
    Row(
        Modifier
            .weight(weight)
            .heightIn(min = 100.dp)
            .clip(Pill)
            .background(bg)
            .then(if (primary) Modifier else Modifier.border(1.dp, T.lineStrong, Pill))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Lucide(it, T.ink, 30.dp); Spacer(Modifier.width(14.dp)) }
        Column {
            Text(label, color = T.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            sub?.let { Text(it, color = if (primary) T.ink.copy(alpha = 0.7f) else T.slate, fontSize = 17.sp, maxLines = 1) }
        }
    }
}

/**
 * 뒤로가기. 웹앱 BackToListButton(흰 알약, ink/15 테두리, arrow-left + 글자)을 차량 크기로 키웠다.
 * 화면 왼쪽 위에 고정한다. 기준: docs/pleos-design-system.md "뒤로가기"
 */
@Composable
fun BackButton(label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        Modifier
            .heightIn(min = 72.dp)
            .clip(Pill)
            .background(if (pressed) T.mist else T.white)
            .border(1.dp, T.lineStrong, Pill)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(start = 22.dp, end = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lucide(R.drawable.lucide_arrow_left, T.ink, 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, color = T.ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------- 배지 ----------
data class BadgeStyle(val label: String, val bg: Color, val fg: Color, @DrawableRes val icon: Int)

fun badgeOf(v: Verdict): BadgeStyle = when (v) {
    Verdict.REACHABLE -> BadgeStyle("볼 수 있어요", T.moss, T.ink, R.drawable.lucide_check)
    Verdict.NEEDS_CHARGING -> BadgeStyle("충전하고 가면 볼 수 있어요", T.white, T.mossDeep, R.drawable.lucide_battery_charging)
    Verdict.ENDS_BEFORE_ARRIVAL -> BadgeStyle("도착 전에 끝나요", T.ember, T.ink, R.drawable.lucide_clock)
    Verdict.NOT_TODAY -> BadgeStyle("오늘은 열리지 않아요", T.ember, T.ink, R.drawable.lucide_calendar_x)
    Verdict.TIME_UNKNOWN -> BadgeStyle("운영 시각이 공지되지 않았어요", T.white, T.slate, R.drawable.lucide_circle_help)
}

@Composable
fun VerdictBadge(v: Verdict) {
    val s = badgeOf(v)
    Row(
        Modifier.clip(Pill).background(s.bg).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lucide(s.icon, s.fg, 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(s.label, color = s.fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- 카드와 정보 줄 ----------
fun Modifier.card(): Modifier = this
    .clip(CardShape)
    .background(T.white)
    .border(1.dp, T.line, CardShape)

/** 웹 상세 화면의 "정보" 카드 줄: 회색 칸 안 아이콘, 이름, 값 */
@Composable
fun InfoRow(@DrawableRes icon: Int, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(T.clay), contentAlignment = Alignment.Center) {
            Lucide(icon, T.slate, 26.dp)
        }
        Spacer(Modifier.width(18.dp))
        Column {
            Caption(label)
            Body(value, maxLines = 2)
        }
    }
}

// ---------- 시간 띠 ----------
/** 시간 띠 위 자동차: 안 그림, 지금에서 도착까지 한 번 달림(정차 중 화면), 지금 자리에 둠(주행 중 화면) */
enum class StripCar { NONE, TRIP, LIVE }

/**
 * 지금, 도착, 운영 구간을 한 줄에 놓는다. 이 앱의 판단 "가면 볼 수 있나"를 그림으로 보여 준다.
 * 시각은 모두 자정부터 센 분이다.
 */
@Composable
fun TimeStrip(
    windows: List<Judge.Window>,
    nowMin: Int?,
    arrivalMin: Int?,
    reachable: Boolean,
    bandLabel: String?,
    modifier: Modifier = Modifier,
    car: StripCar = StripCar.NONE,
) {
    val points = windows.flatMap { listOf(it.start, it.end) } + listOfNotNull(nowMin, arrivalMin)
    if (points.isEmpty()) return
    val startH = (points.min() / 60).coerceAtLeast(0)
    val endH = ((points.max() + 59) / 60).coerceAtLeast(startH + 2)
    val from = startH * 60
    val span = (endH - startH) * 60f
    val step = when (endH - startH) { in 0..6 -> 1; in 7..12 -> 2; else -> 3 }
    fun frac(m: Int) = ((m - from) / span).coerceIn(0f, 1f)

    // TRIP: 화면이 열릴 때 한 번만 지금 → 도착으로 달린다. 반복하지 않는다(운전 중 주의를 끌지 않게).
    // LIVE: 지금 자리에 두고, 시간이 지나 자리가 바뀔 때만 부드럽게 옮긴다.
    val trip = remember(nowMin, arrivalMin) { androidx.compose.animation.core.Animatable(0f) }
    if (car == StripCar.TRIP) LaunchedEffect(nowMin, arrivalMin) {
        trip.animateTo(1f, androidx.compose.animation.core.tween(1400, delayMillis = 250, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val liveFrac by androidx.compose.animation.core.animateFloatAsState(nowMin?.let { frac(it) } ?: 0f, androidx.compose.animation.core.tween(800), label = "car")
    val carFrac: Float? = when (car) {
        StripCar.NONE -> null
        StripCar.LIVE -> nowMin?.let { liveFrac }
        StripCar.TRIP -> if (nowMin != null && arrivalMin != null) frac(nowMin) + (frac(arrivalMin) - frac(nowMin)) * trip.value else null
    }

    Column(modifier.fillMaxWidth()) {
    // 운영 구간 이름은 띠 안이 아니라 위쪽 범례 줄에 둔다. 띠 위에 "지금", 아래에 "도착"만 두어 서로 겹치지 않게 한다.
    bandLabel?.let { label ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 28.dp, height = 12.dp).clip(Pill).background(if (reachable) T.moss else T.lineStrong))
            Spacer(Modifier.width(10.dp))
            Text(label, color = if (reachable) T.mossDeep else T.slate, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(170.dp)) {
        val w = maxWidth
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val top = 64.dp.toPx()
            val h = 20.dp.toPx()
            val r = CornerRadius(h / 2, h / 2)
            drawRoundRect(T.clay, Offset(0f, top), Size(size.width, h), r)
            windows.forEach { win ->
                val x0 = frac(win.start) * size.width
                val x1 = frac(win.end) * size.width
                if (reachable) {
                    drawRoundRect(T.moss, Offset(x0, top), Size((x1 - x0).coerceAtLeast(h), h), r)
                } else {
                    clipRect(x0, top, x1, top + h) {
                        drawRoundRect(T.lineStrong, Offset(x0, top), Size(x1 - x0, h), r)
                        var x = x0 - h
                        while (x < x1) {
                            drawLine(T.slate.copy(alpha = 0.55f), Offset(x, top + h), Offset(x + h, top), 2.dp.toPx())
                            x += 10.dp.toPx()
                        }
                    }
                }
            }
            if (nowMin != null && arrivalMin != null && arrivalMin > nowMin) {
                drawLine(
                    T.ink, Offset(frac(nowMin) * size.width, top + h / 2), Offset(frac(arrivalMin) * size.width, top + h / 2),
                    3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }
            nowMin?.let {
                val x = frac(it) * size.width
                drawLine(T.ink, Offset(x, 30.dp.toPx()), Offset(x, top + h), 3.dp.toPx())
            }
            arrivalMin?.let {
                val x = frac(it) * size.width
                val c = Offset(x, top + h / 2)
                drawCircle(T.white, 22.dp.toPx(), c)
                drawCircle(if (reachable) T.ink else T.ember, 16.dp.toPx(), c)
            }
        }
        fun xOf(m: Int): Dp = w * frac(m)
        nowMin?.let { StripLabel("지금 ${clock(it)}", xOf(it), 0.dp, w, T.ink) }
        arrivalMin?.let { StripLabel("도착 ${clock(it)}", xOf(it), 100.dp, w, if (reachable) T.ink else T.emberDeep) }
        // 자동차: Fluent 3D Automobile. 원본은 왼쪽을 보므로 좌우를 뒤집어 진행 방향(오른쪽)을 보게 한다.
        carFrac?.let { f ->
            val size = 60.dp
            Image(
                painterResource(R.drawable.fluent_automobile), contentDescription = null,
                modifier = Modifier
                    .offset(x = (w * f - size / 2).coerceIn(0.dp, w - size), y = 18.dp)
                    .size(size)
                    .graphicsLayer { scaleX = -1f },
            )
        }
        // 눈금
        for (hh in startH..endH step step) {
            val x = w * ((hh - startH) * 60 / span)
            Text(
                hourLabel(hh), color = T.slate, fontSize = 18.sp,
                modifier = Modifier.offset(x = (x - 20.dp).coerceIn(0.dp, w - 40.dp), y = 140.dp).width(40.dp),
                textAlign = when (hh) { startH -> androidx.compose.ui.text.style.TextAlign.Start; endH -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Center },
            )
        }
    }
    }
}

@Composable
private fun StripLabel(text: String, x: Dp, y: Dp, width: Dp, color: Color, alignEnd: Boolean = false) {
    // 글자 폭을 모르니 넉넉한 칸을 잡고, 가장자리에서는 안쪽으로 붙인다.
    val box = 220.dp
    val left = if (alignEnd) (x - box).coerceIn(0.dp, width - box) else (x - box / 2).coerceIn(0.dp, width - box)
    val align = when {
        alignEnd -> androidx.compose.ui.text.style.TextAlign.End
        x - box / 2 < 0.dp -> androidx.compose.ui.text.style.TextAlign.Start
        x + box / 2 > width -> androidx.compose.ui.text.style.TextAlign.End
        else -> androidx.compose.ui.text.style.TextAlign.Center
    }
    Text(
        text, color = color, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, textAlign = align,
        modifier = Modifier.offset(x = left, y = y).width(box).wrapContentWidth(
            when (align) {
                androidx.compose.ui.text.style.TextAlign.Start -> Alignment.Start
                androidx.compose.ui.text.style.TextAlign.End -> Alignment.End
                else -> Alignment.CenterHorizontally
            },
        ),
    )
}

/** 시간 띠용 짧은 시각: 오후 5:09 → 5:09 */
fun clock(minOfDay: Int): String {
    val h = (minOfDay / 60) % 24
    val hh = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d".format(hh, minOfDay % 60)
}

private fun hourLabel(h: Int): String {
    val x = h % 24
    return "${if (x % 12 == 0) 12 else x % 12}시"
}

// ---------- 원격 사진 ----------
private val photoCache = ConcurrentHashMap<String, Bitmap>()

/**
 * TourAPI 사진을 받아 보여 준다. 받지 못하면 바탕색만 남는다.
 * 원본(originimgurl)은 수 MB라서 긴 변 1600px 안쪽으로 줄여 읽는다.
 */
@Composable
fun RemotePhoto(url: String, modifier: Modifier = Modifier, fit: Boolean = false, background: Color = T.clay) {
    var bmp by remember(url) { mutableStateOf(photoCache[url]) }
    LaunchedEffect(url) {
        if (bmp != null || url.isBlank()) return@LaunchedEffect
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url.replace("http://", "https://")).openConnection() as HttpURLConnection
                c.connectTimeout = 5_000
                c.readTimeout = 8_000
                val bytes = c.inputStream.use { it.readBytes() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()?.also { photoCache[url] = it }
        }
    }
    Box(modifier.background(background)) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = if (fit) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

@Composable
fun Dot(on: Boolean) = Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) T.moss else T.ember))
