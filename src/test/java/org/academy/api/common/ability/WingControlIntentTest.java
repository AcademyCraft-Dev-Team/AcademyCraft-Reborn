package org.academy.api.common.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WingControlIntentTest {
    @Test void stableDirectionSendsFiveHeartbeatsPerSecondIncludingDiagonal() {
        for (int buttons : new int[]{0, 1, 5, 16}) {
            var sender = new WingControlIntent.Sender(); int packets = 0;
            for (int tick = 0; tick < 20; tick++)
                if (sender.shouldSend(new WingControlIntent(buttons, 30, 5), tick * 50_000_000L)) packets++;
            assertEquals(5, packets);
        }
    }
    @Test void turnsAndReleaseAreImmediateButSameTickCannotFlood() {
        var sender = new WingControlIntent.Sender();
        assertTrue(sender.shouldSend(new WingControlIntent(1, 179.8f, 0), 0));
        assertFalse(sender.shouldSend(new WingControlIntent(1, -179.8f, 0), 1));
        assertTrue(sender.shouldSend(new WingControlIntent(1, -177, 0), 2));
        assertFalse(sender.shouldSend(new WingControlIntent(1, 80, 0), 2));
        assertTrue(sender.shouldSend(new WingControlIntent(0, -177, 0), 3));
    }
    @Test void reliableBurstsDoNotMultiplyMotionAndDuplicatesDoNotRefreshTimeout() {
        var mailbox = new WingControlIntent.Mailbox();
        var boost = new WingControlIntent(16, 0, 0);
        for (int i = 1; i <= 20; i++) assertTrue(mailbox.accept(i, boost, 10 * 50_000_000L));
        assertEquals(boost, mailbox.sample(19 * 50_000_000L, 0, 0));
        assertFalse(mailbox.accept(20, boost, 19 * 50_000_000L));
        assertEquals(0, mailbox.sample(20 * 50_000_000L, 0, 0).buttons());
        assertTrue(mailbox.accept(21, new WingControlIntent(5, 30, 0), 21 * 50_000_000L));
        for (int tick = 21; tick < 25; tick++) assertEquals(5, mailbox.sample(tick * 50_000_000L, 0, 0).buttons());
    }
    @Test void inputValidationAndOpposingKeysAreDeterministic() {
        assertEquals(0, new WingControlIntent(15, 0, 0).buttons());
        assertEquals(16, new WingControlIntent(31, 0, 0).buttons());
        assertThrows(IllegalArgumentException.class, () -> new WingControlIntent(32, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WingControlIntent(1, Float.NaN, 0));
        assertEquals(90, new WingControlIntent(0, 720, 100).pitch());
    }
    @Test void expiredBoostBecomesHoverEvenWhenWorldTicksHaveStopped() {
        var mailbox = new WingControlIntent.Mailbox();
        mailbox.accept(1, new WingControlIntent(16, 0, 0, 0), 0);
        assertEquals(16, mailbox.sample(499_000_000L, 0, 0).buttons());
        var hover = mailbox.sample(3_000_000_000L, 0, 0);
        assertEquals(0, hover.buttons());
        assertEquals(0, hover.remainingMomentum());
    }

    @Test void newestReleaseWinsAStalledPacketBurst() {
        var mailbox = new WingControlIntent.Mailbox();
        for (int i = 0; i < 200; i++) mailbox.accept(i, new WingControlIntent(16, 0, 0), 0);
        mailbox.accept(200, new WingControlIntent(0, 0, 0, 0.5f), 0);
        assertEquals(0, mailbox.sample(1, 0, 0).buttons());
        assertEquals(0.5f, mailbox.sample(1, 0, 0).remainingMomentum());
    }

    @Test void settingsChangesSendImmediatelyAndCodecPreservesThem() {
        var sender = new WingControlIntent.Sender();
        var initial = new WingControlIntent(5, 30, 0);
        var changed = new WingControlIntent(5, 30, 0, 0.37f);
        assertTrue(sender.shouldSend(initial, 0));
        assertTrue(sender.shouldSend(changed, 1));
        var buf = io.netty.buffer.Unpooled.buffer();
        try {
            WingControlIntent.CODEC.encode(buf, changed);
            assertEquals(changed, WingControlIntent.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }
}
