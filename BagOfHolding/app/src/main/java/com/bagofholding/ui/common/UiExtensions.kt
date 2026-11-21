package com.bagofholding.ui.common

import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bagofholding.R
import com.bagofholding.data.model.Coin
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.domain.Encumbrance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Pads the root view so content is not drawn under the status / navigation bars (edge-to-edge). */
fun View.applySystemBarsPadding() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.updatePadding(
            left = bars.left,
            top = bars.top,
            right = bars.right,
            bottom = maxOf(bars.bottom, ime.bottom),
        )
        WindowInsetsCompat.CONSUMED
    }
}

/** Collects [flow] only while [this] is at least STARTED, which avoids leaks and wasted work. */
fun <T> LifecycleOwner.collectWhileStarted(flow: Flow<T>, action: suspend (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

/** Dismisses the dialog when the screen is destroyed (e.g. on rotation) so its window does not leak. */
fun AlertDialog.dismissWith(owner: LifecycleOwner, onDismiss: (() -> Unit)? = null): AlertDialog {
    val observer = object : DefaultLifecycleObserver {
        override fun onDestroy(source: LifecycleOwner) {
            dismiss()
        }
    }
    owner.lifecycle.addObserver(observer)
    setOnDismissListener {
        owner.lifecycle.removeObserver(observer)
        onDismiss?.invoke()
    }
    return this
}

/** "Strength 16" or "Strength 16 · Powerful build". */
fun PlayerCharacter.describe(context: Context): String = context.getString(
    if (powerfulBuild) R.string.character_subtitle_powerful else R.string.character_subtitle,
    strength,
)

@StringRes
fun Coin.labelRes(): Int = when (this) {
    Coin.PP -> R.string.coin_pp
    Coin.GP -> R.string.coin_gp
    Coin.EP -> R.string.coin_ep
    Coin.SP -> R.string.coin_sp
    Coin.CP -> R.string.coin_cp
}

/** Full coin name for dialogs: "Gold pieces (gp)". */
@StringRes
fun Coin.nameRes(): Int = when (this) {
    Coin.PP -> R.string.coin_name_pp
    Coin.GP -> R.string.coin_name_gp
    Coin.EP -> R.string.coin_name_ep
    Coin.SP -> R.string.coin_name_sp
    Coin.CP -> R.string.coin_name_cp
}

@StringRes
fun ItemCategory.labelRes(): Int = when (this) {
    ItemCategory.WEAPON -> R.string.category_weapon
    ItemCategory.ARMOR -> R.string.category_armor
    ItemCategory.GEAR -> R.string.category_gear
    ItemCategory.TOOL -> R.string.category_tool
    ItemCategory.CONSUMABLE -> R.string.category_consumable
    ItemCategory.MAGIC -> R.string.category_magic
    ItemCategory.TREASURE -> R.string.category_treasure
    ItemCategory.OTHER -> R.string.category_other
}

@StringRes
fun Encumbrance.labelRes(): Int = when (this) {
    Encumbrance.UNENCUMBERED -> R.string.encumbrance_none
    Encumbrance.ENCUMBERED -> R.string.encumbrance_encumbered
    Encumbrance.HEAVILY_ENCUMBERED -> R.string.encumbrance_heavy
    Encumbrance.OVER_CAPACITY -> R.string.encumbrance_over
}
