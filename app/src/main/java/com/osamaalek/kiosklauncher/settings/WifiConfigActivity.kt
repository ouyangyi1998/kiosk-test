package com.osamaalek.kiosklauncher.settings

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.view.inputmethod.InputMethodManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.osamaalek.kiosklauncher.R

class WifiConfigActivity : AppCompatActivity() {

    private lateinit var ssidEdit: EditText
    private lateinit var passwordEdit: EditText
    private lateinit var securitySpinner: Spinner
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button
    private lateinit var scanResultsLabel: TextView
    private lateinit var scanResultsList: ListView
    private lateinit var scrollView: ScrollView

    private var connectivityManager: ConnectivityManager? = null
    private var wifiManager: WifiManager? = null
    private var pendingNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiScanReceiver: BroadcastReceiver? = null
    private var lastScanSuccessTimeMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_config)

        ssidEdit = findViewById(R.id.edit_wifi_ssid)
        passwordEdit = findViewById(R.id.edit_wifi_password)
        securitySpinner = findViewById(R.id.spinner_security)
        scanButton = findViewById(R.id.button_scan)
        connectButton = findViewById(R.id.button_connect)
        scanResultsLabel = findViewById(R.id.text_scan_results_label)
        scanResultsList = findViewById(R.id.list_scanned_networks)
        scrollView = findViewById(R.id.scroll_wifi_config)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        setupSecuritySpinner()
        setupButtons()
        ssidEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) startWifiScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelPendingConnection()
        unregisterWifiScanReceiver()
    }

    private fun setupSecuritySpinner() {
        val securityTypes = listOf(
            SecurityType.WPA2,
            SecurityType.WPA3,
            SecurityType.OPEN
        )
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            securityTypes.map { getString(it.displayRes) }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        securitySpinner.adapter = adapter

        securitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isOpen = securityTypes[position] == SecurityType.OPEN
                passwordEdit.visibility = if (isOpen) View.GONE else View.VISIBLE
                findViewById<TextView>(R.id.text_password_label).visibility =
                    if (isOpen) View.GONE else View.VISIBLE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        scanButton.setOnClickListener { startWifiScan() }
        connectButton.setOnClickListener { connectToNetwork() }
    }

    private fun startWifiScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION
                )
                return
            }
        }

        val wm = wifiManager ?: return
        if (!wm.isWifiEnabled) {
            Toast.makeText(this, getString(R.string.wifi_please_enable), Toast.LENGTH_SHORT).show()
            return
        }

        unregisterWifiScanReceiver()
        wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                unregisterWifiScanReceiver()
                if (isDestroyed) return
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                } else {
                    true
                }
                if (!success) {
                    runOnUiThread {
                        if (!isDestroyed) {
                            Toast.makeText(this@WifiConfigActivity, getString(R.string.wifi_scan_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                    return
                }
                processScanResults(wm.scanResults)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        }

        val success = wm.startScan()
        if (!success) {
            unregisterWifiScanReceiver()
            val throttled = lastScanSuccessTimeMs > 0 &&
                (System.currentTimeMillis() - lastScanSuccessTimeMs) < SCAN_THROTTLE_WINDOW_MS
            Toast.makeText(
                this,
                getString(if (throttled) R.string.wifi_scan_throttled else R.string.wifi_scan_failed),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, getString(R.string.wifi_scanning), Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun processScanResults(results: List<android.net.wifi.ScanResult>) {
        if (results.isEmpty()) {
            runOnUiThread {
                if (!isDestroyed) {
                    Toast.makeText(this, getString(R.string.wifi_scan_no_results), Toast.LENGTH_SHORT).show()
                    scanResultsLabel.visibility = View.GONE
                    scanResultsList.visibility = View.GONE
                }
            }
            return
        }

        val currentSsid = getCurrentConnectedSsid()
        val sortedResults = results.distinctBy { it.SSID }.sortedBy { it.SSID }
        val items = sortedResults.map { result ->
            val displayName = result.SSID.ifEmpty { getString(R.string.wifi_hidden_network) }
            val isConnected = currentSsid != null && result.SSID == currentSsid
            Pair(displayName, isConnected)
        }
        val securityTypes = listOf(SecurityType.WPA2, SecurityType.WPA3, SecurityType.OPEN)

        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            scanResultsLabel.visibility = View.VISIBLE
            scanResultsList.visibility = View.VISIBLE
            scanResultsList.adapter = object : ArrayAdapter<Pair<String, Boolean>>(this, R.layout.list_item_wifi_network, items) {
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    val view = convertView ?: layoutInflater.inflate(R.layout.list_item_wifi_network, parent, false)
                    val item = getItem(position)!!
                    view.findViewById<TextView>(R.id.text_wifi_ssid).text = item.first
                    view.findViewById<TextView>(R.id.text_wifi_connected_badge).visibility =
                        if (item.second) View.VISIBLE else View.GONE
                    return view
                }
            }
            scanResultsList.setOnItemClickListener { _, _, position, _ ->
                val result = sortedResults[position]
                ssidEdit.setText(result.SSID)
                val security = parseSecurityFromCapabilities(result.capabilities)
                securitySpinner.setSelection(securityTypes.indexOf(security))
                val isOpen = security == SecurityType.OPEN
                passwordEdit.visibility = if (isOpen) View.GONE else View.VISIBLE
                findViewById<TextView>(R.id.text_password_label).visibility =
                    if (isOpen) View.GONE else View.VISIBLE
            }
            hideKeyboard(ssidEdit)
            ssidEdit.clearFocus()
            scrollView.requestFocus()
            scanResultsList.post {
                val y = scanResultsList.top - scrollView.paddingTop
                if (y > 0) scrollView.smoothScrollTo(0, y)
            }
            Toast.makeText(this, getString(R.string.wifi_scan_success, items.size), Toast.LENGTH_SHORT).show()
            lastScanSuccessTimeMs = System.currentTimeMillis()
        }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentConnectedSsid(): String? {
        val info = wifiManager?.connectionInfo ?: return null
        val ssid = info.ssid ?: return null
        if (ssid == "<unknown ssid>") return null
        return ssid.trim('"')
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun parseSecurityFromCapabilities(capabilities: String?): SecurityType {
        val cap = capabilities ?: return SecurityType.OPEN
        return when {
            cap.contains("SAE") -> SecurityType.WPA3
            cap.contains("PSK") || cap.contains("WPA2-PSK") || cap.contains("WPA-PSK") -> SecurityType.WPA2
            else -> SecurityType.OPEN
        }
    }

    private fun unregisterWifiScanReceiver() {
        try {
            wifiScanReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) { /* not registered */ }
        wifiScanReceiver = null
    }

    private fun connectToNetwork() {
        val ssid = ssidEdit.text.toString().trim()
        if (ssid.isBlank()) {
            Toast.makeText(this, getString(R.string.wifi_ssid_required), Toast.LENGTH_SHORT).show()
            return
        }

        val securityType = when (securitySpinner.selectedItemPosition) {
            0 -> SecurityType.WPA2
            1 -> SecurityType.WPA3
            else -> SecurityType.OPEN
        }

        val password = passwordEdit.text.toString()

        if (securityType != SecurityType.OPEN && password.isBlank()) {
            Toast.makeText(this, getString(R.string.wifi_password_required), Toast.LENGTH_SHORT).show()
            return
        }

        connectWithSpecifier(ssid, securityType, password)
    }

    private fun connectWithSpecifier(ssid: String, securityType: SecurityType, password: String) {
        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)

        when (securityType) {
            SecurityType.WPA2 -> specifierBuilder.setWpa2Passphrase(password)
            SecurityType.WPA3 -> specifierBuilder.setWpa3Passphrase(password)
            SecurityType.OPEN -> { /* no auth */ }
        }

        val specifier = specifierBuilder.build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        cancelPendingConnection()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    if (!isDestroyed) {
                        Toast.makeText(this@WifiConfigActivity, getString(R.string.wifi_connected), Toast.LENGTH_SHORT).show()
                    }
                    cancelPendingConnection()
                }
            }
            override fun onUnavailable() {
                runOnUiThread {
                    if (!isDestroyed) {
                        Toast.makeText(this@WifiConfigActivity, getString(R.string.wifi_connect_failed), Toast.LENGTH_SHORT).show()
                    }
                    cancelPendingConnection()
                }
            }
            override fun onLost(network: Network) {
                runOnUiThread { cancelPendingConnection() }
            }
        }
        pendingNetworkCallback = callback

        try {
            connectivityManager?.requestNetwork(request, callback, 60_000)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.wifi_connect_failed) + ": ${e.message}", Toast.LENGTH_LONG).show()
            cancelPendingConnection()
        }
    }

    private fun cancelPendingConnection() {
        pendingNetworkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) { /* already unregistered */ }
            pendingNetworkCallback = null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startWifiScan()
        } else if (requestCode == REQUEST_LOCATION) {
            Toast.makeText(this, getString(R.string.wifi_location_required), Toast.LENGTH_SHORT).show()
        }
    }

    private enum class SecurityType(val displayRes: Int) {
        WPA2(R.string.wifi_security_wpa2),
        WPA3(R.string.wifi_security_wpa3),
        OPEN(R.string.wifi_security_open)
    }

    companion object {
        private const val REQUEST_LOCATION = 3001
        private const val SCAN_THROTTLE_WINDOW_MS = 120_000L  // 2 min (Android: 4 scans per 2 min)

        fun newIntent(context: Context): Intent {
            return Intent(context, WifiConfigActivity::class.java)
        }
    }
}
