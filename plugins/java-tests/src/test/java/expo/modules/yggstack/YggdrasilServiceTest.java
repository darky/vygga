package expo.modules.yggstack;

import android.content.Context;
import android.content.Intent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;

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
}
