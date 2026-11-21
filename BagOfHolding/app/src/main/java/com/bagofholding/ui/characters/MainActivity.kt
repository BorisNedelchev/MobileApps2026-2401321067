package com.bagofholding.ui.characters

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bagofholding.R
import com.bagofholding.appContainer
import com.bagofholding.data.model.CharacterWithStats
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.databinding.ActivityMainBinding
import com.bagofholding.ui.common.ThemePreferences
import com.bagofholding.ui.common.applySystemBarsPadding
import com.bagofholding.ui.common.collectWhileStarted
import com.bagofholding.ui.common.dismissWith
import com.bagofholding.ui.inventory.InventoryActivity
import com.bagofholding.ui.share.ShareActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Start screen: the list of characters (Read + Delete; Create/Update via [CharacterDialog]). */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: CharacterListViewModel by viewModels { CharacterListViewModel.factory(appContainer) }

    private val adapter = CharacterAdapter(
        onClick = { openInventory(it.id) },
        onEdit = { showCharacterDialog(it) },
        onDelete = { confirmDelete(it) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.toolbar.setOnMenuItemClickListener(::onMenuItemSelected)
        binding.recyclerCharacters.adapter = adapter
        binding.fabAddCharacter.setOnClickListener { showCharacterDialog(null) }

        collectWhileStarted(viewModel.characters) { render(it) }
    }

    private fun render(characters: List<CharacterWithStats>?) {
        binding.progressLoading.isVisible = characters == null
        if (characters == null) return
        adapter.submitList(characters)
        binding.textEmpty.isVisible = characters.isEmpty()
    }

    private fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_receive -> {
            startActivity(ShareActivity.intent(this, characterId = 0L))
            true
        }
        R.id.action_theme -> {
            showThemeDialog()
            true
        }
        else -> false
    }

    private fun openInventory(characterId: Long) {
        startActivity(InventoryActivity.intent(this, characterId))
    }

    private fun showCharacterDialog(existing: PlayerCharacter?) {
        CharacterDialog.show(this, existing) { viewModel.save(it) }
    }

    private fun confirmDelete(character: PlayerCharacter) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.character_delete_title)
            .setMessage(getString(R.string.character_delete_message, character.name))
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(character.id) }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
            .dismissWith(this)
            .show()
    }

    private fun showThemeDialog() {
        val themePreferences = appContainer.themePreferences
        val modes = ThemePreferences.Mode.entries
        val labels = modes.map { mode ->
            getString(
                when (mode) {
                    ThemePreferences.Mode.SYSTEM -> R.string.theme_system
                    ThemePreferences.Mode.LIGHT -> R.string.theme_light
                    ThemePreferences.Mode.DARK -> R.string.theme_dark
                },
            )
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_theme)
            .setSingleChoiceItems(labels, modes.indexOf(themePreferences.mode)) { dialog, which ->
                dialog.dismiss()
                themePreferences.mode = modes[which]
            }
            .create()
            .dismissWith(this)
            .show()
    }
}
