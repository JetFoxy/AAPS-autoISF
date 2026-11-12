package app.aaps.plugins.aps.openAPSSMB

import app.aaps.core.data.model.HR
import app.aaps.core.interfaces.logging.AAPSLogger

/**
 * Chooses between the HR-fused [HrActivityCalculator] classification and the legacy rule-based
 * [ActivityClassifier], in a fixed decision order:
 *
 *  1. An explicit OS activity signal (bicycle / walking / running) wins outright — the platform
 *     knows for sure, return the legacy AEROBIC mapping.
 *  2. HR unavailable (no valid readings in the window, or a frozen/stuck sensor value) → the
 *     legacy rule classifier on steps only. A missing heart rate is UNKNOWN, never evidence of
 *     rest: the legacy path has no INACTIVE output, so no HR-driven inactivity claim can ride
 *     on absent data.
 *  3. Otherwise the fused Karvonen-zone classification maps onto the graded [ActivityClass].
 */
object ActivityFusion {

    fun classifyActivity(
        hrReadings: List<HR>,
        nowMillis: Long,
        hrWindowMinutes: Int,
        hrMax: Int,
        hrResting: Int,
        steps15m: Int,
        phoneActivity: Int,
        stressDetection: Boolean,
        aapsLogger: AAPSLogger?
    ): ActivityClass {
        if (phoneActivity == ActivityClassifier.PHONE_BICYCLE ||
            phoneActivity == ActivityClassifier.PHONE_WALKING ||
            phoneActivity == ActivityClassifier.PHONE_RUNNING
        ) {
            return ActivityClass.AEROBIC
        }

        val hrUsable = HrActivityCalculator.averageHrInWindow(hrReadings, nowMillis, hrWindowMinutes) != null &&
            !HrActivityCalculator.isHrWindowFrozen(hrReadings, nowMillis, hrWindowMinutes)
        if (!hrUsable) {
            return ActivityClassifier.classify(
                steps15m = steps15m,
                avgHrLast15m = 0.0,
                phoneActivity = phoneActivity,
                restingHr = hrResting
            )
        }

        val result = HrActivityCalculator.classify(
            hrReadings = hrReadings,
            nowMillis = nowMillis,
            hrWindowMinutes = hrWindowMinutes,
            hrMax = hrMax,
            hrResting = hrResting,
            stepsLast15Min = steps15m,
            stressDetection = stressDetection,
            aapsLogger = aapsLogger
        )
        return ActivityClass.fromHrState(result.exerciseState, stressDetection)
    }
}
