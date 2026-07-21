package expo.modules.yggstack;

import android.app.AlarmManager;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.PowerManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPowerManager;
import org.robolectric.shadows.ShadowService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class YggdrasilServiceTest {

    private ServiceController<YggdrasilService> controller;
    private YggdrasilService service;
    private MockedStatic<YggdrasilManager> mockedManager;

    @Before
    public void setUp() {
        mockedManager = Mockito.mockStatic(YggdrasilManager.class);
        controller = Robolectric.buildService(YggdrasilService.class);
        service = controller.get();
    }

    @After
    public void tearDown() {
        if (mockedManager != null) {
            mockedManager.close();
        }
    }

    @Test
    public void onCreate_setsAppContext() {
        controller.create();
        mockedManager.verify(() -> YggdrasilManager.setAppContext(service.getApplicationContext()));
    }

    @Test
    public void onStartCommand_doesNotCrash() {
        controller.create().startCommand(0, 0);
    }

    @Test
    public void startYggdrasil_startsManagerAndService() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        YggdrasilService.startYggdrasil(ctx, "{}", "", "");
        mockedManager.verify(() -> YggdrasilManager.start(ctx, "{}", "", ""));
    }

    @Test
    public void startYggdrasil_doesNotStart_whenAlreadyRunning() throws Exception {
        mockedManager.when(YggdrasilManager::isRunning).thenReturn(true);
        Context ctx = RuntimeEnvironment.getApplication();
        YggdrasilService.startYggdrasil(ctx, "{}", "", "");
        mockedManager.verify(YggdrasilManager::isRunning);
        mockedManager.verifyNoMoreInteractions();
    }

    @Test
    public void stopYggdrasil_stopsManager() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        YggdrasilService.stopYggdrasil(ctx);
        mockedManager.verify(() -> YggdrasilManager.stop(ctx));
    }

    @Test
    public void onDestroy_stopsManagerInternally() {
        controller.create();
        controller.destroy();
        mockedManager.verify(YggdrasilManager::stopInternal);
    }

    @Test
    public void lifecycle_createStartDestroy() {
        controller.create().startCommand(0, 0).destroy();
        mockedManager.verify(() -> YggdrasilManager.setAppContext(any()));
        mockedManager.verify(YggdrasilManager::stopInternal);
    }

    @Test
    public void onBind_returnsNull() {
        controller.create();
        assertNull(service.onBind(new Intent()));
    }

    @Test
    public void foregroundNotification_hasNoClearFlag() {
        controller.create().startCommand(0, 0);
        ShadowService shadowService = shadowOf(service);
        Notification notification = shadowService.getLastForegroundNotification();
        assertNotNull(notification);
        assertTrue("FLAG_NO_CLEAR must be set", (notification.flags & Notification.FLAG_NO_CLEAR) != 0);
        assertTrue("FLAG_ONGOING_EVENT must be set", (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
    }

    @Test
    public void onStartCommand_acquiresWakeLock() {
        controller.create().startCommand(0, 0);
        PowerManager.WakeLock wakeLock = ShadowPowerManager.getLatestWakeLock();
        assertNotNull("WakeLock should be created", wakeLock);
        assertTrue("WakeLock should be held after onStartCommand", wakeLock.isHeld());
    }

    @Test
    public void onDestroy_releasesWakeLock() {
        controller.create().startCommand(0, 0);
        PowerManager.WakeLock wakeLock = ShadowPowerManager.getLatestWakeLock();
        controller.destroy();
        assertFalse("WakeLock should be released after onDestroy", wakeLock.isHeld());
    }

    @Test
    public void onTimeout_schedulesRestartAlarm() {
        controller.create().startCommand(0, 0);
        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        AlarmManager alarmMgr = (AlarmManager) RuntimeEnvironment.getApplication()
            .getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarm = Shadows.shadowOf(alarmMgr);
        ShadowAlarmManager.ScheduledAlarm nextAlarm = shadowAlarm.peekNextScheduledAlarm();
        assertNotNull("Restart alarm should be scheduled after onTimeout", nextAlarm);
        assertNotNull("Restart alarm PendingIntent should not be null", nextAlarm.operation);
    }

    @Test
    public void onTimeout_thenOnDestroy_doesNotCancelRestartAlarm() {
        controller.create().startCommand(0, 0);
        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        controller.destroy();
        AlarmManager alarmMgr = (AlarmManager) RuntimeEnvironment.getApplication()
            .getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarm = Shadows.shadowOf(alarmMgr);
        ShadowAlarmManager.ScheduledAlarm nextAlarm = shadowAlarm.peekNextScheduledAlarm();
        assertNotNull("Restart alarm should survive onDestroy (different request code)",
            nextAlarm);
    }

    @Test
    public void keepaliveAction_doesNotReacquireLocks() {
        controller.create().startCommand(0, 0);
        PowerManager.WakeLock wakeLock = ShadowPowerManager.getLatestWakeLock();
        Intent keepaliveIntent = new Intent(service, YggdrasilService.class);
        keepaliveIntent.setAction("keepalive");
        service.onStartCommand(keepaliveIntent, 0, 0);
        assertTrue("WakeLock should still be held after keepalive", wakeLock.isHeld());
    }
}
