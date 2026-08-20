package org.academy.internal.client.gui.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PropsConfirmationInputTest {
    @Test
    fun acceptsLocalizedConsoleChoicesCaseInsensitively() {
        assertEquals(PropsConfirmationAnswer.ACCEPT, parsePropsConfirmation("Y"))
        assertEquals(PropsConfirmationAnswer.ACCEPT, parsePropsConfirmation(" y "))
        assertEquals(PropsConfirmationAnswer.RANDOM, parsePropsConfirmation("N"))
        assertEquals(PropsConfirmationAnswer.RANDOM, parsePropsConfirmation(" n "))
    }

    @Test
    fun rejectsCommandsOutsideTheConfirmationChoices() {
        assertEquals(PropsConfirmationAnswer.INVALID, parsePropsConfirmation("learn"))
        assertEquals(PropsConfirmationAnswer.INVALID, parsePropsConfirmation("yes"))
        assertEquals(PropsConfirmationAnswer.INVALID, parsePropsConfirmation(""))
    }
}
