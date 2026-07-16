package expo.modules.yggstack;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class YggstackModuleTest {

    @Mock private ReactApplicationContext mockContext;
    @Mock private Promise promise;
    @Captor private ArgumentCaptor<Boolean> boolCaptor;
    @Captor private ArgumentCaptor<String> stringCaptor;

    private YggstackModule module;
    private MockedStatic<YggdrasilManager> mockedManager;
    private MockedStatic<YggdrasilService> mockedService;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockedManager = Mockito.mockStatic(YggdrasilManager.class);
        mockedService = Mockito.mockStatic(YggdrasilService.class);
        module = new YggstackModule(mockContext);
    }

    @After
    public void tearDown() {
        mockedManager.close();
        mockedService.close();
    }

    @Test
    public void getName_returnsCorrectName() {
        assertEquals("YggstackModule", module.getName());
    }

    @Test
    public void isRunning_delegatesToManager() throws Exception {
        mockedManager.when(YggdrasilManager::isRunning).thenReturn(true);
        module.isRunning(promise);
        verify(promise).resolve(true);
    }

    @Test
    public void isRunning_returnsFalse_whenManagerReturnsFalse() throws Exception {
        mockedManager.when(YggdrasilManager::isRunning).thenReturn(false);
        module.isRunning(promise);
        verify(promise).resolve(false);
    }

    @Test
    public void generateConfig_returnsConfig() throws Exception {
        mockedManager.when(YggdrasilManager::generateConfig).thenReturn("{\"key\": \"val\"}");
        module.generateConfig(promise);
        verify(promise).resolve("{\"key\": \"val\"}");
    }

    @Test
    public void generateConfig_rejects_onException() throws Exception {
        mockedManager.when(YggdrasilManager::generateConfig).thenThrow(new RuntimeException("fail"));
        module.generateConfig(promise);
        verify(promise).reject(eq("YGGSTACK_GEN_CONFIG_ERROR"), anyString(), Mockito.any(Throwable.class));
    }

    @Test
    public void addPeer_delegatesToManager() throws Exception {
        module.addPeer("tls://peer:443", promise);
        mockedManager.verify(() -> YggdrasilManager.addPeer("tls://peer:443"));
        verify(promise).resolve(true);
    }

    @Test
    public void addPeer_rejects_onException() throws Exception {
        mockedManager.when(() -> YggdrasilManager.addPeer(anyString()))
            .thenThrow(new RuntimeException("fail"));
        module.addPeer("tls://peer:443", promise);
        verify(promise).reject(eq("YGGSTACK_ADD_PEER_ERROR"), anyString(), Mockito.any(Throwable.class));
    }

    @Test
    public void removePeer_delegatesToManager() throws Exception {
        module.removePeer("tls://peer:443", promise);
        mockedManager.verify(() -> YggdrasilManager.removePeer("tls://peer:443"));
        verify(promise).resolve(true);
    }

    @Test
    public void retryPeersNow_delegatesToManager() throws Exception {
        module.retryPeersNow(promise);
        mockedManager.verify(YggdrasilManager::retryPeersNow);
        verify(promise).resolve(true);
    }

    @Test
    public void getPeers_delegatesToManager() throws Exception {
        mockedManager.when(YggdrasilManager::getPeers).thenReturn("[{\"key\": \"val\"}]");
        module.getPeers(promise);
        verify(promise).resolve("[{\"key\": \"val\"}]");
    }

    @Test
    public void getAddress_delegatesToManager() throws Exception {
        mockedManager.when(YggdrasilManager::getAddress).thenReturn("200:...");
        module.getAddress(promise);
        verify(promise).resolve("200:...");
    }

    @Test
    public void getPublicKey_delegatesToManager() throws Exception {
        mockedManager.when(YggdrasilManager::getPublicKey).thenReturn("pubkey");
        module.getPublicKey(promise);
        verify(promise).resolve("pubkey");
    }

    @Test
    public void getPeersJSON_delegatesToManager() throws Exception {
        mockedManager.when(YggdrasilManager::getPeersJSON).thenReturn("[]");
        module.getPeersJSON(promise);
        verify(promise).resolve("[]");
    }

    @Test
    public void addRemoteTCPMapping_delegatesToManager() throws Exception {
        module.addRemoteTCPMapping(8080, "127.0.0.1:8080", promise);
        mockedManager.verify(() -> YggdrasilManager.addRemoteTCPMapping(8080, "127.0.0.1:8080"));
        verify(promise).resolve(true);
    }

    @Test
    public void removeRemoteTCPMapping_delegatesToManager() throws Exception {
        module.removeRemoteTCPMapping(8080, "127.0.0.1:8080", promise);
        mockedManager.verify(() -> YggdrasilManager.removeRemoteTCPMapping(8080, "127.0.0.1:8080"));
        verify(promise).resolve(true);
    }

    @Test
    public void clearRemoteMappings_delegatesToManager() throws Exception {
        module.clearRemoteMappings(promise);
        mockedManager.verify(YggdrasilManager::clearRemoteMappings);
        verify(promise).resolve(true);
    }

    @Test
    public void addRemoteUDPMapping_delegatesToManager() throws Exception {
        module.addRemoteUDPMapping(7778, "127.0.0.1:7778", promise);
        mockedManager.verify(() -> YggdrasilManager.addRemoteUDPMapping(7778, "127.0.0.1:7778"));
        verify(promise).resolve(true);
    }

    @Test
    public void removeRemoteUDPMapping_delegatesToManager() throws Exception {
        module.removeRemoteUDPMapping(7778, "127.0.0.1:7778", promise);
        mockedManager.verify(() -> YggdrasilManager.removeRemoteUDPMapping(7778, "127.0.0.1:7778"));
        verify(promise).resolve(true);
    }

    @Test
    public void addLocalUDPMapping_delegatesToManager() throws Exception {
        module.addLocalUDPMapping("127.0.0.1:7778", "[200:...]:9778", promise);
        mockedManager.verify(() -> YggdrasilManager.addLocalUDPMapping("127.0.0.1:7778", "[200:...]:9778"));
        verify(promise).resolve(true);
    }

    @Test
    public void removeLocalUDPMapping_delegatesToManager() throws Exception {
        module.removeLocalUDPMapping("127.0.0.1:7778", "[200:...]:9778", promise);
        mockedManager.verify(() -> YggdrasilManager.removeLocalUDPMapping("127.0.0.1:7778", "[200:...]:9778"));
        verify(promise).resolve(true);
    }

    @Test
    public void addListener_doesNothing() {
        module.addListener("onYggstackStatus");
    }

    @Test
    public void removeListeners_doesNothing() {
        module.removeListeners(0);
    }

    @Test
    public void start_rejects_whenServiceThrows() throws Exception {
        mockedService.when(() -> YggdrasilService.startYggdrasil(any(), anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("fail"));
        module.start("{}", "", "", promise);
        verify(promise).reject(eq("YGGSTACK_START_ERROR"), anyString(), Mockito.any(Throwable.class));
    }

    @Test
    public void stop_rejects_whenServiceThrows() throws Exception {
        mockedService.when(() -> YggdrasilService.stopYggdrasil(any()))
            .thenThrow(new RuntimeException("fail"));
        module.stop(promise);
        verify(promise).reject(eq("YGGSTACK_STOP_ERROR"), anyString(), Mockito.any(Throwable.class));
    }
}
