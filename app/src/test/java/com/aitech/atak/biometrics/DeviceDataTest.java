package com.aitech.atak.biometrics;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeviceDataTest {
    private DeviceData device() {
        return new DeviceData("WearTAK", "Team One", false, "watch-1");
    }

    private void add(DeviceData device, String uid, boolean isAlert, long time) {
        device.addAlertOrClearEvent(uid, new Date(time), isAlert,
                isAlert ? AlertLevel.HIGH : AlertLevel.NONE, "HEART_RATE", 150, 1, null);
    }

    @Test
    public void fiveServerCopiesProduceOneAlert() {
        DeviceData device = device();
        for (int i = 0; i < 5; i++) {
            add(device, "alert-1", true, i);
        }

        assertEquals(1, device.alertOrClearEventsEQ.size());
        assertEquals(1, device.numberOfActiveAlerts);
        assertTrue(device.isAlerting);
        assertEquals(AlertLevel.HIGH, device.highestAlertLevel);
        assertEquals(new Date(0), device.timeHighestAlertLevel);
        assertEquals(new Date(0), device.alertOrClearEventsEQ.peek().timestamp);
    }

    @Test
    public void cancellationIsRecordedOnceAndDelayedAlertCopiesDoNotReactivateIt() {
        DeviceData device = device();
        add(device, "alert-1", true, 0);
        for (int i = 0; i < 5; i++) {
            add(device, "alert-1", false, i + 1);
        }
        add(device, "alert-1", true, 6);

        assertEquals(2, device.alertOrClearEventsEQ.size());
        assertEquals(0, device.numberOfActiveAlerts);
        assertFalse(device.isAlerting);
        assertEquals(AlertLevel.NONE, device.highestAlertLevel);
        assertFalse(new ArrayList<>(device.alertOrClearEventsEQ).get(1).isAlert);
    }

    @Test
    public void distinctAlertsRemainSeparateAndDuplicateClearDoesNotClearNewAlert() {
        DeviceData device = device();
        add(device, "alert-1", true, 0);
        add(device, "alert-2", true, 1);
        assertEquals(2, device.numberOfActiveAlerts);

        add(device, "alert-1", false, 2);
        add(device, "alert-3", true, 3);
        add(device, "alert-1", false, 4);

        assertEquals(4, device.alertOrClearEventsEQ.size());
        assertEquals(1, device.numberOfActiveAlerts);
        assertTrue(device.isAlerting);
    }

    @Test
    public void sameUidOnDifferentDevicesIsNotADuplicate() {
        DeviceData first = device();
        DeviceData second = device();
        add(first, "alert-1", true, 0);
        add(second, "alert-1", true, 0);

        assertEquals(1, first.alertOrClearEventsEQ.size());
        assertEquals(1, second.alertOrClearEventsEQ.size());
        assertEquals(1, first.numberOfActiveAlerts);
        assertEquals(1, second.numberOfActiveAlerts);
    }

    @Test
    public void eventsWithoutAnIdentityKeepExistingBehavior() {
        DeviceData device = device();
        add(device, null, true, 0);
        add(device, null, true, 1);
        add(device, "", true, 2);
        add(device, "", true, 3);
        add(device, "  ", true, 4);
        add(device, "  ", true, 5);
        device.addAlertOrClearEvent(new Date(6), true, AlertLevel.HIGH,
                "HEART_RATE", 150, 1, null);

        assertEquals(7, device.alertOrClearEventsEQ.size());
        assertEquals(7, device.numberOfActiveAlerts);
    }

    @Test
    public void delayedDuplicateIsIgnoredAfterVisibleHistoryEvictsIt() {
        DeviceData device = device();
        for (int i = 0; i < 11; i++) {
            add(device, "alert-" + i, true, i);
        }
        add(device, "alert-0", true, 12);

        assertEquals(10, device.alertOrClearEventsEQ.size());
        assertEquals(11, device.numberOfActiveAlerts);
        assertEquals("alert-1", device.alertOrClearEventsEQ.peek().uid);
    }

    @Test
    public void identityCacheEvictsOldestEvents() {
        DeviceData device = device();
        for (int i = 0; i < 257; i++) {
            add(device, "alert-" + i, true, i);
        }
        add(device, "alert-1", true, 258);
        assertEquals(257, device.numberOfActiveAlerts);

        add(device, "alert-0", true, 259);
        assertEquals(258, device.numberOfActiveAlerts);
    }

    @Test
    public void concurrentServerCopiesProduceOneAlert() throws Exception {
        final DeviceData device = device();
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<?>> deliveries = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                deliveries.add(executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                        add(device, "alert-1", true, 0);
                    }
                }));
            }
            start.countDown();
            for (Future<?> delivery : deliveries) {
                delivery.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, device.alertOrClearEventsEQ.size());
        assertEquals(1, device.numberOfActiveAlerts);
    }
}
