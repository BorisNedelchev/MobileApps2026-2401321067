package com.bagofholding.ui.characters

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bagofholding.R
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.databinding.DialogCharacterBinding
import com.bagofholding.domain.CharacterField
import com.bagofholding.domain.CharacterInput
import com.bagofholding.domain.CharacterValidator
import com.bagofholding.domain.FormResult
import com.bagofholding.ui.common.dismissWith
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Create / edit character form (Create and Update of the CRUD): name, Strength and Powerful Build.
 * The purse is edited on the inventory screen.
 */
object CharacterDialog {

    fun show(activity: AppCompatActivity, existing: PlayerCharacter?, onSave: (PlayerCharacter) -> Unit) {
        val binding = DialogCharacterBinding.inflate(activity.layoutInflater)
        if (existing != null) {
            binding.editName.setText(existing.name)
            binding.editStrength.setText(existing.strength.toString())
            binding.checkPowerfulBuild.isChecked = existing.powerfulBuild
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing == null) R.string.character_new else R.string.character_edit)
            .setView(binding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
            .dismissWith(activity)

        dialog.setOnShowListener {
            // Replace the default click handler so the dialog stays open when validation fails.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val input = CharacterInput(
                    name = binding.editName.text.toString(),
                    strength = binding.editStrength.text.toString(),
                    powerfulBuild = binding.checkPowerfulBuild.isChecked,
                )
                val base = existing ?: PlayerCharacter(name = "")
                when (val result = CharacterValidator.validate(base, input)) {
                    is FormResult.Valid -> {
                        onSave(result.value)
                        dialog.dismiss()
                    }
                    is FormResult.Invalid -> showErrors(binding, result.errors)
                }
            }
        }
        dialog.show()
    }

    private fun showErrors(binding: DialogCharacterBinding, errors: Set<CharacterField>) {
        val context = binding.root.context
        binding.inputName.error =
            if (CharacterField.NAME in errors) context.getString(R.string.error_name) else null
        binding.inputStrength.error =
            if (CharacterField.STRENGTH in errors) context.getString(R.string.error_strength) else null
    }
}
