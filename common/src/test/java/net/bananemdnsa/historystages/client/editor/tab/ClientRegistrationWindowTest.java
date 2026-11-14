package net.bananemdnsa.historystages.client.editor.tab;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fabric may start Architectury's client entrypoint before or after ours; the editor window has to
 * open exactly once in both orders.
 */
class ClientRegistrationWindowTest {

    @Test
    void architecturyFirstOpensTheWindowRightAway() {
        List<Runnable> waiting = new ArrayList<>();
        int[] opened = {0};

        ClientRegistrationWindow.openAfterCommonWindow(true, waiting::add, () -> opened[0]++);

        assertEquals(1, opened[0], "SETUP has fired already and will not fire again");
        waiting.forEach(Runnable::run);
        assertEquals(1, opened[0], "opened once");
    }

    @Test
    void oursFirstWaitsForTheCommonWindow() {
        List<Runnable> waiting = new ArrayList<>();
        int[] opened = {0};

        ClientRegistrationWindow.openAfterCommonWindow(false, waiting::add, () -> opened[0]++);

        assertEquals(0, opened[0], "the common window has not run yet");
        waiting.forEach(Runnable::run);
        waiting.forEach(Runnable::run);
        assertEquals(1, opened[0], "opened once CLIENT_SETUP came");
    }
}
