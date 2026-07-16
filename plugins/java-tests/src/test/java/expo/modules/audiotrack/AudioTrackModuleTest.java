package expo.modules.audiotrack;

import android.app.Application;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class AudioTrackModuleTest {

    @Mock private ReactApplicationContext mockContext;
    @Mock private Promise promise;

    private AudioTrackModule module;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        Application app = RuntimeEnvironment.getApplication();
        when(mockContext.getApplicationContext()).thenReturn(app);
        module = new AudioTrackModule(mockContext);
    }

    @Test
    public void getName_returnsCorrectName() {
        assertEquals("AudioTrackModule", module.getName());
    }

    @Test
    public void addListener_doesNothing() {
        module.addListener("onAudioData");
    }

    @Test
    public void removeListeners_doesNothing() {
        module.removeListeners(0);
    }

    @Test
    public void createCallChannel_resolves() throws Exception {
        when(mockContext.getSystemService(anyString())).thenReturn(
            RuntimeEnvironment.getApplication().getSystemService(android.content.Context.NOTIFICATION_SERVICE));
        module.createCallChannel(promise);
        verify(promise).resolve(true);
    }

    @Test
    public void stopUdpAudio_resolves_whenNeverStarted() throws Exception {
        module.stopUdpAudio(promise);
        verify(promise).resolve(true);
    }
}
