package expo.modules.audiotrack;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import expo.modules.yggstack.YggdrasilManager;

public class AudioTrackModule extends ReactContextBaseJavaModule {

  private static final String TAG = "AudioTrackModule";
  private static final int OPUS_APPLICATION_VOIP = 2048;

  private static final int UDP_RECV_PORT = 7778;
  private static final int UDP_SEND_PORT = 9778;
  private static final int CAPTURE_BUF_SIZE = 960;

  private AudioTrack audioTrack;
  private AudioRecord audioCapture;
  private int sampleRate;

  private long opusEncoder;
  private long opusDecoder;
  private byte[] encodeBuf;
  private byte[] decodeBuf;

  private DatagramSocket recvSocket;
  private DatagramSocket sendSocket;
  private InetSocketAddress sendAddr;
  private Thread recvThread;
  private Thread captureThread;
  private volatile boolean udpRunning;
  private String remoteYggIp;
  private int remotePort;
  private String sessionTokenHex;
  private byte[] sessionTokenBytes;

  AudioTrackModule(ReactApplicationContext context) {
    super(context);
  }

  @ReactMethod
  public void createCallChannel(Promise promise) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel channel = new NotificationChannel(
          "ringtone_calls",
          "Calls",
          NotificationManager.IMPORTANCE_HIGH
        );
        channel.setVibrationPattern(new long[]{0, 100, 100, 100});
        AudioAttributes attrs = new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build();
        Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        channel.setSound(ringtoneUri, attrs);
        NotificationManager mgr = (NotificationManager) getReactApplicationContext()
          .getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null) mgr.createNotificationChannel(channel);
      }
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "createCallChannel error", e);
      promise.reject("CREATE_CALL_CHANNEL_ERROR", e.getMessage(), e);
    }
  }

  @Override
  public String getName() {
    return "AudioTrackModule";
  }

  @ReactMethod
  public void addListener(String eventName) {}

  @ReactMethod
  public void removeListeners(int count) {}

  // ---- UDP Audio ----

  @ReactMethod
  public void initUdpAudio(int localRecvPort, String remoteYggIp, int remotePort, String sessionToken, Promise promise) {
    try {
      release();
      releaseCodec();
      this.sampleRate = 24000;
      this.remoteYggIp = remoteYggIp;
      this.remotePort = remotePort;
      this.sessionTokenHex = sessionToken != null ? sessionToken : "";
      this.sessionTokenBytes = this.sessionTokenHex.getBytes(StandardCharsets.UTF_8);

      int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
      int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
      int bufferSize = Math.max(
        AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat), 4096);
      audioTrack = new AudioTrack(
        AudioManager.STREAM_VOICE_CALL, sampleRate, channelConfig, audioFormat,
        bufferSize, AudioTrack.MODE_STREAM);
      audioTrack.play();

      opusEncoder = OpusBridge.nativeEncoderCreate(sampleRate, 1, OPUS_APPLICATION_VOIP);
      if (opusEncoder == 0) {
        promise.reject("OPUS_ENCODER_CREATE_ERROR", "Failed to create Opus encoder");
        return;
      }
      opusDecoder = OpusBridge.nativeDecoderCreate(sampleRate, 1);
      if (opusDecoder == 0) {
        OpusBridge.nativeEncoderDestroy(opusEncoder);
        opusEncoder = 0;
        promise.reject("OPUS_DECODER_CREATE_ERROR", "Failed to create Opus decoder");
        return;
      }
      encodeBuf = new byte[4000 + sessionTokenBytes.length];
      decodeBuf = new byte[sampleRate * 2 * 120 / 1000];

      int recvPort = localRecvPort > 0 ? localRecvPort : UDP_RECV_PORT;

      recvSocket = new DatagramSocket(new InetSocketAddress("127.0.0.1", recvPort));
      YggdrasilManager.addRemoteUDPMapping(recvPort, "127.0.0.1:" + recvPort);

      int sendPort = recvPort == UDP_RECV_PORT ? UDP_SEND_PORT : recvPort + 2000;
      sendAddr = new InetSocketAddress("127.0.0.1", sendPort);
      sendSocket = new DatagramSocket();

      String localSendAddr = "127.0.0.1:" + sendPort;
      String remoteSendAddr = "[" + remoteYggIp + "]:" + remotePort;
      YggdrasilManager.addLocalUDPMapping(localSendAddr, remoteSendAddr);

      udpRunning = true;
      recvThread = new Thread(this::udpReceiveLoop);
      recvThread.start();
      startCapture();

      Log.d(TAG, "UDP audio initialized: recv=" + recvPort + " send=127.0.0.1:" + sendPort
            + " -> " + remoteSendAddr + " token=" + sessionTokenHex);
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "initUdpAudio error", e);
      promise.reject("UDP_INIT_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void stopUdpAudio(Promise promise) {
    try {
      udpRunning = false;
      stopCapture();

      if (recvSocket != null) {
        recvSocket.close();
        recvSocket = null;
      }
      if (sendSocket != null) {
        sendSocket.close();
        sendSocket = null;
      }

      // Wait for capture and receive threads to fully exit before destroying
      // codec handles to avoid use-after-free in native encoder/decoder.
      if (captureThread != null) {
        try { captureThread.join(500); } catch (InterruptedException ignored) {}
        captureThread = null;
      }
      if (recvThread != null) {
        try { recvThread.join(500); } catch (InterruptedException ignored) {}
        recvThread = null;
      }

      String localSend = "127.0.0.1:" + UDP_SEND_PORT;
      String remoteSend = "[" + remoteYggIp + "]:" + remotePort;
      try {
        YggdrasilManager.removeLocalUDPMapping(localSend, remoteSend);
      } catch (Exception e) {
        Log.w(TAG, "removeLocalUDPMapping error", e);
      }
      try {
        YggdrasilManager.removeRemoteUDPMapping(UDP_RECV_PORT, "127.0.0.1:" + UDP_RECV_PORT);
      } catch (Exception e) {
        Log.w(TAG, "removeRemoteUDPMapping error", e);
      }

      releaseCodec();
      release();

      sessionTokenHex = null;
      sessionTokenBytes = null;

      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "stopUdpAudio error", e);
      promise.reject("STOP_UDP_ERROR", e.getMessage(), e);
    }
  }

  private void udpReceiveLoop() {
    int tokenLen = sessionTokenBytes != null ? sessionTokenBytes.length : 0;
    byte[] buf = new byte[4000];
    byte[] opusBuf = new byte[4000];
    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
    while (udpRunning) {
      try {
        recvSocket.receive(pkt);
        int len = pkt.getLength();

        if (tokenLen > 0 && len <= tokenLen) {
          Log.w(TAG, "dropping short packet: " + len + " <= token len " + tokenLen);
          continue;
        }

        if (tokenLen > 0) {
          byte[] pktToken = Arrays.copyOfRange(buf, 0, tokenLen);
          if (!Arrays.equals(pktToken, sessionTokenBytes)) {
            continue;
          }
          System.arraycopy(buf, tokenLen, opusBuf, 0, len - tokenLen);
          int pcmLen = OpusBridge.nativeDecoderDecode(
            opusDecoder, opusBuf, len - tokenLen, decodeBuf);
          if (pcmLen > 0) {
            audioTrack.write(decodeBuf, 0, pcmLen);
          }
        } else {
          int pcmLen = OpusBridge.nativeDecoderDecode(
            opusDecoder, buf, len, decodeBuf);
          if (pcmLen > 0) {
            audioTrack.write(decodeBuf, 0, pcmLen);
          }
        }
      } catch (java.net.SocketException e) {
        break;
      } catch (Exception e) {
        if (udpRunning) {
          Log.w(TAG, "UDP receive error", e);
        }
      }
    }
  }

  private void startCapture() {
    if (audioCapture != null) return;
    int minBufSize = AudioRecord.getMinBufferSize(
      sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    int bufSize = Math.max(CAPTURE_BUF_SIZE, minBufSize);
    audioCapture = new AudioRecord(
      MediaRecorder.AudioSource.MIC, sampleRate,
      AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
    if (audioCapture.getState() != AudioRecord.STATE_INITIALIZED) {
      Log.e(TAG, "AudioRecord init failed");
      audioCapture.release();
      audioCapture = null;
      return;
    }
    audioCapture.startRecording();
    captureThread = new Thread(this::udpCaptureLoop);
    captureThread.start();
  }

  private void stopCapture() {
    if (audioCapture != null) {
      try { audioCapture.stop(); } catch (Exception e) { Log.w(TAG, "stopCapture stop", e); }
      try { audioCapture.release(); } catch (Exception e) { Log.w(TAG, "stopCapture release", e); }
      audioCapture = null;
    }
  }

  private void udpCaptureLoop() {
    int tokenLen = sessionTokenBytes != null ? sessionTokenBytes.length : 0;
    byte[] pcmBuf = new byte[CAPTURE_BUF_SIZE];
    byte[] encBuf = new byte[4000];
    byte[] sendBuf = new byte[4000 + tokenLen];
    while (udpRunning) {
      int bytesRead = audioCapture.read(pcmBuf, 0, pcmBuf.length);
      if (bytesRead <= 0) break;
      int frameSize = bytesRead / 2;
      if (frameSize < 60) continue;

      int opusLen = OpusBridge.nativeEncoderEncode(opusEncoder, pcmBuf, frameSize, encBuf);
      if (opusLen <= 0) continue;

      if (tokenLen > 0) {
        System.arraycopy(sessionTokenBytes, 0, sendBuf, 0, tokenLen);
        System.arraycopy(encBuf, 0, sendBuf, tokenLen, opusLen);
      } else {
        System.arraycopy(encBuf, 0, sendBuf, 0, opusLen);
      }

      try {
        int totalLen = tokenLen + opusLen;
        DatagramPacket pkt = new DatagramPacket(sendBuf, totalLen, sendAddr);
        sendSocket.send(pkt);
      } catch (Exception e) {
        if (udpRunning) Log.w(TAG, "capture send error", e);
      }
    }
  }

  private void releaseCodec() {
    if (opusEncoder != 0) {
      OpusBridge.nativeEncoderDestroy(opusEncoder);
      opusEncoder = 0;
    }
    if (opusDecoder != 0) {
      OpusBridge.nativeDecoderDestroy(opusDecoder);
      opusDecoder = 0;
    }
    encodeBuf = null;
    decodeBuf = null;
  }

  private void release() {
    if (audioTrack != null) {
      try {
        if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
          audioTrack.stop();
        }
        audioTrack.release();
      } catch (Exception e) {
        Log.w(TAG, "release error", e);
      }
      audioTrack = null;
    }
  }
}
