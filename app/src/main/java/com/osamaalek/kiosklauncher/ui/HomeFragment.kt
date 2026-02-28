package com.osamaalek.kiosklauncher.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osamaalek.kiosklauncher.R
import com.osamaalek.kiosklauncher.adapter.AppsAdapter
import com.osamaalek.kiosklauncher.policy.PolicyStore
import com.osamaalek.kiosklauncher.util.AppsUtil

class HomeFragment : Fragment() {
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var hotspotView: View
    private lateinit var topRightCornerView: View
    private lateinit var bottomRightCornerView: View
    private lateinit var bottomLeftCornerView: View

    private val uiHandler = Handler(Looper.getMainLooper())

    private var cornerGestureIndex = 0
    private var cornerGestureStartedAt = 0L
    private var armedUntilMs = 0L

    private var longPressTriggered = false
    private var suppressAutoLaunch = false
    private var topLeftDownX = 0f
    private var topLeftDownY = 0f
    private var topLeftDownAt = 0L
    private var emergencyTapCount = 0
    private var emergencyFirstTapAt = 0L

    private val autoLaunchRunnable = Runnable {
        val policy = PolicyStore(requireContext()).getPolicy()
        if (!policy.singleAppMode || suppressAutoLaunch) return@Runnable
        val allowedInstalledApps = AppsUtil.getAllowedApps(requireContext(), policy.allowedPackages)
        if (allowedInstalledApps.size == 1) {
            val packageName = allowedInstalledApps.first().packageName?.toString().orEmpty()
            launchApp(packageName)
        }
    }
    private val openSettingsRunnable = Runnable {
        if (!isExitGestureArmed()) return@Runnable
        longPressTriggered = true
        armedUntilMs = 0L
        suppressAutoLaunch = true
        (activity as? MainActivity)?.openSettingsWithPin()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appsRecyclerView = view.findViewById(R.id.recycler_allowed_apps)
        emptyStateText = view.findViewById(R.id.text_empty_state)
        hotspotView = view.findViewById(R.id.kiosk_exit_hotspot)
        topRightCornerView = view.findViewById(R.id.kiosk_corner_top_right)
        bottomRightCornerView = view.findViewById(R.id.kiosk_corner_bottom_right)
        bottomLeftCornerView = view.findViewById(R.id.kiosk_corner_bottom_left)

        appsRecyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
        appsRecyclerView.setHasFixedSize(true)

        hotspotView.setOnTouchListener { _, event -> handleTopLeftTouch(event) }
        setupCornerSwipeListener(topRightCornerView, CORNER_TOP_RIGHT)
        setupCornerSwipeListener(bottomRightCornerView, CORNER_BOTTOM_RIGHT)
        setupCornerSwipeListener(bottomLeftCornerView, CORNER_BOTTOM_LEFT)

        renderLauncher(allowAutoLaunch = false)
    }

    override fun onResume() {
        super.onResume()
        renderLauncher(allowAutoLaunch = true)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(autoLaunchRunnable)
        uiHandler.removeCallbacks(openSettingsRunnable)
        super.onPause()
        suppressAutoLaunch = false
        resetCornerGesture()
    }

    override fun onDestroyView() {
        uiHandler.removeCallbacks(autoLaunchRunnable)
        uiHandler.removeCallbacks(openSettingsRunnable)
        super.onDestroyView()
    }

    private fun renderLauncher(allowAutoLaunch: Boolean) {
        val policy = PolicyStore(requireContext()).getPolicy()
        val allowedInstalledApps = AppsUtil.getAllowedApps(requireContext(), policy.allowedPackages)

        if (allowedInstalledApps.isEmpty()) {
            appsRecyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = getString(R.string.home_empty_state)
            return
        }

        emptyStateText.visibility = View.GONE
        appsRecyclerView.visibility = View.VISIBLE
        appsRecyclerView.adapter = AppsAdapter(allowedInstalledApps, requireContext())

        uiHandler.removeCallbacks(autoLaunchRunnable)
        if (allowAutoLaunch && policy.singleAppMode && allowedInstalledApps.size == 1 && !suppressAutoLaunch) {
            Toast.makeText(requireContext(), getString(R.string.toast_auto_launch_countdown), Toast.LENGTH_SHORT)
                .show()
            uiHandler.postDelayed(autoLaunchRunnable, AUTO_LAUNCH_DELAY_MS)
        }
    }

