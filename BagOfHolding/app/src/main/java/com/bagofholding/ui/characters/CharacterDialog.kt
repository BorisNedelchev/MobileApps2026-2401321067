package com.bagofholding.ui.characters

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bagofholding.R
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.databinding.DialogCharacterBinding
import com.bagofholding.domain.CharacterField
import com.bagofholding.domain.CharacterInput
import com.bagofholding.domain.CharacterValidator
import com.bagofholding.domain.Currency
import com.bagofholding.domain.FormResult
import com.bagofholding.ui.common.dismissWith
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Create / edit character form (Create and Update of the CRUD). */
object CharacterDialog {

    fun show(activity: AppCompatActivity, existing: PlayerCharacter?, onSave: (PlayerCharacter) -> Unit) {
        val binding = DialogCharacterBinding.inflate(activity.layoutInflater)
        existing?.let { fill(binding, it) }

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
                when (val result = CharacterValidator.validate(existing?.id ?: 0L, readInput(binding))) {
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

    private fun fill(binding: DialogCharacterBinding, character: PlayerCharacter) {
        val coins = Currency.split(character.purseCp)
        binding.editName.setText(character.name)
        binding.editClass.setText(character.characterClass)
        binding.editRace.setText(character.race)
        binding.editLevel.setText(character.level.toString())
        binding.editStrength.setText(character.strength.toString())
        binding.editGold.setText(coins.gp.toString())
        binding.editSilver.setText(coins.sp.toString())
        binding.editCopper.setText(coins.cp.toString())
    }

    private fun readInput(binding: DialogCharacterBinding) = CharacterInput(
        name = binding.editName.text.toString(),
        characterClass = binding.editClass.text.toString(),
        race = binding.editRace.text.toString(),
        level = binding.editLevel.text.toString(),
        strength = binding.editStrength.text.toString(),
        gold = binding.editGold.text.toString(),
        silver = binding.editSilver.text.toString(),
        copper = binding.editCopper.text.toString(),
    )

    private fun showErrors(binding: DialogCharacterBinding, errors: Set<CharacterField>) {
        val context = binding.root.context
        fun message(field: CharacterField, res: Int) = if (field in errors) context.getString(res) else null
        binding.inputName.error = message(CharacterField.NAME, R.string.error_name)
        binding.inputLevel.error = message(CharacterField.LEVEL, R.string.error_level)
        binding.inputStrength.error = message(CharacterField.STRENGTH, R.string.error_strength)
        binding.inputGold.error = message(CharacterField.GOLD, R.string.error_coins)
        binding.inputSilver.error = message(CharacterField.SILVER, R.string.error_coins)
        binding.inputCopper.error = message(CharacterField.COPPER, R.string.error_coins)
    }
}
