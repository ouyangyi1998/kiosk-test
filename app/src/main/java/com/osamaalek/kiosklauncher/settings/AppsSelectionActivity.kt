package com.osamaalek.kiosklauncher.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osamaalek.kiosklauncher.R
import com.osamaalek.kiosklauncher.adapter.AppsSelectionAdapter
import com.osamaalek.kiosklauncher.util.AppsUtil

class AppsSelectionActivity : AppCompatActivity() {
    private val selectedPackages = LinkedHashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps_selection)

        val preset = intent.getStringArrayListExtra(EXTRA_SELECTED) ?: arrayListOf()
        selectedPackages.addAll(preset)
        val singleAppMode = intent.getBooleanExtra(EXTRA_SINGLE_MODE, false)

        val apps = AppsUtil.getAllApps(this)
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_apps)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = AppsSelectionAdapter(apps, selectedPackages, singleAppMode) {
            // no-op, we read selectedPackages on submit
        }

        title = if (singleAppMode) {
            getString(R.string.title_select_single_app)
        } else {
            getString(R.string.title_select_multiple_apps)
        }

        findViewById<Button>(R.id.button_apps_done).setOnClickListener {
            val data = Intent().putStringArrayListExtra(
                EXTRA_SELECTED,
                ArrayList(selectedPackages)
            )
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    companion object {
        const val EXTRA_SELECTED = "extra_selected_packages"
        const val EXTRA_SINGLE_MODE = "extra_single_mode"

        fun newIntent(context: Context, selected: Set<String>, singleAppMode: Boolean): Intent {
            return Intent(context, AppsSelectionActivity::class.java).putStringArrayListExtra(
                EXTRA_SELECTED,
                ArrayList(selected)
            ).putExtra(EXTRA_SINGLE_MODE, singleAppMode)
        }
    }
}
