package com.bagofholding.ui.share

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bagofholding.databinding.ItemHostBinding
import com.bagofholding.network.DiscoveredHost

/** Phones found on the local network via NSD. Tapping one downloads its inventory. */
class HostAdapter(
    private val onClick: (DiscoveredHost) -> Unit,
) : ListAdapter<DiscoveredHost, HostAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemHostBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemHostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(host: DiscoveredHost) {
            binding.textHostName.text = host.displayName
            binding.textHostAddress.text = host.address.toString()
            binding.root.setOnClickListener { onClick(host) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DiscoveredHost>() {
        override fun areItemsTheSame(oldItem: DiscoveredHost, newItem: DiscoveredHost) =
            oldItem.serviceName == newItem.serviceName

        override fun areContentsTheSame(oldItem: DiscoveredHost, newItem: DiscoveredHost) = oldItem == newItem
    }
}
