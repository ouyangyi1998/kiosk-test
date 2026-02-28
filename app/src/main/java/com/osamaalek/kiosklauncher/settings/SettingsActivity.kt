package com.osamaalek.kiosklauncher.settings

import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.osamaalek.kiosklauncher.MyDeviceAdminReceiver
import com.osamaalek.kiosklauncher.R
import com.osamaalek.kiosklauncher.policy.KioskPolicy
import com.osamaalek.kiosklauncher.policy.PolicyApplier
import com.osamaalek.kiosklauncher.policy.PolicyStore
import com.osamaalek.kiosklauncher.scheduler.KioskScheduler
import com.osamaalek.kiosklauncher.util.KioskUtil
import com.osamaalek.kiosklauncher.util.PinUtil
import com.osamaalek.kiosklauncher.util.TimeUtil
import com.osamaalek.kiosklauncher.workers.PolicySyncWorker

class SettingsActivity : AppCompatActivity() {
    private lateinit var store: PolicyStore
    private lateinit var applier: PolicyApplier

    private lateinit var remoteUrlEdit: EditText
    private lateinit var remoteTokenEdit: EditText
    private lateinit var allowedAppsText: TextView
    private lateinit var deviceOwnerStateText: TextView
    private lateinit var switchSingleAppMode: Switch
    private lateinit var switchDisableStatusBar: Switch
    private lateinit var switchDisableNotifications: Switch
    private lateinit var switchHideNavigationBar: Switch
    private lateinit var startTimeText: TextView
    private lateinit var stopTimeText: TextView
    private lateinit var rebootTimeText: TextView

    private var selectedPackages = LinkedHashSet<String>()
    private var startTime: String? = null
    private var stopTime: String? = null
    private var rebootTime: String? = null
    private var currentPinHash: String? = null
    private var currentKioskUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        store = PolicyStore(this)
        applier = PolicyApplier(this)

        remoteUrlEdit = findViewById(R.id.edit_remote_url)
        remoteTokenEdit = findViewById(R.id.edit_remote_token)
        allowedAppsText = findViewById(R.id.text_allowed_apps)
        deviceOwnerStateText = findViewById(R.id.text_device_owner_state)
        switchSingleAppMode = findViewById(R.id.switch_single_app_mode)
        switchDisableStatusBar = findViewById(R.id.switch_disable_status_bar)
        switchDisableNotifications = findViewById(R.id.switch_disable_notifications)
        switchHideNavigationBar = findViewById(R.id.switch_hide_navigation_bar)
        startTimeText = findViewById(R.id.text_start_time)
        stopTimeText = findViewById(R.id.text_stop_time)
        rebootTimeText = findViewById(R.id.text_reboot_time)

        bindPolicy(store.getPolicy())
        refreshDeviceState()

