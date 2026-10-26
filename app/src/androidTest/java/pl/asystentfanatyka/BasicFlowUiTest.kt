package pl.asystentfanatyka

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

/** Podstawowe przepływy UI (./gradlew connectedAndroidTest). */
class BasicFlowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun homeShowsFourMainActions() {
        rule.onNodeWithText("Rozpoznaj rybę").assertIsDisplayed()
        rule.onNodeWithText("Zmierz rybę").assertIsDisplayed()
        rule.onNodeWithText("Kalendarz ochronny").assertIsDisplayed()
        rule.onNodeWithText("Sprawdź przepisy łowiska").assertIsDisplayed()
    }

    @Test fun calendarTabOpens() {
        rule.onNodeWithText("Kalendarz").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Dzień połowu") }
        rule.onNodeWithText("Wróć do dzisiaj").performScrollTo().assertIsDisplayed()
    }

    @Test fun checkWithoutLocalRulesGivesC() {
        // Szczupak 60 cm – bez potwierdzenia regulaminu łowiska wynik nie może być „B”.
        rule.onNodeWithText("Gatunki").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Szczupak") }
        rule.onNodeWithText("Szczupak").performClick()
        rule.onNodeWithText("Sprawdź złowioną rybę tego gatunku").performScrollTo().performClick()
        rule.onNodeWithText("Sprawdź przepisy").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Wynik C") }
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextExists(text: String): Boolean =
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
