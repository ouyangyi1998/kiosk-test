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
import com.osamaalek.kiosklauncher.util.AppsUtil
import com.osamaalek.kiosklauncher.policy.PolicyStore

class HomeFragment : Fragment() {
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var hotspotView: View
    private val uiHandler = Handler(Looper.getMainLooper())
    private var tapCount = 0
    private var firstTapAtMs = 0L
    private var armedUntilMs = 0L
    private var longPressTriggered = false
    private var suppressAutoLaunch = false
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

        appsRecyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
        appsRecyclerView.setHasFixedSize(true)

        hotspotView.setOnTouchListener { _, event -> handleHotspotTouch(event) }

        renderLauncher(allowAutoLaunch = false)
    }

    override fun onResume() {
        super.onResume()
        renderLauncher(allowAutoLaunch = true)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(autoLaunchRunnable)
        super.onPause()
        suppressAutoLaunch = false
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
            emptyStateText.text = "未配置可用应用，请长按左上角进入设置。"
            return
        }

        emptyStateText.visibility = View.GONE
        appsRecyclerView.visibility = View.VISIBLE
        appsRecyclerView.adapter = AppsAdapter(allowedInstalledApps, requireContext())

        uiHandler.removeCallbacks(autoLaunchRunnable)
        if (allowAutoLaunch && policy.singleAppMode && allowedInstalledApps.size == 1 && !suppressAutoLaunch) {
            Toast.makeText(requireContext(), "单应用模式：6秒后自动启动，点左上角可取消", Toast.LENGTH_SHORT).show()
            uiHandler.postDelayed(autoLaunchRunnable, AUTO_LAUNCH_DELAY_MS)
        }
    }

    private fun launchApp(packageName: String) {
        if (packageName.isBlank()) return
        val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(requireContext(), "应用未安装或不可启动", Toast.LENGTH_SHORT).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    private fun handleHotspotTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
                        Toast.makeText(requireContext(), "请继续长按 1.2 秒进入管理入口", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    registerHotspotTap()
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

    private fun registerHotspotTap() {
        suppressAutoLaunch = true
        uiHandler.removeCallbacks(autoLaunchRunnable)
        vibrateLight()
        val now = SystemClock.elapsedRealtime()
        if (firstTapAtMs == 0L || now - firstTapAtMs > EXIT_TAP_WINDOW_MS) {
            firstTapAtMs = now
            tapCount = 0
        }
        tapCount += 1
        val remain = EXIT_TAP_COUNT - tapCount
        if (remain > 0) {
            Toast.makeText(requireContext(), "再点击 $remain 次激活管理入口", Toast.LENGTH_SHORT).show()
            return
        }

        tapCount = 0
        firstTapAtMs = 0L
        armedUntilMs = now + EXIT_ARM_VALID_MS
        vibrateLight()
        Toast.makeText(requireContext(), "管理入口已激活，请长按左上角 1.2 秒", Toast.LENGTH_SHORT).show()
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
        private const val EXIT_TAP_COUNT = 5
        private const val EXIT_TAP_WINDOW_MS = 3000L
        private const val EXIT_LONG_PRESS_MS = 1200L
        private const val EXIT_ARM_VALID_MS = 8000L
        private const val AUTO_LAUNCH_DELAY_MS = 6000L
    }
}
