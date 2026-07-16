package expo.modules.wifilock;

import android.app.Application;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.content.Context;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class WifiLockModuleTest {

    @Mock private ReactApplicationContext mockContext;
    @Mock private Promise promise;
    @Captor private ArgumentCaptor<Boolean> boolCaptor;
    @Captor private ArgumentCaptor<String> stringCaptor;

    private WifiLockModule module;
    private Application app;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        app = RuntimeEnvironment.getApplication();
        when(mockContext.getApplicationContext()).thenReturn(app);
        module = new WifiLockModule(mockContext);
    }

    @Test
    public void getName_returnsCorrectName() {
        assertEquals("WifiLockModule", module.getName());
    }

    @Test
    public void acquire_createsAndAcquiresWifiLock() throws Exception {
        module.acquire("test-tag", promise);
        verify(promise).resolve(true);
    }

    @Test
    public void acquire_returnsTrue_whenAlreadyHeld() throws Exception {
        module.acquire("first", promise);
        module.acquire("second", promise);
        verify(promise, times(2)).resolve(true);
    }

    @Test
    public void release_releasesWifiLock() throws Exception {
        module.acquire("test", mock(Promise.class));
        module.release(promise);
        verify(promise).resolve(true);
    }

    @Test
    public void release_doesNothing_whenNotHeld() throws Exception {
        module.release(promise);
        verify(promise).resolve(true);
    }

    @Test
    public void isHeld_returnsFalse_whenNotAcquired() throws Exception {
        module.isHeld(promise);
        verify(promise).resolve(false);
    }

    @Test
    public void isHeld_returnsTrue_afterAcquire() throws Exception {
        module.acquire("test", mock(Promise.class));
        module.isHeld(promise);
        verify(promise).resolve(true);
    }

    @Test
    public void isHeld_returnsFalse_afterRelease() throws Exception {
        module.acquire("test", mock(Promise.class));
        module.release(mock(Promise.class));
        module.isHeld(promise);
        verify(promise).resolve(false);
    }

    @Test
    public void acquire_rejects_whenNoWifiManager() {
        when(mockContext.getApplicationContext()).thenReturn(mock(Application.class));
        WifiLockModule noWifiModule = new WifiLockModule(mockContext);
        noWifiModule.acquire("test", promise);
        verify(promise).reject(eq("WIFILOCK_NO_WIFI"), anyString());
    }

    @Test
    public void requestMobileNetwork_rejects_whenNoConnectivityManager() {
        module.requestMobileNetwork(promise);
        verify(promise).reject(eq("MOBILENET_NO_CONNECTIVITY"), anyString());
    }

    @Test
    public void releaseMobileNetwork_doesNothing_whenNotRequested() throws Exception {
        module.releaseMobileNetwork(promise);
        verify(promise).resolve(true);
    }
}
