package kr.bandi.car.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pleos Virtual Console이 정의한 단위를 따른다.
 * RANGE_REMAINING은 remaining_range_m(미터), TRIP_DRIVING_TIME은 usage_time_min(분)이다.
 */
class VehicleUnitTest {
    @Test
    fun rangeIsAlwaysMeters() {
        assertEquals(500.0, VehicleMonitor.toKm(500000f)!!, 0.001)
        // 2km 미만이 남은 차를 1,500km로 읽으면 안 된다.
        assertEquals(1.5, VehicleMonitor.toKm(1500f)!!, 0.001)
        assertEquals(0.0, VehicleMonitor.toKm(0f)!!, 0.001)
        assertNull(VehicleMonitor.toKm(null))
    }

    @Test
    fun tripDrivingTimeIsMinutes() {
        assertEquals(90, VehicleMonitor.toMin(90))
        assertNull(VehicleMonitor.toMin(null))
    }
}
