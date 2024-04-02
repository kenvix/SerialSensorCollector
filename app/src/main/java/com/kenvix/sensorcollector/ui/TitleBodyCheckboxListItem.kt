package com.kenvix.sensorcollector.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.kenvix.sensorcollector.databinding.TitleBodyCheckboxListEntryBinding


data class TitleBodyCheckboxListItem(val title: String, val body: String, var isChecked: Boolean = false)

class TitleBodyCheckboxListAdapter(
    context: Context,
    private val items: List<TitleBodyCheckboxListItem>
) :
    ArrayAdapter<TitleBodyCheckboxListItem>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            TitleBodyCheckboxListEntryBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            TitleBodyCheckboxListEntryBinding.bind(convertView)
        }

        val item = items[position]
        binding.itemTitle.text = item.title

        binding.itemBody.text = item.body
        binding.checkBox.isChecked = item.isChecked

        binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
            item.isChecked = isChecked
        }

        return binding.root
    }
}
