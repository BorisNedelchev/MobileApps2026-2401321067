package com.bagofholding

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.bagofholding.ui.characters.MainActivity
import org.hamcrest.Matcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Main user scenario: create a character, open its inventory, add a custom item,
 * increase its quantity with the "+" button and open the item's description page.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainScenarioTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun createCharacterAddItemAndIncreaseQuantity() {
        val characterName = "Espresso ${System.currentTimeMillis() % 100_000}"

        // Create the character
        onView(withId(R.id.fabAddCharacter)).perform(click())
        onView(withId(R.id.editName)).perform(replaceText(characterName), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).perform(click())

        // Open its inventory (the database write happens on a background thread)
        waitUntilDisplayed(withText(characterName))
        onView(withText(characterName)).perform(click())
        waitUntilDisplayed(withId(R.id.fabAddItem))

        // Add a custom item with quantity 3
        onView(withId(R.id.fabAddItem)).perform(click())
        onView(withText(R.string.add_custom)).perform(click())
        onView(withId(R.id.editItemName)).perform(replaceText("Rope of Testing"), closeSoftKeyboard())
        onView(withId(R.id.editQuantity)).perform(replaceText("3"), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).perform(click())

        waitUntilDisplayed(withText("Rope of Testing"))
        onView(withId(R.id.textQuantity)).check(matches(withText("3")))

        // Quick add: "+" increases the stack
        onView(withId(R.id.buttonPlus)).perform(click())
        waitUntil { onView(withId(R.id.textQuantity)).check(matches(withText("4"))) }

        // Tapping the item shows its description page; the feather button opens the editor
        onView(withText("Rope of Testing")).perform(click())
        waitUntil { onView(withId(R.id.textDetailName)).check(matches(withText("Rope of Testing"))) }
        onView(withId(R.id.textDetailQuantity)).check(matches(withText("4")))
        onView(withId(R.id.fabEdit)).perform(click())
        onView(withId(R.id.editItemName)).check(matches(withText("Rope of Testing")))
    }

    private fun waitUntilDisplayed(matcher: Matcher<View>) = waitUntil {
        onView(matcher).check(matches(isDisplayed()))
    }

    /** Retries [assertion] for up to [timeoutMs]; the repositories update the UI asynchronously. */
    private fun waitUntil(timeoutMs: Long = 5_000, assertion: () -> Unit) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            try {
                assertion()
                return
            } catch (e: NoMatchingViewException) {
                if (System.currentTimeMillis() > deadline) throw e
            } catch (e: AssertionError) {
                if (System.currentTimeMillis() > deadline) throw e
            }
            Thread.sleep(100)
        }
    }
}
