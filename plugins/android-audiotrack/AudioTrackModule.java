package expo.modules.audiotrack;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import expo.modules.yggstack.YggdrasilManager;

public class AudioTrackModule extends ReactContextBaseJavaModule {

  private static final String TAG = "AudioTrackModule";
  private static final int OPUS_APPLICATION_VOIP = 2048;

  private static final int UDP_RECV_PORT = 7778;
  private static final int UDP_SEND_PORT = 9778;

  private AudioTrack audioTrack;
  private int sampleRate;

  private long opusEncoder;
  private long opusDecoder;
  private byte[] encodeBuf;
  private byte[] decodeBuf;

  private DatagramSocket recvSocket;
  private DatagramSocket sendSocket;
  private InetSocketAddress sendAddr;
  private Thread recvThread;
  private volatile boolean udpRunning;
  private String remoteYggIp;
  private int remotePort;

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
  public void initUdpAudio(int localRecvPort, String remoteYggIp, int remotePort, Promise promise) {
    try {
      release();
      releaseCodec();
      this.sampleRate = 24000;
      this.remoteYggIp = remoteYggIp;
      this.remotePort = remotePort;

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
      encodeBuf = new byte[4000];
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

      Log.d(TAG, "UDP audio initialized: recv=" + recvPort + " send=127.0.0.1:" + sendPort
            + " -> " + remoteSendAddr);
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "initUdpAudio error", e);
      promise.reject("UDP_INIT_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void encodeAndSendUdp(ReadableArray pcmBytes) {
    try {
      if (opusEncoder == 0) return;
      byte[] pcm = toByteArray(pcmBytes);
      int frameSize = pcm.length / 2;
      if (frameSize < 60) return;
      int len = OpusBridge.nativeEncoderEncode(opusEncoder, pcm, frameSize, encodeBuf);
      if (len < 0) return;
      DatagramPacket pkt = new DatagramPacket(encodeBuf, len, sendAddr);
      sendSocket.send(pkt);
    } catch (Exception e) {
      Log.w(TAG, "encodeAndSendUdp error", e);
    }
  }

  @ReactMethod
  public void stopUdpAudio(Promise promise) {
    try {
      udpRunning = false;

      if (recvSocket != null) {
        recvSocket.close();
        recvSocket = null;
      }
      if (sendSocket != null) {
        sendSocket.close();
        sendSocket = null;
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

      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "stopUdpAudio error", e);
      promise.reject("STOP_UDP_ERROR", e.getMessage(), e);
    }
  }

  private void udpReceiveLoop() {
    byte[] buf = new byte[4000];
    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
    while (udpRunning) {
      try {
        recvSocket.receive(pkt);
        int pcmLen = OpusBridge.nativeDecoderDecode(
          opusDecoder, pkt.getData(), pkt.getLength(), decodeBuf);
        if (pcmLen > 0) {
          audioTrack.write(decodeBuf, 0, pcmLen);
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

  private byte[] toByteArray(ReadableArray arr) {
    int size = arr.size();
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; i++) {
      bytes[i] = (byte) arr.getInt(i);
    }
    return bytes;
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
