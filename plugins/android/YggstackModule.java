package expo.modules.yggstack;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class YggstackModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

  private static final String TAG = "YggstackModule";
  private YggdrasilManager.LogListener logListener;
  private Runnable statusChangeListener;

  YggstackModule(ReactApplicationContext context) {
    super(context);
    context.addLifecycleEventListener(this);
  }

  @Override
  public String getName() {
    return "YggstackModule";
  }

  @ReactMethod
  public void generateConfig(Promise promise) {
    try {
      promise.resolve(YggdrasilManager.generateConfig());
    } catch (Exception e) {
      promise.reject("YGGSTACK_GEN_CONFIG_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void start(String configJSON, String socksAddress, String nameserver, Promise promise) {
    try {
      if (YggdrasilManager.isRunning()) {
        promise.resolve(true);
        return;
      }
      YggdrasilService.startYggdrasil(
        getReactApplicationContext(), configJSON, socksAddress, nameserver);
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "start error: " + e.getMessage());
      promise.reject("YGGSTACK_START_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void stop(Promise promise) {
    try {
      YggdrasilService.stopYggdrasil(getReactApplicationContext());
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "stop error", e);
      promise.reject("YGGSTACK_STOP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void isRunning(Promise promise) {
    promise.resolve(YggdrasilManager.isRunning());
  }

  @ReactMethod
  public void addPeer(String uri, Promise promise) {
    try {
      YggdrasilManager.addPeer(uri);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_ADD_PEER_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void removePeer(String uri, Promise promise) {
    try {
      YggdrasilManager.removePeer(uri);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOVE_PEER_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void retryPeersNow(Promise promise) {
    try {
      YggdrasilManager.retryPeersNow();
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_RETRY_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getPeers(Promise promise) {
    try {
      promise.resolve(YggdrasilManager.getPeers());
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_PEERS_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getAddress(Promise promise) {
    try {
      promise.resolve(YggdrasilManager.getAddress());
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_ADDRESS_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getPublicKey(Promise promise) {
    try {
      promise.resolve(YggdrasilManager.getPublicKey());
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_PUBKEY_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getPeersJSON(Promise promise) {
    try {
      promise.resolve(YggdrasilManager.getPeersJSON());
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_PEERS_JSON_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void addRemoteTCPMapping(int remotePort, String localAddr, Promise promise) {
    try {
      YggdrasilManager.addRemoteTCPMapping(remotePort, localAddr);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOTE_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void removeRemoteTCPMapping(int remotePort, String localAddr, Promise promise) {
    try {
      YggdrasilManager.removeRemoteTCPMapping(remotePort, localAddr);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOVE_REMOTE_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void clearRemoteMappings(Promise promise) {
    try {
      YggdrasilManager.clearRemoteMappings();
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_CLEAR_REMOTE_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void addRemoteUDPMapping(int remotePort, String localAddr, Promise promise) {
    try {
      YggdrasilManager.addRemoteUDPMapping(remotePort, localAddr);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOTE_UDP_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void removeRemoteUDPMapping(int remotePort, String localAddr, Promise promise) {
    try {
      YggdrasilManager.removeRemoteUDPMapping(remotePort, localAddr);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOVE_REMOTE_UDP_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void addLocalUDPMapping(String localAddr, String remoteAddr, Promise promise) {
    try {
      YggdrasilManager.addLocalUDPMapping(localAddr, remoteAddr);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_LOCAL_UDP_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void removeLocalUDPMapping(String localAddr, String remoteAddr, Promise promise) {
    try {
      YggdrasilManager.removeLocalUDPMapping(localAddr, remoteAddr);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOVE_LOCAL_UDP_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void addListener(String eventName) {}

  @ReactMethod
  public void removeListeners(int count) {}

  // ---- Event helpers ----

  private void sendEvent(String eventName, Object data) {
    if (getReactApplicationContext().hasActiveReactInstance()) {
      getReactApplicationContext()
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, data);
    }
  }

  private void sendStatusEvent() {
    WritableMap m = Arguments.createMap();
    m.putBoolean("running", YggdrasilManager.isRunning());
    try {
      String addr = YggdrasilManager.getAddress();
      m.putString("address", addr != null ? addr : "");
    } catch (Exception e) {
      m.putString("address", "");
    }
    try {
      String pk = YggdrasilManager.getPublicKey();
      m.putString("publicKey", pk != null ? pk : "");
    } catch (Exception e) {
      m.putString("publicKey", "");
    }
    try {
      String peersJson = YggdrasilManager.getPeersJSON();
      m.putString("peersJSON", peersJson != null ? peersJson : "[]");
    } catch (Exception e) {
      m.putString("peersJSON", "[]");
    }
    sendEvent("onYggstackStatus", m);
  }

  // ---- LifecycleEventListener ----

  @Override
  public void onHostResume() {
    if (logListener == null) {
      logListener = msg -> sendEvent("onYggstackLog", msg);
      YggdrasilManager.addLogListener(logListener);
    }
    if (statusChangeListener == null) {
      statusChangeListener = this::sendStatusEvent;
      YggdrasilManager.addStatusChangeListener(statusChangeListener);
    }
    if (YggdrasilManager.isRunning()) {
      sendStatusEvent();
    }
  }

  @Override
  public void onHostPause() {}

  @Override
  public void onHostDestroy() {
    if (logListener != null) {
      YggdrasilManager.removeLogListener(logListener);
      logListener = null;
    }
    if (statusChangeListener != null) {
      YggdrasilManager.removeStatusChangeListener(statusChangeListener);
      statusChangeListener = null;
    }
  }

}
