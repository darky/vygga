package expo.modules.cpulock;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class CpuLockModule extends ReactContextBaseJavaModule {

  private static final String TAG = "CpuLockModule";
  private WakeLock wakeLock;

  CpuLockModule(ReactApplicationContext context) {
    super(context);
  }

  @Override
  public String getName() {
    return "CpuLockModule";
  }

  @ReactMethod
  public void acquire(String tag, Promise promise) {
    try {
      if (wakeLock != null && wakeLock.isHeld()) {
        promise.resolve(true);
        return;
      }
      PowerManager pm = (PowerManager) getReactApplicationContext()
        .getApplicationContext()
        .getSystemService(Context.POWER_SERVICE);
      if (pm == null) {
        promise.reject("CPULOCK_NO_POWER", "PowerManager not available");
        return;
      }
      wakeLock = pm.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        tag != null ? tag : "Vygga::CpuLock"
      );
      wakeLock.acquire();
      Log.d(TAG, "CpuLock acquired: " + tag);
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "acquire error", e);
      promise.reject("CPULOCK_ACQUIRE_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void release(Promise promise) {
    try {
      if (wakeLock != null && wakeLock.isHeld()) {
        wakeLock.release();
        wakeLock = null;
        Log.d(TAG, "CpuLock released");
      }
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "release error", e);
      promise.reject("CPULOCK_RELEASE_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void isHeld(Promise promise) {
    promise.resolve(wakeLock != null && wakeLock.isHeld());
  }
}
