package tw.linsy.aelorn.discordbridge;

import java.util.concurrent.atomic.LongAdder;

/** Contention-free counters; incremented from DiscordSRV/chat threads. */
final class BridgeMetrics {

    private final LongAdder minecraftSeen = new LongAdder();
    private final LongAdder minecraftForwarded = new LongAdder();
    private final LongAdder minecraftBlocked = new LongAdder();
    private final LongAdder discordSeen = new LongAdder();
    private final LongAdder discordForwarded = new LongAdder();
    private final LongAdder discordBlocked = new LongAdder();
    private final LongAdder interactiveShares = new LongAdder();

    void minecraftSeen() {
        minecraftSeen.increment();
    }

    void minecraftForwarded() {
        minecraftForwarded.increment();
    }

    void minecraftBlocked() {
        minecraftBlocked.increment();
    }

    void discordSeen() {
        discordSeen.increment();
    }

    void discordForwarded() {
        discordForwarded.increment();
    }

    void discordBlocked() {
        discordBlocked.increment();
    }

    void interactiveShares(int count) {
        interactiveShares.add(count);
    }

    Snapshot snapshot() {
        return new Snapshot(minecraftSeen.sum(), minecraftForwarded.sum(), minecraftBlocked.sum(),
            discordSeen.sum(), discordForwarded.sum(), discordBlocked.sum(), interactiveShares.sum());
    }

    record Snapshot(long minecraftSeen, long minecraftForwarded, long minecraftBlocked,
                    long discordSeen, long discordForwarded, long discordBlocked, long interactiveShares) {
    }
}
