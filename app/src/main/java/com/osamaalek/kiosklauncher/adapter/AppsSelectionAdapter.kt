package com.osamaalek.kiosklauncher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.osamaalek.kiosklauncher.R
import com.osamaalek.kiosklauncher.model.AppInfo

class AppsSelectionAdapter(
    private val list: List<AppInfo>,
    private val selected: MutableSet<String>,
    private val singleSelection: Boolean,
    private val onSelectionChanged: (Set<String>) -> Unit
) : RecyclerView.Adapter<AppsSelectionAdapter.ContentHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_app_select, parent, false)
        return ContentHolder(view)
    }

    override fun onBindViewHolder(holder: ContentHolder, position: Int) {
        val app = list[position]
        holder.textView.text = app.label
        holder.imageView.setImageDrawable(app.icon)
        val packageName = app.packageName?.toString() ?: ""
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selected.contains(packageName)

        holder.itemView.setOnClickListener {
            if (packageName.isNotBlank()) {
                toggleSelection(packageName)
                if (!singleSelection) {
                    notifyItemChanged(position)
                }
            }
        }
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (packageName.isBlank()) return@setOnCheckedChangeListener
            if (isChecked) {
                if (singleSelection) {
                    selected.clear()
                }
                selected.add(packageName)
            } else {
                selected.remove(packageName)
            }
            if (singleSelection) {
                notifyDataSetChanged()
            }
            onSelectionChanged(selected)
        }
    }

    override fun getItemCount(): Int = list.size

    private fun toggleSelection(packageName: String) {
        if (selected.contains(packageName)) {
            selected.remove(packageName)
        } else {
            if (singleSelection) {
                selected.clear()
            }
            selected.add(packageName)
        }
        if (singleSelection) {
            notifyDataSetChanged()
        }
        onSelectionChanged(selected)
    }

    class ContentHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.app_icon)
        val textView: TextView = itemView.findViewById(R.id.app_name)
        val checkBox: CheckBox = itemView.findViewById(R.id.app_check)
    }
}
