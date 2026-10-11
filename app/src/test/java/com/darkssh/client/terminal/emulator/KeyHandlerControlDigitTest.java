package com.darkssh.client.terminal.emulator;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KeyHandlerControlDigitTest {

    @Test
    public void ctrlDigitsUseDistinctCsiUSequences() {
        int[] rowKeyCodes = {
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_9,
        };

        for (int digit = 0; digit <= 9; digit++) {
            assertEquals(
                "\033[" + (48 + digit) + ";5u",
                KeyHandler.getControlDigitCsiUCode(rowKeyCodes[digit], KeyHandler.KEYMOD_CTRL)
            );
        }
    }

    @Test
    public void ctrlNumpadDigitsUseTheSameCsiUSequences() {
        assertEquals("\033[48;5u", KeyHandler.getControlDigitCsiUCode(KeyEvent.KEYCODE_NUMPAD_0, KeyHandler.KEYMOD_CTRL));
        assertEquals("\033[53;5u", KeyHandler.getControlDigitCsiUCode(KeyEvent.KEYCODE_NUMPAD_5, KeyHandler.KEYMOD_CTRL));
        assertEquals("\033[57;5u", KeyHandler.getControlDigitCsiUCode(KeyEvent.KEYCODE_NUMPAD_9, KeyHandler.KEYMOD_CTRL));
    }

    @Test
    public void unsupportedDigitAndAdditionalModifiersKeepLegacyHandling() {
        assertNull(KeyHandler.getControlDigitCsiUCode(KeyEvent.KEYCODE_A, KeyHandler.KEYMOD_CTRL));
        assertNull(
            KeyHandler.getControlDigitCsiUCode(
                KeyEvent.KEYCODE_1,
                KeyHandler.KEYMOD_CTRL | KeyHandler.KEYMOD_SHIFT
            )
        );
        assertNull(KeyHandler.getControlDigitCsiUCode(KeyEvent.KEYCODE_1, 0));
    }
}
