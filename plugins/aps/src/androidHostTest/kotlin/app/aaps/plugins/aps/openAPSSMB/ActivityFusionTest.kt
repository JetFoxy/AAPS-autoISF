package app.aaps.plugins.aps.openAPSSMB

import app.aaps.core.data.model.HR
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ActivityFusionTest {

    private val now = 1_700_000_000_000L

    private fun hr(bpm: Double, minutesAgo: Long = 0) = HR(
        duration = 60_000,
        timestamp = now - minutesAgo * 60_000,
        beatsPerMinute = bpm,
        device = "test"
    )

    private fun classify(
        readings: List<HR>,
        steps15m: Int,
        phoneActivity: Int = ActivityClassifier.PHONE_STILL,
        stressDetection: Boolean = false
    ) = ActivityFusion.classifyActivity(
        hrReadings = readings,
        nowMillis = now,
        hrWindowMinutes = 15,
        hrMax = 180,
        hrResting = 60,
        steps15m = steps15m,
        phoneActivity = phoneActivity,
        stressDetection = stressDetection,
        aapsLogger = null
    )

    @Test fun `os signals win over any hr data`() {
        val garbage = listOf(hr(130.0), hr(140.0), hr(135.0), hr(138.0))
        assertThat(classify(garbage, steps15m = 0, phoneActivity = ActivityClassifier.PHONE_BICYCLE)).isEqualTo(ActivityClass.AEROBIC)
        assertThat(classify(garbage, steps15m = 0, phoneActivity = ActivityClassifier.PHONE_WALKING)).isEqualTo(ActivityClass.AEROBIC)
        assertThat(classify(garbage, steps15m = 0, phoneActivity = ActivityClassifier.PHONE_RUNNING)).isEqualTo(ActivityClass.AEROBIC)
    }

    @Test fun `no hr readings falls back to legacy step rules`() {
        assertThat(classify(emptyList(), steps15m = 200)).isEqualTo(ActivityClass.AEROBIC)
        assertThat(classify(emptyList(), steps15m = 10)).isEqualTo(ActivityClass.REST)
    }

    @Test fun `frozen hr window falls back to legacy step rules`() {
        val frozen = listOf(hr(124.0), hr(124.0), hr(124.0), hr(124.0))
        assertThat(classify(frozen, steps15m = 200)).isEqualTo(ActivityClass.AEROBIC)
        assertThat(classify(frozen, steps15m = 10)).isEqualTo(ActivityClass.REST)
    }

    @Test fun `zone two hr with almost no steps is stress when opted in`() {
        val readings = listOf(hr(100.0, minutesAgo = 1))
        assertThat(classify(readings, steps15m = 5, stressDetection = true)).isEqualTo(ActivityClass.STRESS)
    }

    @Test fun `zone three hr with almost no steps is resistance even when stress detection on`() {
        val readings = listOf(hr(120.0, minutesAgo = 1))
        assertThat(classify(readings, steps15m = 5, stressDetection = true)).isEqualTo(ActivityClass.RESISTANCE)
    }

    @Test fun `elevated hr with almost no steps is resistance when stress detection off`() {
        val readings = listOf(hr(120.0, minutesAgo = 1))
        assertThat(classify(readings, steps15m = 5, stressDetection = false)).isEqualTo(ActivityClass.RESISTANCE)
    }

    @Test fun `brisk walking hr fuses into graded aerobic`() {
        val readings = listOf(hr(110.0, minutesAgo = 1))
        assertThat(classify(readings, steps15m = 400)).isEqualTo(ActivityClass.VIGOROUS_AEROBIC)
    }
}
