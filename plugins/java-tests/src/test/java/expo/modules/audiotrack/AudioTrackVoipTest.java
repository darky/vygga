package expo.modules.audiotrack;

import android.app.Application;

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
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAudioRecord;
import org.robolectric.shadows.ShadowAudioTrack;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import expo.modules.yggstack.YggdrasilManager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class AudioTrackVoipTest {

    @Mock private ReactApplicationContext mockContext;
    @Mock private Promise promise;

    private AudioTrackModule module;
    private Application app;
    private MockedStatic<YggdrasilManager> mockedYgg;

    private static final int SAMPLE_RATE = 24000;
    private static final int FRAME_SIZE = 480;           // 20ms @ 24kHz
    private static final int OPUS_APPLICATION_VOIP = 2048;
    private static int portCounter = 19876;

    private static synchronized int nextRecvPort() { return portCounter += 2; }
    private static int sendPort(int recvPort) { return recvPort + 1; }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockedYgg = Mockito.mockStatic(YggdrasilManager.class);
        app = RuntimeEnvironment.getApplication();
        when(mockContext.getApplicationContext()).thenReturn(app);
        when(mockContext.getSystemService(anyString())).thenAnswer(
            i -> app.getSystemService((String) i.getArgument(0)));
        module = new AudioTrackModule(mockContext);
    }

    @After
    public void tearDown() {
        if (mockedYgg != null) {
            mockedYgg.close();
        }
        ShadowAudioRecord.clearSource();
    }

    @Test
    public void initUdpAudio_createsEncoderAndDecoder() throws Exception {
        int rp = nextRecvPort();
        module.initUdpAudio(rp, "200::1", sendPort(rp), "token", promise);
        verify(promise).resolve(true);
        module.stopUdpAudio(mock(Promise.class));
    }

    @Test
    public void initUdpAudio_createsUdpMappings() throws Exception {
        int rp = nextRecvPort();
        int sp = sendPort(rp);
        // Module adds 2000 to sendPort when localRecvPort != UDP_RECV_PORT
        int expectedSendPort = rp + 2000;
        module.initUdpAudio(rp, "200::1", sp, null, promise);
        verify(promise).resolve(true);
        mockedYgg.verify(() -> YggdrasilManager.addRemoteUDPMapping(
            eq(rp), contains("127.0.0.1:" + rp)));
        mockedYgg.verify(() -> YggdrasilManager.addLocalUDPMapping(
            contains("127.0.0.1:" + expectedSendPort), contains("200::1]:" + sp)));
        module.stopUdpAudio(mock(Promise.class));
    }

    @Test
    public void initUdpAudio_usesDefaultPorts_whenPortZero() throws Exception {
        module.initUdpAudio(0, "200::1", 9778, "token", promise);
        verify(promise).resolve(true);
        module.stopUdpAudio(mock(Promise.class));
    }

    @Test
    public void stopUdpAudio_removesUdpMappings() throws Exception {
        // NOTE: stopUdpAudio currently uses the hardcoded UDP_RECV_PORT (7778) and
        // UDP_SEND_PORT (9778) constants rather than the dynamically assigned ports
        // from initUdpAudio. This test documents the current behavior — the cleanup
        // removes the default port mappings, which may leave custom port mappings dangling.
        int rp = nextRecvPort();
        module.initUdpAudio(rp, "200::1", sendPort(rp), "token", promise);
        verify(promise).resolve(true);

        Promise stopPromise = mock(Promise.class);
        module.stopUdpAudio(stopPromise);
        verify(stopPromise).resolve(true);

        mockedYgg.verify(() -> YggdrasilManager.removeRemoteUDPMapping(
            eq(7778), contains("127.0.0.1:7778")));
        mockedYgg.verify(() -> YggdrasilManager.removeLocalUDPMapping(
            contains("127.0.0.1:9778"), contains("200::1]:" + sendPort(rp))));
    }

    @Test
    public void stopUdpAudio_doesNotCrash_whenNeverStarted() throws Exception {
        module.stopUdpAudio(promise);
        verify(promise).resolve(true);
    }

    @Test
    public void stopUdpAudio_afterStartStopStart() throws Exception {
        int rp = nextRecvPort();
        module.initUdpAudio(rp, "200::1", sendPort(rp), "token", promise);
        verify(promise).resolve(true);
        module.stopUdpAudio(mock(Promise.class));

        int rp2 = nextRecvPort();
        module.initUdpAudio(rp2, "200::2", sendPort(rp2), "token2", promise);
        verify(promise, times(2)).resolve(true);
        module.stopUdpAudio(mock(Promise.class));
    }

    @Test
    public void udpReceive_decodesAndPlaysAudio() throws Exception {
        int rp = nextRecvPort();
        int sp = sendPort(rp);

        // Encode a real opus frame first
        long enc = OpusBridge.nativeEncoderCreate(SAMPLE_RATE, 1, OPUS_APPLICATION_VOIP);
        byte[] pcm = new byte[FRAME_SIZE * 2];
        for (int i = 0; i < pcm.length; i += 2) {
            short val = (short) (Math.sin(i / 40.0 * Math.PI * 2) * 8000);
            pcm[i] = (byte) (val & 0xFF);
            pcm[i + 1] = (byte) ((val >> 8) & 0xFF);
        }
        byte[] opusFrame = new byte[4000];
        int opusLen = OpusBridge.nativeEncoderEncode(enc, pcm, FRAME_SIZE, opusFrame);
        OpusBridge.nativeEncoderDestroy(enc);
        assertTrue(opusLen > 0);

        // Capture written audio data
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<byte[]> writtenData = new AtomicReference<>();
        ShadowAudioTrack.addAudioDataListener((track, data, format) -> {
            writtenData.set(data);
            latch.countDown();
        });

        module.initUdpAudio(rp, "200::1", sp, "token", promise);
        verify(promise).resolve(true);

        // Send the opus frame with token prefix via real loopback UDP
        String token = "token";
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[tokenBytes.length + opusLen];
        System.arraycopy(tokenBytes, 0, packet, 0, tokenBytes.length);
        System.arraycopy(opusFrame, 0, packet, tokenBytes.length, opusLen);

        try (DatagramSocket sender = new DatagramSocket()) {
            sender.send(new DatagramPacket(packet, packet.length,
                new InetSocketAddress("127.0.0.1", rp)));
        }

        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertTrue("AudioTrack should have received decoded PCM", received);
        assertNotNull("Written data should not be null", writtenData.get());

        module.stopUdpAudio(mock(Promise.class));
    }

    @Test
    public void udpReceive_ignoresShortPacket() throws Exception {
        int rp = nextRecvPort();
        module.initUdpAudio(rp, "200::1", sendPort(rp), "token", promise);
        verify(promise).resolve(true);

        try (DatagramSocket sender = new DatagramSocket()) {
            byte[] shortPkt = "ab".getBytes();
            sender.send(new DatagramPacket(shortPkt, shortPkt.length,
                new InetSocketAddress("127.0.0.1", rp)));
        }

        Thread.sleep(200);
        module.stopUdpAudio(mock(Promise.class));
    }

    @Test
    public void udpReceive_ignoresWrongToken() throws Exception {
        int rp = nextRecvPort();
        module.initUdpAudio(rp, "200::1", sendPort(rp), "correct_token", promise);
        verify(promise).resolve(true);

        String wrongToken = "wrong_token";
        byte[] wrongTokenBytes = wrongToken.getBytes(StandardCharsets.UTF_8);
        byte[] opusFrame = new byte[100];
        for (int i = 0; i < 100; i++) opusFrame[i] = (byte) i;

        byte[] packet = new byte[wrongTokenBytes.length + opusFrame.length];
        System.arraycopy(wrongTokenBytes, 0, packet, 0, wrongTokenBytes.length);
        System.arraycopy(opusFrame, 0, packet, wrongTokenBytes.length, opusFrame.length);

        try (DatagramSocket sender = new DatagramSocket()) {
            sender.send(new DatagramPacket(packet, packet.length,
                new InetSocketAddress("127.0.0.1", rp)));
        }

        Thread.sleep(200);
        module.stopUdpAudio(mock(Promise.class));
    }

    @Test
    public void udpReceive_worksWithoutToken() throws Exception {
        int rp = nextRecvPort();

        // Encode a short real opus frame
        long enc = OpusBridge.nativeEncoderCreate(SAMPLE_RATE, 1, OPUS_APPLICATION_VOIP);
        byte[] pcm = new byte[FRAME_SIZE * 2];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (byte) (i * 3);
        byte[] opusFrame = new byte[4000];
        int opusLen = OpusBridge.nativeEncoderEncode(enc, pcm, FRAME_SIZE, opusFrame);
        OpusBridge.nativeEncoderDestroy(enc);
        assertTrue(opusLen > 0);

        final CountDownLatch latch = new CountDownLatch(1);
        ShadowAudioTrack.addAudioDataListener((track, data, format) -> latch.countDown());

        module.initUdpAudio(rp, "200::1", sendPort(rp), null, promise);
        verify(promise).resolve(true);

        try (DatagramSocket sender = new DatagramSocket()) {
            sender.send(new DatagramPacket(opusFrame, opusLen,
                new InetSocketAddress("127.0.0.1", rp)));
        }

        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertTrue("Should decode and play packet without token", received);

        module.stopUdpAudio(mock(Promise.class));
    }

    @Test
    public void udpCapture_encodesAndSends() throws Exception {
        int rp = nextRecvPort();
        int sp = rp + 2000; // matches module's send port calculation

        // Track that the AudioRecord source was read
        final CountDownLatch sourceRead = new CountDownLatch(1);
        byte[] testPcm = new byte[FRAME_SIZE * 2];
        for (int i = 0; i < testPcm.length; i += 2) {
            short val = (short) (Math.sin(i / 40.0 * Math.PI * 2) * 8000);
            testPcm[i] = (byte) (val & 0xFF);
            testPcm[i + 1] = (byte) ((val >> 8) & 0xFF);
        }
        ShadowAudioRecord.setSource(new ShadowAudioRecord.AudioRecordSource() {
            @Override
            public int readInByteArray(byte[] audioData, int offsetInBytes, int sizeInBytes,
                                       boolean isBlocking) {
                sourceRead.countDown();
                int copyLen = Math.min(sizeInBytes, testPcm.length);
                System.arraycopy(testPcm, 0, audioData, 0, copyLen);
                return copyLen;
            }
        });

        // Listen on the module's send port
        final CountDownLatch pktReceived = new CountDownLatch(1);
        new Thread(() -> {
            try (DatagramSocket receiver = new DatagramSocket(sp)) {
                byte[] buf = new byte[4096];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                receiver.receive(pkt);
                if (pkt.getLength() > 0) pktReceived.countDown();
            } catch (Exception e) {
            }
        }).start();

        Thread.sleep(200);

        module.initUdpAudio(rp, "200::1", sp, "token", promise);
        verify(promise, timeout(5000)).resolve(true);

        boolean sourceWasRead = sourceRead.await(3, TimeUnit.SECONDS);
        assertTrue("AudioRecord source should have been read", sourceWasRead);

        boolean packetWasSent = pktReceived.await(3, TimeUnit.SECONDS);
        assertTrue("Capture loop should have sent a UDP packet", packetWasSent);

        module.stopUdpAudio(mock(Promise.class));
    }
}
