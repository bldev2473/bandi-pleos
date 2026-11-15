package kr.bandi.car.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kr.bandi.car.engine.Geo

/**
 * Pleos NaviHelper SDK로 기본 내비게이션에 목적지, 경유지를 넘기고 주행 정보를 받는다.
 */
class NaviBridge(context: Context) {

    data class Stop(val name: String, val geo: Geo, val address: String = "")

    data class State(
        val connected: Boolean = false,
        val status: String = "-",
        val routing: Boolean = false,
        val location: Geo? = null,
        val destinationName: String? = null,
        /** 목적지까지 남은 시간(초). 에뮬레이터에서 1560을 받고 내비 화면 26분과 대조해 확인했다. */
        val remainSec: Int? = null,
        /** 목적지까지 남은 거리(m). 에뮬레이터에서 22000을 받고 내비 화면 22km와 대조해 확인했다. */
        val remainMeters: Int? = null,
        val lastError: String? = null,
    )

    sealed interface Event {
        data class WaypointArrived(val name: String) : Event
        data class DestinationArrived(val name: String) : Event
        data object RouteCancelled : Event
        data object RouteEnded : Event
    }

    private val navi = NaviHelper(context.applicationContext)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()
    private var started = false

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) =
            _state.update { it.copy(location = Geo(info.latitude, info.longitude)) }

        override fun onDestinationInfo(info: DestinationInfo) {
            Log.d(TAG, "destinationInfo 원값: remainTime=${info.remainTime} remainDistance=${info.remainDistance}")
            _state.update { it.copy(destinationName = info.name, remainSec = info.remainTime, remainMeters = info.remainDistance) }
        }

        override fun onDrivingInfo(info: DrivingInfo) {
            val d = info.destination ?: return
            Log.d(TAG, "drivingInfo 원값: duration=${d.duration} distance=${d.distance}")
            _state.update { it.copy(destinationName = d.name, remainSec = d.duration, remainMeters = d.distance) }
        }

        override fun onNavigationStatus(status: NavigationStatus) = _state.update {
            it.copy(status = status.name, routing = status == NavigationStatus.ROUTE_DRIVE)
        }

        override fun onRouteStarted(info: ai.pleos.playground.navi.data.RouteStartInfo) =
            _state.update { it.copy(routing = true) }

        override fun onWayPointArrived(info: WaypointArrivedInfo) {
            _events.tryEmit(Event.WaypointArrived(info.name))
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            _state.update { it.copy(routing = false, remainMeters = 0, remainSec = 0) }
            _events.tryEmit(Event.DestinationArrived(info.name))
        }

        override fun onRouteCancelled() {
            _state.update { it.copy(routing = false) }
            _events.tryEmit(Event.RouteCancelled)
        }

        override fun onRouteEnded() {
            _state.update { it.copy(routing = false) }
            _events.tryEmit(Event.RouteEnded)
        }

        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "NaviHelper 오류: $code")
            _state.update { it.copy(lastError = code.name) }
        }
    }

    fun start() {
        if (started) return
        navi.initialize(
            {
                started = true
                navi.addListener(listener)
                _state.update { it.copy(connected = true) }
                refresh()
            },
            { e ->
                Log.w(TAG, "NaviHelper 초기화 실패: ${e.message}")
                _state.update { it.copy(connected = false, lastError = "initialize: ${e.javaClass.simpleName}") }
            },
        )
    }

    fun stop() {
        if (!started) return
        runCatching {
            navi.removeListener(listener)
            navi.release()
        }
        started = false
        _state.update { it.copy(connected = false) }
    }

    /** 현재 위치, 목적지 정보를 다시 요청한다. 응답은 리스너로 온다. */
    fun refresh() {
        if (!started) return
        runCatching {
            navi.getCurrentLocationInfo()
            navi.getDestinationInfo()
            navi.getRouteStateInfo()
        }.onFailure { Log.w(TAG, "refresh 실패: ${it.message}") }
    }

    /**
     * 목적지와 경유지(최대 2곳)로 안내를 시작한다.
     * 경유지가 있으면 requestRouteWithWaypoints, 없으면 requestRoute를 쓴다.
     */
    fun startRoute(destination: Stop, waypoints: List<Stop>): Boolean {
        if (!started) return false
        return runCatching {
            val route = RouteInfo(
                destination.geo.lat, destination.geo.lon, null, null,
                destination.name, "", destination.address, "",
                RouteOption.RECOMMENDED, "", false,
            )
            if (waypoints.isEmpty()) {
                navi.requestRoute(route)
            } else {
                val wps = waypoints.take(2).mapIndexed { i, w ->
                    RequestWaypointInfo(
                        w.geo.lat, w.geo.lon, w.name,
                        if (i == 0) WaypointIndex.FIRST else WaypointIndex.SECOND,
                        "", w.address, "", "",
                    )
                }
                navi.requestRouteWithWaypoints(RouteWithWaypointsRequest(route, wps))
            }
            _state.update { it.copy(destinationName = destination.name) }
            true
        }.getOrElse {
            Log.w(TAG, "안내 요청 실패: ${it.message}")
            _state.update { s -> s.copy(lastError = "route: ${it.javaClass.simpleName}") }
            false
        }
    }

    fun cancelRoute() {
        if (started) runCatching { navi.cancelRoute() }
    }

    companion object {
        private const val TAG = "Bandi/Navi"
    }
}
