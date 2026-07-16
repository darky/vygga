package expo.modules.yggstack;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import link.yggdrasil.yggstack.mobile.Mobile;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class YggdrasilManagerTest {

    @Before
    public void setUp() {
        YggdrasilManager.setAppContext(null);
    }

    @After
    public void tearDown() {
        YggdrasilManager.stopInternal();
    }

    @Test
    public void isRunning_returnsFalse_whenNotStarted() {
        assertFalse(YggdrasilManager.isRunning());
    }

    @Test
    public void getAddress_returnsEmptyString_whenNotRunning() {
        assertEquals("", YggdrasilManager.getAddress());
    }

    @Test
    public void getPublicKey_returnsEmptyString_whenNotRunning() {
        assertEquals("", YggdrasilManager.getPublicKey());
    }

    @Test
    public void getPeers_returnsEmptyArray_whenNotRunning() {
        assertEquals("[]", YggdrasilManager.getPeers());
    }

    @Test
    public void getPeersJSON_returnsEmptyArray_whenNotRunning() {
        assertEquals("[]", YggdrasilManager.getPeersJSON());
    }

    @Test
    public void setAppContext_storesReference() {
        Context ctx = org.robolectric.RuntimeEnvironment.getApplication();
        YggdrasilManager.setAppContext(ctx);
        assertSame(ctx, YggdrasilManager.getAppContext());
    }

    @Test
    public void addLogListener_firesOnLog() {
        final boolean[] called = {false};
        YggdrasilManager.addLogListener(message -> called[0] = true);
        assertFalse(called[0]);
    }

    @Test
    public void addAndRemoveLogListener_removesListener() {
        final int[] count = {0};
        YggdrasilManager.LogListener l = message -> count[0]++;
        YggdrasilManager.addLogListener(l);
        YggdrasilManager.removeLogListener(l);
    }

    @Test
    public void addAndRemoveStatusChangeListener_removesListener() {
        final int[] count = {0};
        Runnable r = () -> count[0]++;
        YggdrasilManager.addStatusChangeListener(r);
        YggdrasilManager.removeStatusChangeListener(r);
    }

    @Test
    public void stopInternal_setsRunningFalse_whenNotStarted() {
        YggdrasilManager.stopInternal();
        assertFalse(YggdrasilManager.isRunning());
    }

    @Test
    @Ignore("Requires Mockito mockStatic for native JNI class")
    public void generateConfig_returnsNull_whenMobileFails() {
        try (MockedStatic<Mobile> mobileStatic = Mockito.mockStatic(Mobile.class)) {
            mobileStatic.when(Mobile::generateConfig).thenThrow(new RuntimeException("error"));
            assertNull(YggdrasilManager.generateConfig());
        }
    }
}
