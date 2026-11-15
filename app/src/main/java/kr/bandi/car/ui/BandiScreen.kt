package kr.bandi.car.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kr.bandi.car.AppController
import kr.bandi.car.AppController.Mode
import kr.bandi.car.BuildConfig
import kr.bandi.car.R
import kr.bandi.car.engine.CompanionType
import kr.bandi.car.engine.Festival
import kr.bandi.car.engine.FestivalText
import kr.bandi.car.engine.Judge
import kr.bandi.car.engine.Verdict
import kr.bandi.car.vehicle.VehicleMonitor
import org.json.JSONObject
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 차량 화면. 기준: docs/pleos-design-system.md
 * 정차 중에는 사진과 근거를, 주행 중에는 도착 시각과 한 줄만 크게 보여 준다.
 * 개발 정보(Gleo 연동 기록, SDK 상태, 개발 도구)는 debug 빌드에서만 오른쪽 위 렌치로 연다.
 */
@Composable
fun BandiScreen(ui: AppController.Ui, c: AppController) {
    var devOpen by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<PhotoViewer?>(null) }
    // 운전 중에는 사진 상세를 띄우지 않는다. 열린 채 기어를 바꾸면 닫는다.
    if (ui.driving && viewer != null) viewer = null
    val openPhotos: (PhotoViewer) -> Unit = { if (!ui.driving) viewer = it }
    Box(Modifier.fillMaxSize().background(T.white)) {
        // 뒤로가기 키: 질문이 떠 있으면 아니오, 아니면 화면의 뒤로가기와 같다.
        BackHandler(enabled = ui.mode != Mode.IDLE || ui.question != null) {
            if (ui.question != null) c.answer(false) else c.back()
        }
        Column(Modifier.fillMaxSize().padding(40.dp)) {
            if (ui.mode != Mode.IDLE) {
                BackButton("처음으로") { c.back() }
                Spacer(Modifier.height(24.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when (ui.mode) {
                Mode.IDLE -> Idle(ui, c)
                Mode.SUGGEST, Mode.CHECK -> Recommendation(ui, c, openPhotos)
                Mode.NONE -> NoResult(ui, c)
                Mode.NAV -> Navigating(ui)
                Mode.ARRIVED -> Arrived(ui)
                Mode.PARKED -> Parked(ui, c, openPhotos)
                Mode.HOME -> HomeDrive(ui)
            }
            }
        }
        if (ui.question == null) ui.info?.let { InfoCard(it, Modifier.align(Alignment.BottomCenter)) { c.clearInfo() } }
        ui.question?.let { QuestionSheet(it, c) }
        viewer?.let { PhotoViewerScreen(it) { viewer = null } }
        if (BuildConfig.DEBUG) {
            DevToggle(devOpen, Modifier.align(Alignment.TopEnd).padding(top = 0.dp, end = 0.dp)) { devOpen = !devOpen }
            if (devOpen) DevSheet(ui, c, Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 8.dp, bottom = 8.dp))
        }
    }
}

private val AppController.Ui.driving: Boolean
    get() = vehicle.gear != VehicleMonitor.Gear.PARK && vehicle.gear != VehicleMonitor.Gear.UNKNOWN

private fun nowMin(ui: AppController.Ui) = Judge.minuteOfDay(ui.now)

