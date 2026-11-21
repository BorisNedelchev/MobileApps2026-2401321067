package com.bagofholding.ui.inventory

import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bagofholding.R
import com.bagofholding.data.model.Coin
import com.bagofholding.databinding.DialogCoinBinding
import com.bagofholding.domain.CoinOperation
import com.bagofholding.domain.Currency
import com.bagofholding.domain.PurseRules
import com.bagofholding.ui.common.dismissWith
import com.bagofholding.ui.common.nameRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Add, remove or set the number of one coin type (e.g. "+150 gp" after a fight). */
object CoinDialog {

    fun show(activity: AppCompatActivity, coin: Coin, current: Long, onSave: (newAmount: Long) -> Unit) {
        val binding = DialogCoinBinding.inflate(activity.layoutInflater)
        val label = Currency.label(coin)
        binding.textCoinCurrent.text = activity.getString(R.string.coin_current, current, label)
        binding.toggleOperation.check(R.id.buttonOpAdd)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(coin.nameRes())
            .setView(binding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
            .dismissWith(activity)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        dialog.setOnShowListener {
            binding.editCoinAmount.requestFocus()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val operation = when (binding.toggleOperation.checkedButtonId) {
                    R.id.buttonOpRemove -> CoinOperation.REMOVE
                    R.id.buttonOpSet -> CoinOperation.SET
                    else -> CoinOperation.ADD
                }
                val amount = binding.editCoinAmount.text?.toString()?.trim()?.toLongOrNull()
                val result = amount?.let { PurseRules.apply(current, operation, it) }
                if (result != null) {
                    onSave(result)
                    dialog.dismiss()
                } else {
                    binding.inputCoinAmount.error = if (amount != null && operation == CoinOperation.REMOVE) {
                        activity.getString(R.string.error_not_enough_coins, current, label)
                    } else {
                        activity.getString(R.string.error_coins)
                    }
                }
            }
        }
        dialog.show()
    }
}
