package kr.bandi.car.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Vehicle SDK로 판단에 필요한 차량 상태를 받는다.
 * - 기어(P단에서만 상세 화면 허용, 도착 확인)
 * - 주행 가능 거리(도착 가능성, 충전 경유 판단)
 * - 배터리 잔량(화면 표시)
 * - 이번 주행 시간(귀갓길 졸음쉼터 판단)
 * 차종마다 지원 속성이 다르므로 실패하면 null로 두고, 판단 쪽에서 대체 동작을 쓴다.
 */
class VehicleMonitor(context: Context) {

    data class State(
        val connected: Boolean = false,
        val gear: Gear = Gear.UNKNOWN,
        val rangeKm: Double? = null,
        val socPercent: Int? = null,
        val tripDrivingMin: Int? = null,
        val errors: List<String> = emptyList(),
    )

    enum class Gear(val label: String) { PARK("P"), REVERSE("R"), NEUTRAL("N"), DRIVE("D"), UNKNOWN("-") }

    private val vehicle = Vehicle(context.applicationContext)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var started = false

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = _state.update { it.copy(gear = map(gear)) }
        override fun onFailed(e: Exception) = fail("gear", e)
    }
    private val rangeListener = object : RangeRemainingListener {
        override fun onRangeRemainingChanged(value: Float?) = _state.update { it.copy(rangeKm = toKm(raw("range", value))) }
        override fun onFailed(e: Exception) = fail("range", e)
    }
    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) = _state.update { it.copy(socPercent = value) }
        override fun onFailed(e: Exception) = fail("soc", e)
    }
    private val drivingTimeListener = object : DrivingTimeListener {
        override fun onDrivingTimeUpdated(value: Int?) = _state.update { it.copy(tripDrivingMin = toMin(value)) }
        override fun onFailed(e: Exception) = fail("drivingTime", e)
    }

    fun start() {
        if (started) return
        try {
            vehicle.initialize()
            started = true
            _state.update { it.copy(connected = true) }
        } catch (e: Exception) {
            fail("initialize", e)
            return
        }
        val gear = vehicle.getGear()
        val odometer = vehicle.getOdometer()
        val battery = vehicle.getEvBattery()

        call("gear.get") { gear.getCurrentGearSelectionState({ g -> _state.update { it.copy(gear = map(g)) } }, { fail("gear.get", it) }) }
        call("gear.register") { gear.registerGearSelection(gearListener) }

        call("range.get") { odometer.getRangeRemainingDistance({ v -> _state.update { it.copy(rangeKm = toKm(raw("range.get", v))) } }, { fail("range.get", it) }) }
        call("range.register") { odometer.registerRangeRemainingListener(rangeListener) }

        call("soc.get") { battery.getEvBatteryStateOfCharge({ v -> _state.update { it.copy(socPercent = v) } }, { fail("soc.get", it) }) }
        call("soc.register") { battery.registerEvBatteryStateOfCharge(socListener) }

        call("drivingTime.get") { odometer.getTripCurrentDrivingTime({ v -> _state.update { it.copy(tripDrivingMin = toMin(v)) } }, { fail("drivingTime.get", it) }) }
        call("drivingTime.register") { odometer.registerTripCurrentDrivingTime(drivingTimeListener) }
    }

    fun stop() {
        if (!started) return
        runCatching {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getOdometer().unregisterRangeRemainingListener(rangeListener)
            vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(socListener)
            vehicle.getOdometer().unregisterTripCurrentDrivingTime(drivingTimeListener)
            vehicle.release()
        }
        started = false
        _state.update { it.copy(connected = false) }
    }

    private inline fun call(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail(name, e)
        }
    }

    /** 단위가 문서에 없는 값은 변환 전 원값을 남긴다. 실차에서 단위를 다시 확인할 때 쓴다. */
    private fun <T> raw(name: String, v: T): T = v.also { Log.d(TAG, "$name 원값: $it") }

    private fun fail(name: String, e: Exception) {
        Log.w(TAG, "$name 실패: ${e.message}")
        _state.update { it.copy(errors = (it.errors + "$name: ${e.javaClass.simpleName}").takeLast(6)) }
    }

    companion object {
        private const val TAG = "Bandi/Vehicle"

        fun map(g: VehicleGear?): Gear = when (g) {
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            else -> Gear.DRIVE // GEAR_DRIVE, GEAR_1~9
        }

        /**
         * 남은 주행 가능 거리는 미터다. Pleos Virtual Console이 remaining_range_m으로 정의하고,
         * 에뮬레이터에서 500000.0(500km)과 1500.0(1.5km)을 받아 확인했다.
         */
        fun toKm(v: Float?): Double? = v?.toDouble()?.let { it / 1000 }

        /**
         * 이번 주행 시간(TRIP_DRIVING_TIME)은 분이다. Pleos Virtual Console의 usage_time_min 정의를 따른다.
         * 에뮬레이터 이미지가 이 속성을 지원하지 않아 실제 값으로는 확인하지 못했다.
         */
        fun toMin(v: Int?): Int? = v
    }
}
