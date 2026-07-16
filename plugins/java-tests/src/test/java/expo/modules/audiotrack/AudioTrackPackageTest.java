package expo.modules.audiotrack;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
public class AudioTrackPackageTest {

    @Test
    public void createNativeModules_returnsAudioTrackModule() {
        AudioTrackPackage pkg = new AudioTrackPackage();
        ReactApplicationContext context = mock(ReactApplicationContext.class);
        List<NativeModule> modules = pkg.createNativeModules(context);
        assertEquals(1, modules.size());
        assertTrue(modules.get(0) instanceof AudioTrackModule);
    }
}
