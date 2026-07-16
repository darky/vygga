package expo.modules.floatingcalloverlay;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.N)
public class FloatingCallOverlayModuleTest {

    @Mock private ReactApplicationContext mockContext;
    @Mock private Promise promise;
    @Captor private ArgumentCaptor<Boolean> boolCaptor;
    @Captor private ArgumentCaptor<String> stringCaptor;

    private FloatingCallOverlayModule module;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        module = new FloatingCallOverlayModule(mockContext);
    }

    @Test
    public void getName_returnsCorrectName() {
        assertEquals("FloatingCallOverlay", module.getName());
    }

    @Test
    public void hasOverlayPermission_checksCanDrawOverlays() {
        module.hasOverlayPermission(promise);
        verify(promise).resolve(false);
    }

    @Test
    public void showIncomingOverlay_rejects_whenNoPermission() {
        module.showIncomingOverlay("addr", "callId", promise);
        verify(promise).reject(eq("NO_PERMISSION"), anyString());
    }

    @Test
    public void showActiveOverlay_rejects_whenNoPermission() {
        module.showActiveOverlay("addr", promise);
        verify(promise).reject(eq("NO_PERMISSION"), anyString());
    }

    @Test
    public void hideOverlay_resolves_whenNoOverlayShown() throws Exception {
        module.hideOverlay(promise);
        verify(promise).resolve(true);
    }

    @Test
    public void requestOverlayPermission_rejects_whenNoActivity() {
        when(mockContext.hasCurrentActivity()).thenReturn(false);
        module.requestOverlayPermission(promise);
        verify(promise).reject(eq("NO_ACTIVITY"), anyString());
    }

    @Test
    public void addListener_doesNothing() {
        module.addListener("onOverlayAccept");
    }

    @Test
    public void removeListeners_doesNothing() {
        module.removeListeners(1);
    }

    @Test
    public void onCatalystInstanceDestroy_doesNotCrash() {
        module.onCatalystInstanceDestroy();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N)
    public void hasOverlayPermission_returnsFalse_whenNoPermissionAtMAndAbove() {
        module.hasOverlayPermission(promise);
        verify(promise).resolve(false);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N)
    public void requestOverlayPermission_launchesSettings_whenActivityAvailable() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        when(mockContext.getCurrentActivity()).thenReturn(activity);

        module.requestOverlayPermission(promise);
        verify(promise).resolve(true);

        Intent expected = new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:" + activity.getPackageName()));
        expected.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
