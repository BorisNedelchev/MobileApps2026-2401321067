package com.bagofholding.ui.characters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bagofholding.R
import com.bagofholding.data.model.CharacterWithStats
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.databinding.ItemCharacterBinding
import com.bagofholding.domain.WeightFormat
import com.bagofholding.ui.common.describe
import java.util.Locale

class CharacterAdapter(
    private val onClick: (PlayerCharacter) -> Unit,
    private val onEdit: (PlayerCharacter) -> Unit,
    private val onDelete: (PlayerCharacter) -> Unit,
) : ListAdapter<CharacterWithStats, CharacterAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemCharacterBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemCharacterBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: CharacterWithStats) {
            val character = entry.character
            val context = binding.root.context
            binding.textAvatar.text = character.name.take(1).uppercase(Locale.getDefault())
            binding.textName.text = character.name
            binding.textSubtitle.text = character.describe(context)
            binding.textStats.text = context.resources.getQuantityString(
                R.plurals.character_stats,
                entry.itemCount,
                entry.itemCount,
                WeightFormat.format(entry.totalWeight),
            )
            binding.root.setOnClickListener { onClick(character) }
            binding.buttonMore.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    inflate(R.menu.menu_character_item)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_edit_character -> onEdit(character)
                            R.id.action_delete_character -> onDelete(character)
                        }
                        true
                    }
                    show()
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<CharacterWithStats>() {
        override fun areItemsTheSame(oldItem: CharacterWithStats, newItem: CharacterWithStats) =
            oldItem.character.id == newItem.character.id

        override fun areContentsTheSame(oldItem: CharacterWithStats, newItem: CharacterWithStats) =
            oldItem == newItem
    }
}
