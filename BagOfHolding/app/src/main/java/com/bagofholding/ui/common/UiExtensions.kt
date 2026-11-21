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

/** "Level 5 Dwarf Fighter" or just "Level 5". */
fun PlayerCharacter.describe(context: Context): String {
    val details = listOf(race, characterClass).filter { it.isNotBlank() }.joinToString(" ")
    return if (details.isEmpty()) {
        context.getString(R.string.character_level, level)
    } else {
        context.getString(R.string.character_subtitle, level, details)
    }
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
