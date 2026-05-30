package com.micr.oldf3.client.debug;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class AllocationRateCalculator {
    private static final long SAMPLE_INTERVAL_MILLIS = 500L;
    private static final List<GarbageCollectorMXBean> GARBAGE_COLLECTORS = ManagementFactory.getGarbageCollectorMXBeans();

    private long lastCalculationTime = 0L;
    private long lastAllocatedBytes = -1L;
    private long lastGarbageCollectionCount = -1L;
    private long allocationRate = 0L;

    public long getAllocationRate(long currentAllocatedBytes) {
        long now = System.currentTimeMillis();
        if (now - this.lastCalculationTime < SAMPLE_INTERVAL_MILLIS) {
            return this.allocationRate;
        }

        long garbageCollectionCount = getGarbageCollectionCount();
        if (this.lastCalculationTime != 0L && garbageCollectionCount == this.lastGarbageCollectionCount) {
            double scale = (double)TimeUnit.SECONDS.toMillis(1L) / (double)(now - this.lastCalculationTime);
            long allocatedDelta = currentAllocatedBytes - this.lastAllocatedBytes;
            this.allocationRate = Math.round((double)allocatedDelta * scale);
        }

        this.lastCalculationTime = now;
        this.lastAllocatedBytes = currentAllocatedBytes;
        this.lastGarbageCollectionCount = garbageCollectionCount;
        return this.allocationRate;
    }

    private static long getGarbageCollectionCount() {
        long count = 0L;
        for (GarbageCollectorMXBean collector : GARBAGE_COLLECTORS) {
            count += collector.getCollectionCount();
        }
        return count;
    }
}
