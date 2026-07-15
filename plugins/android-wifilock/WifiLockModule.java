package expo.modules.wifilock;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Handler;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class WifiLockModule extends ReactContextBaseJavaModule {

  private static final String TAG = "WifiLockModule";
  private WifiLock wifiLock;
  private ConnectivityManager.NetworkCallback mobileNetworkCallback;

  WifiLockModule(ReactApplicationContext context) {
    super(context);
  }

  @Override
  public String getName() {
    return "WifiLockModule";
  }

  @ReactMethod
  public void acquire(String tag, Promise promise) {
    try {
      if (wifiLock != null && wifiLock.isHeld()) {
        promise.resolve(true);
        return;
      }
      WifiManager wifiManager = (WifiManager) getReactApplicationContext()
        .getApplicationContext()
        .getSystemService(Context.WIFI_SERVICE);
      if (wifiManager == null) {
        promise.reject("WIFILOCK_NO_WIFI", "WifiManager not available");
        return;
      }
      wifiLock = wifiManager.createWifiLock(
        WifiManager.WIFI_MODE_FULL_HIGH_PERF,
        tag != null ? tag : "Vygga::WifiLock"
      );
      wifiLock.acquire();
      Log.d(TAG, "WifiLock acquired: " + tag);
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "acquire error", e);
      promise.reject("WIFILOCK_ACQUIRE_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void release(Promise promise) {
    try {
      if (wifiLock != null && wifiLock.isHeld()) {
        wifiLock.release();
        wifiLock = null;
        Log.d(TAG, "WifiLock released");
      }
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "release error", e);
      promise.reject("WIFILOCK_RELEASE_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void isHeld(Promise promise) {
    promise.resolve(wifiLock != null && wifiLock.isHeld());
  }

  @ReactMethod
  public void requestMobileNetwork(Promise promise) {
    try {
      if (mobileNetworkCallback != null) {
        promise.resolve(true);
        return;
      }
      ConnectivityManager cm = (ConnectivityManager) getReactApplicationContext()
        .getSystemService(Context.CONNECTIVITY_SERVICE);
      if (cm == null) {
        promise.reject("MOBILENET_NO_CONNECTIVITY", "ConnectivityManager not available");
        return;
      }
      NetworkRequest request = new NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build();
      mobileNetworkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
          Log.d(TAG, "Mobile network available: " + network);
        }
      };
      cm.requestNetwork(request, mobileNetworkCallback);
      Log.d(TAG, "Mobile network requested");
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "mobile network request error", e);
      promise.reject("MOBILENET_REQUEST_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void releaseMobileNetwork(Promise promise) {
    try {
      if (mobileNetworkCallback != null) {
        ConnectivityManager cm = (ConnectivityManager) getReactApplicationContext()
          .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
          cm.unregisterNetworkCallback(mobileNetworkCallback);
        }
        mobileNetworkCallback = null;
        Log.d(TAG, "Mobile network released");
      }
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "mobile network release error", e);
      promise.reject("MOBILENET_RELEASE_ERROR", e.getMessage(), e);
    }
  }
}
