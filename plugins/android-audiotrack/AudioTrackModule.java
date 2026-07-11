package expo.modules.audiotrack;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class AudioTrackModule extends ReactContextBaseJavaModule {

  private static final String TAG = "AudioTrackModule";
  private AudioTrack audioTrack;
  private int sampleRate;

  AudioTrackModule(ReactApplicationContext context) {
    super(context);
  }

  @Override
  public String getName() {
    return "AudioTrackModule";
  }

  @ReactMethod
  public void init(int sampleRate, Promise promise) {
    try {
      release();
      this.sampleRate = sampleRate;
      int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
      int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
      int bufferSize = Math.max(
        AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat),
        4096
      );
      audioTrack = new AudioTrack(
        AudioManager.STREAM_VOICE_CALL,
        sampleRate,
        channelConfig,
        audioFormat,
        bufferSize,
        AudioTrack.MODE_STREAM
      );
      audioTrack.play();
      Log.d(TAG, "AudioTrack initialized: " + sampleRate + "Hz, buffer=" + bufferSize);
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "init error", e);
      promise.reject("AUDIOTRACK_INIT_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void write(String base64PCM, Promise promise) {
    try {
      if (audioTrack == null) {
        promise.reject("AUDIOTRACK_NOT_INITIALIZED", "Call init() before write()");
        return;
      }
      byte[] pcmData = Base64.decode(base64PCM, Base64.DEFAULT);
      int written = audioTrack.write(pcmData, 0, pcmData.length);
      promise.resolve(written);
    } catch (Exception e) {
      Log.e(TAG, "write error", e);
      promise.reject("AUDIOTRACK_WRITE_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void stop(Promise promise) {
    try {
      release();
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "stop error", e);
      promise.reject("AUDIOTRACK_STOP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void flush(Promise promise) {
    try {
      if (audioTrack != null) {
        audioTrack.flush();
      }
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "flush error", e);
      promise.reject("AUDIOTRACK_FLUSH_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void addListener(String eventName) {}

  @ReactMethod
  public void removeListeners(int count) {}

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