// ---------- 대기 ----------
@Composable
private fun Idle(ui: AppController.Ui, c: AppController) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Display("어디로\n떠나볼까요?", size = 64)
            Spacer(Modifier.height(16.dp))
            Body("주행 가능 거리와 운영 시각, 함께 탄 사람을 따져 지금 볼 수 있는 곳을 골라 드려요.", T.slate)
            Spacer(Modifier.height(40.dp))
            Caption("Gleo에게 이렇게 물어보세요")
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "“3시간 비는데 갈 만한 축제 있어?”" to { c.suggest(JSONObject().put("timeBudgetMinutes", 180), viaGleo = false) },
                    "“지금 출발하면 나오라쇼 볼 수 있어?”" to { c.check("나오라쇼", viaGleo = false) },
                    "“비 와도 하는 행사 있어?”" to { c.suggest(JSONObject().put("rainSafe", true), viaGleo = false) },
                ).forEach { (label, action) ->
                    Row(
                        Modifier.fillMaxWidth().card().clickable(onClick = action).padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(T.mossTint), contentAlignment = Alignment.Center) {
                            Lucide(R.drawable.lucide_mic, T.mossDeep, 24.dp)
                        }
                        Spacer(Modifier.width(18.dp))
                        Body(label)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
        Column(Modifier.fillMaxWidth().card().padding(36.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Text("지금 이 차", color = T.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Column {
                InfoRow(R.drawable.lucide_clock, if (ui.demoClock) "지금 (시연 시각)" else "지금", ui.now.format(DateTimeFormatter.ofPattern("M월 d일 a h시 m분", Locale.KOREAN)))
                Spacer(Modifier.height(14.dp))
                // 표본 행사는 2026년 7월에 몰려 있다. 실제 날짜로는 대부분 "오늘은 열리지 않아요"라서, 심사와 시연용으로 시각을 옮길 수 있게 한다.
                Chip(
                    if (ui.demoClock) "실제 시각으로 돌아가기" else "시연 시각(7월 11일 오후 4시 30분)으로 보기",
                    on = ui.demoClock,
                    onClick = { c.setDemoClock(!ui.demoClock) },
                )
            }
            InfoRow(R.drawable.lucide_battery_charging, "주행 가능 거리", ui.vehicle.rangeKm?.let { "${it.toInt()}km" } ?: "차에서 받는 중이에요")
            Column {
                InfoRow(R.drawable.lucide_check, "함께 탄 사람", c.profileText().ifBlank { "어른만" })
                Spacer(Modifier.height(16.dp))
                Companions(ui, c)
            }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Companions(ui: AppController.Ui, c: AppController) {
    val editable = !ui.driving
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(CompanionType.CHILD to "7살 아이", CompanionType.SENIOR to "어르신", CompanionType.PET to "강아지", CompanionType.STROLLER to "유모차")
            .forEach { (type, label) ->
                val on = ui.profile.any { it.type == type }
                Chip(label, on, if (editable) ({ c.toggleProfile(type) }) else null)
            }
    }
    if (!editable) {
        Spacer(Modifier.height(10.dp))
        Caption("함께 탄 사람은 정차 중에 바꿀 수 있어요.")
    }
}

/** 웹 왼쪽 메뉴의 선택 상태와 같은 모양: 연한 민트 바탕 + 진한 청록 글자 */
@Composable
private fun Chip(label: String, on: Boolean, onClick: (() -> Unit)?, icon: Int? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) T.mossTint else T.white)
            .then(if (on) Modifier else Modifier.border(1.dp, T.lineStrong, RoundedCornerShape(14.dp)))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 편의정보처럼 뜻이 있는 칩은 그 아이콘을, 고르는 칩은 선택 표시(check)를 붙인다.
        val mark = icon ?: if (on) R.drawable.lucide_check else null
        mark?.let { Lucide(it, if (on) T.mossDeep else T.slate, 22.dp); Spacer(Modifier.width(8.dp)) }
        Text(label, color = if (on) T.mossDeep else T.ink, fontSize = 20.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ---------- 추천, 도착 판단 ----------
@Composable
private fun Recommendation(ui: AppController.Ui, c: AppController, openPhotos: (PhotoViewer) -> Unit) {
    val r = ui.current ?: return
    val f = r.festival
    val canGo = r.verdict == Verdict.REACHABLE || r.verdict == Verdict.NEEDS_CHARGING
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Hero(f, r.verdict, Modifier.weight(0.85f).fillMaxHeight(), "${FestivalText.shortAddr(f.addr)}, ${Judge.fmtDur(r.driveMin)} 걸려요", !ui.driving, openPhotos)
        Column(Modifier.weight(1.15f).fillMaxHeight()) {
            Lead(c.keyLine(r), if (canGo || r.verdict == Verdict.TIME_UNKNOWN) T.ink else T.emberDeep)
            Spacer(Modifier.height(24.dp))
            val alt = ui.alternative
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (r.hours.windows.isNotEmpty() && r.verdict != Verdict.NOT_TODAY) {
                TimeStrip(
                    windows = r.hours.windows, nowMin = nowMin(ui), arrivalMin = r.arrivalMin, reachable = canGo,
                    bandLabel = hoursLabel(r.hours.windows), car = StripCar.TRIP,
                )
                Spacer(Modifier.height(24.dp))
            }
            if (!canGo && alt != null) {
                AlternativeCard(alt)
            } else {
                Column(Modifier.fillMaxWidth().card().padding(32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    FestivalText.intro(f.overview)?.let { text ->
                        Column {
                            Caption("행사 소개")
                            Spacer(Modifier.height(4.dp))
                            Body(text, maxLines = 3)
                        }
                    }
                    if (canGo) {
                        val reasons = r.reasons.filterNot { it.contains("입장료") }.take(3)
                        if (reasons.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            reasons.forEach { reason ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Lucide(R.drawable.lucide_check, T.mossDeep, 28.dp)
                                    Spacer(Modifier.width(14.dp))
                                    Body(reason)
                                }
                            }
                            if (r.needsCharging) Row(verticalAlignment = Alignment.CenterVertically) {
                                Lucide(R.drawable.lucide_battery_charging, T.mossDeep, 28.dp)
                                Spacer(Modifier.width(14.dp))
                                Body("가는 길에 충전을 한 번 해야 해요")
                            }
                        }
                    }
                    if (r.verdict == Verdict.NOT_TODAY) InfoRow(R.drawable.lucide_calendar_x, "행사 기간", spokenPeriod(f))
                    InfoRow(R.drawable.lucide_clock, "운영 시간", spokenHours(r.hours.windows) ?: "공지되지 않았어요")
                    if (f.price.isNotBlank()) InfoRow(R.drawable.lucide_ticket, "요금", priceText(f.price))
                }
                if (ui.fromProfile && ui.mode == Mode.SUGGEST) {
                    Spacer(Modifier.height(14.dp))
                    Caption("${c.profileText()}와 함께라는 조건으로 골랐어요.")
                }
            }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    canGo -> {
                        PrimaryButton("안내 시작", R.drawable.lucide_navigation, "행사장 입구까지") { c.accept() }
                        if (ui.mode == Mode.SUGGEST) {
                            val more = (ui.candidates.size - 1 - ui.index).coerceAtLeast(0)
                            SecondaryButton("다른 곳 보기", R.drawable.lucide_shuffle, if (more > 0) "${more}곳 더" else null) { c.another() }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    alt != null -> {
                        PrimaryButton("${alt.festival.shortTitle}로 안내", R.drawable.lucide_navigation) { c.accept() }
                        Spacer(Modifier.weight(1f))
                    }
                    else -> Unit
                }
            }
        }
    }
}

/** 웹 축제 상세의 머리 사진: 사진 위 어두운 그라데이션, 흰 제목과 주소 */
@Composable
private fun Hero(f: Festival, verdict: Verdict?, modifier: Modifier, place: String, browsable: Boolean, openPhotos: (PhotoViewer) -> Unit) {
    Box(modifier.clip(CardShape)) {
        // 큰 사진은 캐러셀이다. 옆으로 넘기고, 누르면 사진 상세를 연다(정차 중에만).
        PhotoCarousel(f, browsable, openPhotos, Modifier.matchParentSize()) { p, n ->
            PageDots(p, n, Modifier.align(Alignment.TopEnd).padding(24.dp))
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0x99000000)),
            ),
        )
        verdict?.let { Box(Modifier.align(Alignment.TopStart).padding(24.dp)) { VerdictBadge(it) } }
        Column(Modifier.align(Alignment.BottomStart).padding(32.dp)) {
            Display(f.shortTitle, T.white, size = 44, maxLines = 3)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lucide(R.drawable.lucide_map_pin, T.white, 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(place, color = T.white, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AlternativeCard(alt: Judge.Result) {
    Row(Modifier.fillMaxWidth().card().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        RemotePhoto(alt.festival.thumb, Modifier.size(width = 160.dp, height = 120.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.width(24.dp))
        Column {
            Caption("대신 갈 수 있는 곳")
            Text(alt.festival.shortTitle, color = T.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            val end = alt.slot?.let { "${Judge.fmtClock(alt.arrivalMin + it.wait + it.left)}까지 열려 있어요." } ?: ""
            Body("${Judge.fmtDur(alt.driveMin)} 걸려요. $end", T.slate)
        }
    }
}

// ---------- 결과 없음 ----------
@Composable
private fun NoResult(ui: AppController.Ui, c: AppController) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        StateBlock(R.drawable.fluent_magnifying_glass_tilted_left, "지금 볼 수 있는 곳이 없어요") {
            ui.stats?.let {
                Body("근처 ${it.considered}곳을 살폈어요. ${it.endsBeforeArrival}곳은 도착하기 전에 끝나고, 나머지는 함께 탄 사람이나 거리 조건이 맞지 않아요.", T.slate)
            }
        }
    }
}

// ---------- 안내 중 (주행) ----------
@Composable
private fun Navigating(ui: AppController.Ui) {
    val r = ui.current
    val remainMin = ui.navi.remainSec?.let { it / 60 }
    val now = nowMin(ui)
    val arrival = remainMin?.let { now + it } ?: r?.arrivalMin
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        if (arrival != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(clock(arrival), color = T.ink, fontSize = 150.sp, lineHeight = 150.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(24.dp))
                Text(
                    "도착" + (remainMin?.let { ", ${Judge.fmtDur(it)} 남았어요" } ?: ""),
                    color = T.slate, fontSize = 36.sp, modifier = Modifier.padding(bottom = 22.dp),
                )
            }
        } else {
            Display("길 안내 중이에요")
        }
        Text(ui.navTarget ?: "", color = T.ink, fontSize = 32.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(36.dp))
        if (r != null && r.hours.windows.isNotEmpty() && arrival != null) {
            val ok = Judge.slotAt(r.hours, arrival) != null
            TimeStrip(r.hours.windows, now, arrival, ok, hoursLabel(r.hours.windows), car = StripCar.LIVE)
            Spacer(Modifier.height(24.dp))
        }
        ui.liveLine?.let { line ->
            val bad = line.contains("끝나")
            Text(
                line, color = if (bad) T.emberDeep else T.ink, fontSize = 38.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(if (bad) T.ember.copy(alpha = 0.16f) else T.mossTint).padding(horizontal = 36.dp, vertical = 32.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        ui.parkingLine?.let { IconLine(R.drawable.lucide_square_parking, it) }
        if (r?.needsCharging == true) IconLine(R.drawable.lucide_battery_charging, "가는 길에 충전이 한 번 필요해요. 내비의 충전소 추천을 따라가 주세요.")
        Spacer(Modifier.weight(1f))
        VoiceHint("운전 중에는 Gleo에게 말로 물어보세요")
    }
}

@Composable
private fun IconLine(icon: Int, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Lucide(icon, T.mossDeep, 34.dp)
        Spacer(Modifier.width(16.dp))
        Text(text, color = T.slate, fontSize = 28.sp)
    }
}

@Composable
private fun VoiceHint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(T.mossTint), contentAlignment = Alignment.Center) {
            Lucide(R.drawable.lucide_mic, T.mossDeep, 26.dp)
        }
        Spacer(Modifier.width(16.dp))
        Text(text, color = T.slate, fontSize = 24.sp)
    }
}

// ---------- 도착 ----------
@Composable
private fun Arrived(ui: AppController.Ui) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        StateBlock(R.drawable.fluent_chequered_flag, "도착했어요") {
            Body("기어를 P에 두면 행사장 안내를 휴대폰으로 보내 드려요.", T.slate)
        }
        Spacer(Modifier.height(56.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(VehicleMonitor.Gear.PARK, VehicleMonitor.Gear.REVERSE, VehicleMonitor.Gear.NEUTRAL, VehicleMonitor.Gear.DRIVE).forEach { g ->
                val target = g == VehicleMonitor.Gear.PARK
                val now = ui.vehicle.gear == g
                Box(
                    Modifier.size(128.dp).clip(RoundedCornerShape(28.dp))
                        .background(if (target) T.moss else T.white)
                        .border(if (now && !target) 3.dp else 2.dp, if (now && !target) T.ink else if (target) T.moss else T.line, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(g.label, color = if (target || now) T.ink else T.slate, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StateBlock(img: Int, title: String, body: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Fluent(img, 200.dp)
        Spacer(Modifier.width(40.dp))
        Column {
            Display(title, size = 56)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(760.dp)) { body() }
        }
    }
}

// ---------- 주차 후 ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Parked(ui: AppController.Ui, c: AppController, openPhotos: (PhotoViewer) -> Unit) {
    val r = ui.current ?: return
    val f = r.festival
    // 트래블젠 웹의 축제 상세 주소. id는 TourAPI contentid다. from=car는 차에서 넘어온 방문을 구분한다.
    val qr = remember(f.id) { qrBitmap("https://travelgen.kr/ko/events/${f.id}?from=car") }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(
            Modifier.width(520.dp).fillMaxHeight().card().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Image(qr.asImageBitmap(), contentDescription = "행사장 안내 QR 코드", modifier = Modifier.size(400.dp))
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lucide(R.drawable.lucide_smartphone, T.mossDeep, 26.dp)
                Spacer(Modifier.width(10.dp))
                Text("휴대폰 카메라로 찍어 주세요", color = T.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Caption("행사장 지도와 오늘 프로그램이 열려요")
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
        // 내용은 스크롤하고, 다음 행동(집으로 안내)은 아래에 고정한다.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // 머리: 왼쪽에 행사 사진, 오른쪽에 이름과 장소
            Row(verticalAlignment = Alignment.CenterVertically) {
                val photos = rememberPhotos(f)
                Box(
                    Modifier.size(width = 200.dp, height = 150.dp).clip(RoundedCornerShape(16.dp))
                        .clickable { openPhotos(PhotoViewer(f.shortTitle, photos, 0)) },
                ) {
                    RemotePhoto(f.thumb, Modifier.matchParentSize())
                    if (photos.size > 1) Row(
                        Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(Pill).background(Color(0x99000000))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Lucide(R.drawable.lucide_images, T.white, 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("${photos.size}장", color = T.white, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.width(28.dp))
                Column {
                    Display(f.shortTitle, size = 44)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Lucide(R.drawable.lucide_map_pin, T.slate, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Caption(FestivalText.shortAddr(f.addr))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                InfoRow(R.drawable.lucide_clock, "운영 시간", spokenHours(r.hours.windows) ?: f.hours.ifBlank { "공지되지 않았어요" })
                if (f.price.isNotBlank()) InfoRow(R.drawable.lucide_ticket, "요금", priceText(f.price))
            }
            f.enrich?.let { e ->
                val items = amenities(e).filter { it.on }
                if (items.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items.forEach { Chip(it.label, true, null, it.icon) }
                    }
                    if (e.demo) { Spacer(Modifier.height(10.dp)); Caption("편의시설 정보는 아직 확인하는 중이에요.") }
                }
            }
            if (f.overview.isNotBlank() || f.program.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth().card().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (f.overview.isNotBlank()) Column { Caption("행사 소개"); Spacer(Modifier.height(4.dp)); Body(FestivalText.wholeSentences(f.overview)) }
                    FestivalText.programItems(f.program).takeIf { it.isNotEmpty() }?.let { items ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Caption("프로그램")
                            items.forEach { item ->
                                Row {
                                    Text("·", color = T.mossDeep, fontSize = 26.sp, modifier = Modifier.width(22.dp))
                                    Body(item)
                                }
                            }
                        }
                    }
                }
            }
            if (r.hours.windows.isNotEmpty()) {
                val now = nowMin(ui)
                val end = r.hours.windows.maxOf { it.end }
                Spacer(Modifier.height(24.dp))
                TimeStrip(r.hours.windows, now, null, now < end, if (now < end) "끝날 때까지 ${Judge.fmtDur(end - now)}" else "오늘은 끝났어요")
            }
        }
            Spacer(Modifier.height(24.dp))
            Row { SecondaryButton("집으로 안내", R.drawable.lucide_house) { c.goHome() } }
        }
    }
}

// ---------- 귀갓길 ----------
@Composable
private fun HomeDrive(ui: AppController.Ui) {
    val h = ui.homePlan ?: return
    val at = Judge.fmtClock(nowMin(ui) + h.driveMin)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Spacer(Modifier.weight(0.6f))
        StateBlock(R.drawable.fluent_house, "집까지 ${Judge.fmtDur(h.driveMin)}") {
            Body("${at}쯤 도착해요.", T.slate)
        }
        Spacer(Modifier.height(48.dp))
        if (h.restSuggested) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(T.ember.copy(alpha = 0.16f)).padding(horizontal = 32.dp, vertical = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Lucide(R.drawable.lucide_coffee, T.emberDeep, 34.dp)
                Spacer(Modifier.width(18.dp))
                Text("오늘 ${Judge.fmtDur(h.drivenMin)} 운전했어요. 가는 길에 한 번 쉬어 가세요.", color = T.emberDeep, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                "무리 없는 거리예요.", color = T.ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(T.mossTint).padding(horizontal = 32.dp, vertical = 26.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        VoiceHint(if (h.restSuggested) "“가까운 휴게소 들러 줘”라고 말해도 돼요" else "운전 중에는 Gleo에게 말로 물어보세요")
    }
}

// ---------- Gleo 답 ----------
/** GET_FESTIVAL_INFO 답. Gleo가 소리로 읽는 문장을 화면 아래에 12초 동안 함께 보여 준다. */
@Composable
private fun InfoCard(a: AppController.InfoAnswer, modifier: Modifier, onClose: () -> Unit) {
    Row(
        modifier.padding(40.dp).fillMaxWidth().card().padding(horizontal = 32.dp, vertical = 28.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(T.mossTint), contentAlignment = Alignment.Center) {
            Lucide(R.drawable.lucide_mic, T.mossDeep, 28.dp)
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Caption("${a.title} · “${a.question}”")
            Spacer(Modifier.height(4.dp))
            Text(a.text, color = T.ink, fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Lucide(R.drawable.lucide_x, T.slate, 26.dp)
        }
    }
}

// ---------- 질문 ----------
@Composable
private fun BoxScope.QuestionSheet(q: AppController.Question, c: AppController) {
    Box(Modifier.matchParentSize().background(Color(0xCCFFFFFF)))
    Column(
        Modifier.align(Alignment.BottomCenter).padding(40.dp).fillMaxWidth()
            .card().padding(40.dp),
    ) {
        Text(q.text, color = T.ink, fontSize = 36.sp, lineHeight = 50.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryButton(q.yes, R.drawable.lucide_check) { c.answer(true) }
            SecondaryButton(q.no) { c.answer(false) }
        }
    }
}

// ---------- 표현 ----------
private fun hoursLabel(w: List<Judge.Window>): String =
    if (w.size == 1) "운영 ${clock(w[0].start)}–${clock(w[0].end)}" else "공연 ${w.size}회"

/** 운영 시간을 말로: 16:30~23:00 → 오후 4시 30분부터 오후 11시까지 */
private fun spokenHours(w: List<Judge.Window>): String? = when {
    w.isEmpty() -> null
    w.size == 1 -> "${Judge.fmtClock(w[0].start)}부터 ${Judge.fmtClock(w[0].end)}까지"
    else -> w.joinToString(", ") { Judge.fmtClock(it.start) } + " 시작"
}

/** 편의정보 칩: 이름, 아이콘(lucide), 있는지. 기준: docs/pleos-design-system.md "편의정보 칩" */
private data class Amenity(val label: String, val icon: Int, val on: Boolean)

private fun amenities(e: kr.bandi.car.engine.Enrich) = listOf(
    Amenity("유모차", R.drawable.lucide_baby, e.stroller),
    Amenity("수유실", R.drawable.lucide_milk, e.nursing),
    Amenity("화장실", R.drawable.lucide_toilet, e.restroom),
    Amenity("휠체어", R.drawable.lucide_accessibility, e.barrierFree),
    Amenity("반려동물", R.drawable.lucide_paw_print, e.pet),
    Amenity("실내", R.drawable.lucide_umbrella, e.indoor),
)

/** 2026-04-25 ~ 2026-10-31 → 4월 25일부터 10월 31일까지 */
private fun spokenPeriod(f: Festival): String {
    fun md(d: String) = d.split("-").let { if (it.size == 3) "${it[1].toInt()}월 ${it[2].toInt()}일" else d }
    return "${md(f.start)}부터 ${md(f.end)}까지"
}

private fun priceText(p: String): String = when {
    p.contains("무료") -> "입장료가 없어요"
    p == "유료" -> "입장료가 있어요"
    else -> p
}

// ---------- debug 빌드 전용 ----------
@Composable
private fun DevToggle(open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(if (open) T.ink else T.white)
            .border(1.dp, T.lineStrong, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Lucide(if (open) R.drawable.lucide_x else R.drawable.lucide_wrench, if (open) T.white else T.slate, 20.dp) }
}

@Composable
private fun DevSheet(ui: AppController.Ui, c: AppController, modifier: Modifier) {
    Column(
        modifier.width(600.dp).fillMaxHeight().card().background(T.white).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("개발 정보 (debug 빌드에만 보임)", color = T.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        val v = ui.vehicle
        Text(
            "${ui.now.format(DateTimeFormatter.ofPattern("M/d HH:mm"))}${if (ui.demoClock) " (시연 시각)" else ""}  기어 ${v.gear.label}  " +
                "주행 가능 ${v.rangeKm?.toInt() ?: "-"}km  배터리 ${v.socPercent ?: "-"}%  이번 주행 ${v.tripDrivingMin ?: "-"}분",
            color = T.slate, fontSize = 15.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(v.connected); Text("Vehicle", fontSize = 14.sp, color = T.slate)
            Dot(ui.navi.connected); Text("NaviHelper", fontSize = 14.sp, color = T.slate)
            Dot(ui.ttsEngine != "-" && ui.ttsEngine != "없음"); Text("TTS ${ui.ttsEngine}", fontSize = 14.sp, color = T.slate)
        }
        if (v.errors.isNotEmpty()) Text("지원하지 않는 속성: ${v.errors.joinToString(", ")}", color = T.slate, fontSize = 13.sp, maxLines = 2)
        DevSwitch("시연 시각(2026-07-11 16:30)", ui.demoClock) { c.setDemoClock(it) }
        DevSwitch("비 오는 중", ui.rain) { c.setRain(it) }
        DevSwitch("가정 주차장 데이터로 안내", ui.useDemoParking) { c.setUseDemoParking(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DevButton("행사 일찍 끝남") { c.demoEarlyEnd() }
            DevButton("지금 위치를 집으로") { c.setHomeHere() }
            DevButton("도착 신호") { c.devArrive() }
        }
        Text("Gleo 연동 기록", color = T.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ui.log) { e ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(T.mist).padding(12.dp)) {
                    Row {
                        Text(
                            when (e.kind) { "ACTION" -> "Gleo Action"; "CALLBACK" -> "Callback"; else -> "System" },
                            color = if (e.kind == "SYSTEM") T.slate else T.mossDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        )
                        Text(e.time, color = T.slate, fontSize = 12.sp)
                    }
                    Text(e.title, color = T.ink, fontSize = 14.sp)
                    e.body?.let { Text(it, color = T.slate, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 10, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun DevSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = T.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = T.moss, checkedThumbColor = T.white))
    }
}

@Composable
private fun DevButton(label: String, onClick: () -> Unit) {
    Text(
        label, color = T.ink, fontSize = 14.sp,
        modifier = Modifier.clip(Pill).border(1.dp, T.lineStrong, Pill).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp
}