        switchSingleAppMode.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked || selectedPackages.size <= 1) {
                updateAllowedAppsText()
                return@setOnCheckedChangeListener
            }
            val first = selectedPackages.firstOrNull()
            selectedPackages.clear()
            if (!first.isNullOrBlank()) {
                selectedPackages.add(first)
            }
            updateAllowedAppsText()
            Toast.makeText(this, getString(R.string.toast_single_mode_keep_one), Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.button_select_apps).setOnClickListener {
            val intent = AppsSelectionActivity.newIntent(
                this,
                selectedPackages,
                switchSingleAppMode.isChecked
            )
            startActivityForResult(intent, REQUEST_SELECT_APPS)
        }

        findViewById<Button>(R.id.button_set_start_time).setOnClickListener {
            openTimePicker(startTime) { value ->
                startTime = value
                startTimeText.text = value
            }
        }
        findViewById<Button>(R.id.button_clear_start_time).setOnClickListener {
            startTime = null
            startTimeText.text = getString(R.string.not_selected)
        }
        findViewById<Button>(R.id.button_set_stop_time).setOnClickListener {
            openTimePicker(stopTime) { value ->
                stopTime = value
                stopTimeText.text = value
            }
        }
        findViewById<Button>(R.id.button_clear_stop_time).setOnClickListener {
            stopTime = null
            stopTimeText.text = getString(R.string.not_selected)
        }
        findViewById<Button>(R.id.button_set_reboot_time).setOnClickListener {
            openTimePicker(rebootTime) { value ->
                rebootTime = value
                rebootTimeText.text = value
            }
        }
        findViewById<Button>(R.id.button_clear_reboot_time).setOnClickListener {
            rebootTime = null
            rebootTimeText.text = getString(R.string.not_selected)
        }

        findViewById<Button>(R.id.button_save).setOnClickListener {
            val policy = buildPolicy()
            if (!validatePolicy(policy)) return@setOnClickListener
            KioskUtil.resumeKioskMode(this)
            store.savePolicy(policy)
            applier.apply(policy)
            KioskScheduler.scheduleAll(this, policy)
            PolicySyncWorker.scheduleIfNeeded(this, policy)
            Toast.makeText(this, buildSaveMessage(), Toast.LENGTH_SHORT).show()
            KioskUtil.applyWindowPolicy(this, policy.hideNavigationBar)
            refreshDeviceState()
        }

        findViewById<Button>(R.id.button_sync).setOnClickListener {
            PolicySyncWorker.runOnce(this)
            Toast.makeText(this, getString(R.string.toast_sync_triggered), Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.button_set_pin).setOnClickListener {
            showSetPinDialog()
        }

        findViewById<Button>(R.id.button_open_system_settings).setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }

        findViewById<Button>(R.id.button_exit_kiosk).setOnClickListener {
            if (currentPinHash.isNullOrBlank()) {
                Toast.makeText(this, getString(R.string.toast_pin_not_set), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PinPrompt.verifyPin(this, currentPinHash) {
                KioskUtil.stopKioskMode(this)
                Toast.makeText(this, getString(R.string.toast_kiosk_exited), Toast.LENGTH_SHORT).show()
                refreshDeviceState()
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                finishAffinity()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDeviceState()
    }

    private fun bindPolicy(policy: KioskPolicy) {
        currentKioskUrl = policy.kioskUrl
        remoteUrlEdit.setText(policy.remoteUrl ?: "")
        remoteTokenEdit.setText(policy.remoteToken ?: "")
        switchSingleAppMode.isChecked = policy.singleAppMode
        switchDisableStatusBar.isChecked = policy.disableStatusBar
        switchDisableNotifications.isChecked = policy.disableNotifications
        switchHideNavigationBar.isChecked = policy.hideNavigationBar

        selectedPackages.clear()
        selectedPackages.addAll(policy.allowedPackages)
        updateAllowedAppsText()

        startTime = policy.scheduleStart
        stopTime = policy.scheduleStop
        rebootTime = policy.rebootTime
        startTimeText.text = policy.scheduleStart ?: getString(R.string.not_selected)
        stopTimeText.text = policy.scheduleStop ?: getString(R.string.not_selected)
        rebootTimeText.text = policy.rebootTime ?: getString(R.string.not_selected)

        currentPinHash = policy.exitPinHash
    }

    private fun buildPolicy(): KioskPolicy {
        return KioskPolicy(
            kioskUrl = currentKioskUrl,
            allowedPackages = selectedPackages.toSet(),
            singleAppMode = switchSingleAppMode.isChecked,
            disableStatusBar = switchDisableStatusBar.isChecked,
            disableNotifications = switchDisableNotifications.isChecked,
            hideNavigationBar = switchHideNavigationBar.isChecked,
            exitPinHash = currentPinHash,
            scheduleStart = startTime,
            scheduleStop = stopTime,
            rebootTime = rebootTime,
            remoteUrl = remoteUrlEdit.text.toString().ifBlank { null },
            remoteToken = remoteTokenEdit.text.toString().ifBlank { null }
        )
    }

    private fun updateAllowedAppsText() {
        allowedAppsText.text = if (selectedPackages.isEmpty()) {
            if (switchSingleAppMode.isChecked) {
                getString(R.string.mode_single_no_selection)
            } else {
                getString(R.string.mode_multi_no_selection)
            }
        } else {
            val mode = if (switchSingleAppMode.isChecked) {
                getString(R.string.mode_single)
            } else {
                getString(R.string.mode_multi)
            }
            val preview = selectedPackages.take(3).joinToString("\n")
            val tail = if (selectedPackages.size > 3) {
                getString(R.string.mode_extra_count, selectedPackages.size)
            } else {
                ""
            }
            val suffix = if (switchSingleAppMode.isChecked) {
                getString(R.string.mode_single_auto_launch_suffix)
            } else {
                ""
            }
            "${getString(R.string.mode_selected_header, mode, selectedPackages.size)}\n$preview$tail$suffix"
        }
    }

    private fun validatePolicy(policy: KioskPolicy): Boolean {
        if (!policy.remoteUrl.isNullOrBlank() && !isValidHttpUrl(policy.remoteUrl)) {
            Toast.makeText(this, getString(R.string.error_remote_url_invalid), Toast.LENGTH_SHORT).show()
            return false
        }
        if (policy.singleAppMode && policy.allowedPackages.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_single_mode_need_one), Toast.LENGTH_SHORT).show()
            return false
        }
        if (policy.singleAppMode && policy.allowedPackages.size > 1) {
            Toast.makeText(this, getString(R.string.error_single_mode_only_one), Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun isValidHttpUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(url.trim())
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun buildSaveMessage(): String {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, MyDeviceAdminReceiver::class.java)
        val isAdmin = dpm.isAdminActive(admin)
        val isOwner = dpm.isDeviceOwnerApp(packageName)
        return when {
            isOwner -> getString(R.string.status_saved_owner)
            isAdmin -> getString(R.string.status_saved_not_owner)
            else -> getString(R.string.status_saved_need_owner_setup)
        }
    }

    private fun refreshDeviceState() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, MyDeviceAdminReceiver::class.java)
        val isAdmin = dpm.isAdminActive(admin)
        val isOwner = dpm.isDeviceOwnerApp(packageName)
        val adminText = if (isAdmin) getString(R.string.status_active) else getString(R.string.status_inactive)
        val ownerText = if (isOwner) getString(R.string.status_yes) else getString(R.string.status_no)
        deviceOwnerStateText.text = getString(R.string.status_device_owner_line, adminText, ownerText)
    }

    private fun openTimePicker(current: String?, onSelected: (String) -> Unit) {
        val (hour, minute) = parseTime(current)
        TimePickerDialog(this, { _, h, m ->
            onSelected(TimeUtil.format(h, m))
        }, hour, minute, true).show()
    }

    private fun parseTime(current: String?): Pair<Int, Int> {
        if (current.isNullOrBlank()) return 9 to 0
        val parts = current.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h to m
    }

    private fun showSetPinDialog() {
        val input1 = EditText(this)
        val input2 = EditText(this)
        input1.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input2.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input1.hint = getString(R.string.hint_enter_pin)
        input2.hint = getString(R.string.hint_confirm_pin)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(input1)
            addView(input2)
            setPadding(24, 16, 24, 0)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.title_set_pin)
            .setView(layout)
            .setPositiveButton(R.string.button_save, null)
            .setNegativeButton(R.string.button_cancel, null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val pin1 = input1.text.toString()
            val pin2 = input2.text.toString()
            if (pin1.length < 4 || pin1 != pin2) {
                Toast.makeText(this, getString(R.string.error_pin_invalid_or_mismatch), Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            currentPinHash = PinUtil.hashPin(pin1)
            val policy = buildPolicy()
            if (!validatePolicy(policy)) {
                return@setOnClickListener
            }
            store.savePolicy(policy)
            applier.apply(policy)
            Toast.makeText(this, getString(R.string.toast_pin_updated_saved), Toast.LENGTH_SHORT).show()
            refreshDeviceState()
            dialog.dismiss()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SELECT_APPS && resultCode == RESULT_OK) {
            val selected = data?.getStringArrayListExtra(AppsSelectionActivity.EXTRA_SELECTED)
                ?.toSet() ?: emptySet()
            selectedPackages.clear()
            if (switchSingleAppMode.isChecked && selected.size > 1) {
                selected.firstOrNull()?.let { selectedPackages.add(it) }
                Toast.makeText(this, getString(R.string.toast_single_mode_keep_one), Toast.LENGTH_SHORT).show()
            } else {
                selectedPackages.addAll(selected)
            }
            updateAllowedAppsText()
        }
    }

    companion object {
        private const val REQUEST_SELECT_APPS = 2001
        fun newIntent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }
}