    private fun launchApp(packageName: String) {
        if (packageName.isBlank()) return
        val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(requireContext(), getString(R.string.toast_app_not_installed), Toast.LENGTH_SHORT).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    private fun handleTopLeftTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                suppressAutoLaunch = true
                uiHandler.removeCallbacks(autoLaunchRunnable)
                topLeftDownX = event.x
                topLeftDownY = event.y
                topLeftDownAt = SystemClock.elapsedRealtime()
                longPressTriggered = false
                if (isExitGestureArmed()) {
                    uiHandler.removeCallbacks(openSettingsRunnable)
                    uiHandler.postDelayed(openSettingsRunnable, EXIT_LONG_PRESS_MS)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(openSettingsRunnable)
                if (isExitGestureArmed()) {
                    if (!longPressTriggered) {
                        Toast.makeText(requireContext(), getString(R.string.toast_hold_to_open_admin), Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    val duration = SystemClock.elapsedRealtime() - topLeftDownAt
                    val dx = event.x - topLeftDownX
                    val dy = event.y - topLeftDownY
                    if (isTap(dx, dy, duration)) {
                        registerEmergencyTap()
                    } else if (isCornerSwipe(CORNER_TOP_LEFT, dx, dy, duration)) {
                        registerCornerSwipe(CORNER_TOP_LEFT)
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                uiHandler.removeCallbacks(openSettingsRunnable)
                return true
            }
        }
        return false
    }

    private fun setupCornerSwipeListener(view: View, corner: Int) {
        var downX = 0f
        var downY = 0f
        var downAt = 0L
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    suppressAutoLaunch = true
                    uiHandler.removeCallbacks(autoLaunchRunnable)
                    downX = event.x
                    downY = event.y
                    downAt = SystemClock.elapsedRealtime()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isExitGestureArmed()) {
                        val duration = SystemClock.elapsedRealtime() - downAt
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (isCornerSwipe(corner, dx, dy, duration)) {
                            registerCornerSwipe(corner)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun registerEmergencyTap() {
        val now = SystemClock.elapsedRealtime()
        if (now > EMERGENCY_UNLOCK_UPTIME_MS) return
        if (emergencyFirstTapAt == 0L || now - emergencyFirstTapAt > EMERGENCY_TAP_WINDOW_MS) {
            emergencyFirstTapAt = now
            emergencyTapCount = 0
        }
        emergencyTapCount += 1
        val remain = EMERGENCY_TAP_COUNT - emergencyTapCount
        if (remain > 0) {
            Toast.makeText(requireContext(), getString(R.string.toast_emergency_tap_remaining, remain), Toast.LENGTH_SHORT)
                .show()
            return
        }
        emergencyTapCount = 0
        emergencyFirstTapAt = 0L
        vibrateLight()
        Toast.makeText(requireContext(), getString(R.string.toast_emergency_ready), Toast.LENGTH_SHORT).show()
        (activity as? MainActivity)?.openSettingsWithPin()
    }

    private fun registerCornerSwipe(corner: Int) {
        val now = SystemClock.elapsedRealtime()
        if (cornerGestureStartedAt != 0L && now - cornerGestureStartedAt > EXIT_SEQUENCE_WINDOW_MS) {
            resetCornerGesture()
            Toast.makeText(requireContext(), getString(R.string.toast_corner_timeout), Toast.LENGTH_SHORT).show()
        }

        val expectedCorner = CORNER_SEQUENCE[cornerGestureIndex]
        if (corner != expectedCorner) {
            if (corner == CORNER_SEQUENCE.first()) {
                cornerGestureIndex = 1
                cornerGestureStartedAt = now
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_corner_step, cornerGestureIndex, CORNER_SEQUENCE.size),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                resetCornerGesture()
                Toast.makeText(requireContext(), getString(R.string.toast_corner_retry), Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (cornerGestureIndex == 0) {
            cornerGestureStartedAt = now
        }
        cornerGestureIndex += 1
        vibrateLight()
        if (cornerGestureIndex < CORNER_SEQUENCE.size) {
            Toast.makeText(
                requireContext(),
                getString(R.string.toast_corner_step, cornerGestureIndex, CORNER_SEQUENCE.size),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        resetCornerGesture()
        armedUntilMs = now + EXIT_ARM_VALID_MS
        Toast.makeText(requireContext(), getString(R.string.toast_admin_armed), Toast.LENGTH_SHORT).show()
    }

    private fun resetCornerGesture() {
        cornerGestureIndex = 0
        cornerGestureStartedAt = 0L
    }

    private fun isTap(dx: Float, dy: Float, durationMs: Long): Boolean {
        val threshold = dpToPx(12f)
        return kotlin.math.abs(dx) < threshold &&
            kotlin.math.abs(dy) < threshold &&
            durationMs <= TAP_MAX_DURATION_MS
    }

    private fun isCornerSwipe(corner: Int, dx: Float, dy: Float, durationMs: Long): Boolean {
        if (durationMs > SWIPE_MAX_DURATION_MS) return false
        val minDistance = dpToPx(SWIPE_MIN_DP)
        return when (corner) {
            CORNER_TOP_LEFT -> dx >= minDistance && dy >= minDistance
            CORNER_TOP_RIGHT -> dx <= -minDistance && dy >= minDistance
            CORNER_BOTTOM_RIGHT -> dx <= -minDistance && dy <= -minDistance
            CORNER_BOTTOM_LEFT -> dx >= minDistance && dy <= -minDistance
            else -> false
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun isExitGestureArmed(): Boolean {
        return SystemClock.elapsedRealtime() < armedUntilMs
    }

    private fun vibrateLight() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = requireContext().getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = requireContext().getSystemService(Vibrator::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(35)
                }
            }
        } catch (_: Exception) {
            // Ignore devices without vibration capability.
        }
    }

    companion object {
        private const val CORNER_TOP_LEFT = 0
        private const val CORNER_TOP_RIGHT = 1
        private const val CORNER_BOTTOM_RIGHT = 2
        private const val CORNER_BOTTOM_LEFT = 3
        private val CORNER_SEQUENCE = intArrayOf(
            CORNER_TOP_LEFT,
            CORNER_TOP_RIGHT,
            CORNER_BOTTOM_RIGHT,
            CORNER_BOTTOM_LEFT
        )

        private const val EXIT_LONG_PRESS_MS = 1500L
        private const val EXIT_ARM_VALID_MS = 10000L
        private const val EXIT_SEQUENCE_WINDOW_MS = 5000L
        private const val AUTO_LAUNCH_DELAY_MS = 8000L
        private const val SWIPE_MIN_DP = 28f
        private const val SWIPE_MAX_DURATION_MS = 1200L
        private const val TAP_MAX_DURATION_MS = 300L
        private const val EMERGENCY_TAP_COUNT = 10
        private const val EMERGENCY_TAP_WINDOW_MS = 5000L
        private const val EMERGENCY_UNLOCK_UPTIME_MS = 180000L
    }
}
