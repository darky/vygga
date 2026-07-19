package expo.modules.cpulock;

import android.app.Application;
import android.content.Context;
import android.os.PowerManager;

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
public class CpuLockModuleTest {

    @Mock private ReactApplicationContext mockContext;
    @Mock private Promise promise;
    @Captor private ArgumentCaptor<Boolean> boolCaptor;
    @Captor private ArgumentCaptor<String> stringCaptor;

    private CpuLockModule module;
    private Application app;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        app = RuntimeEnvironment.getApplication();
        when(mockContext.getApplicationContext()).thenReturn(app);
        module = new CpuLockModule(mockContext);
    }

    @Test
    public void getName_returnsCorrectName() {
        assertEquals("CpuLockModule", module.getName());
    }

    @Test
    public void acquire_createsAndAcquiresCpuLock() throws Exception {
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
    public void release_releasesCpuLock() throws Exception {
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
    public void acquire_rejects_whenNoPowerManager() {
        when(mockContext.getApplicationContext()).thenReturn(mock(Application.class));
        CpuLockModule noPmModule = new CpuLockModule(mockContext);
        noPmModule.acquire("test", promise);
        verify(promise).reject(eq("CPULOCK_NO_POWER"), anyString());
    }
}
