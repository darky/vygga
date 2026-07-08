package expo.modules.yggstack;

import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class YggstackModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

  private static final String TAG = "YggstackModule";
  private YggdrasilService.MessageListener messageListener;
  private YggdrasilService.LogListener logListener;
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
      promise.resolve(YggdrasilService.generateConfig());
    } catch (Exception e) {
      promise.reject("YGGSTACK_GEN_CONFIG_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void start(String configJSON, String socksAddress, String nameserver, Promise promise) {
    try {
      if (YggdrasilService.isRunning()) {
        promise.resolve(true);
        return;
      }
      YggdrasilService.startYggdrasil(
        getReactApplicationContext(), configJSON, socksAddress, nameserver);
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "start error", e);
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
    promise.resolve(YggdrasilService.isRunning());
  }

  @ReactMethod
  public void addPeer(String uri, Promise promise) {
    try {
      YggdrasilService.addPeer(uri);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_ADD_PEER_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void removePeer(String uri, Promise promise) {
    try {
      YggdrasilService.removePeer(uri);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOVE_PEER_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getPeers(Promise promise) {
    try {
      promise.resolve(YggdrasilService.getPeers());
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_PEERS_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getAddress(Promise promise) {
    try {
      promise.resolve(YggdrasilService.getAddress());
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_ADDRESS_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getPublicKey(Promise promise) {
    try {
      promise.resolve(YggdrasilService.getPublicKey());
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_PUBKEY_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getPeersJSON(Promise promise) {
    try {
      promise.resolve(YggdrasilService.getPeersJSON());
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_PEERS_JSON_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void addRemoteTCPMapping(int remotePort, String localAddr, Promise promise) {
    try {
      YggdrasilService.addRemoteTCPMapping(remotePort, localAddr);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOTE_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void removeRemoteTCPMapping(int remotePort, String localAddr, Promise promise) {
    try {
      YggdrasilService.removeRemoteTCPMapping(remotePort, localAddr);
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOVE_REMOTE_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void clearRemoteMappings(Promise promise) {
    try {
      YggdrasilService.clearRemoteMappings();
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_CLEAR_REMOTE_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void startMessengerServer(int port, Promise promise) {
    try {
      YggdrasilService.startMessengerServer(port);
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "Messenger server error", e);
      promise.reject("START_SERVER_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void stopMessengerServer(Promise promise) {
    YggdrasilService.stopMessengerServer();
    promise.resolve(true);
  }

  @ReactMethod
  public void addListener(String eventName) {}

  @ReactMethod
  public void removeListeners(int count) {}

  @ReactMethod
  public void pollPendingMessages(Promise promise) {
    try {
      java.util.List<String> msgs = YggdrasilService.pollPendingMessages();
      com.facebook.react.bridge.WritableArray arr = Arguments.createArray();
      for (String msg : msgs) {
        arr.pushString(msg);
      }
      promise.resolve(arr);
    } catch (Exception e) {
      promise.resolve(Arguments.createArray());
    }
  }

  // ---- SOCKS5 Message Sending (stays in module, doesn't need service) ----

  @ReactMethod
  public void sendMessage(String targetAddr, String message, Promise promise) {
    new Thread(() -> {
      try {
        Socket socket = new Socket("127.0.0.1", 1080);
        socket.setSoTimeout(10000);
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        out.write(new byte[]{0x05, 0x01, 0x00});
        byte[] handshakeResp = new byte[2];
        readFully(in, handshakeResp);
        if (handshakeResp[0] != 0x05 || handshakeResp[1] != 0x00) {
          throw new Exception("SOCKS5 handshake failed");
        }

        int port = 7777;
        String ip = targetAddr.replace("[", "").replace("]", "");
        InetAddress addr = InetAddress.getByName(ip);
        byte[] addrBytes = addr.getAddress();

        byte[] connectReq = new byte[addrBytes.length + 6];
        connectReq[0] = 0x05;
        connectReq[1] = 0x01;
        connectReq[2] = 0x00;
        connectReq[3] = addrBytes.length == 16 ? (byte) 0x04 : (byte) 0x01;
        System.arraycopy(addrBytes, 0, connectReq, 4, addrBytes.length);
        connectReq[connectReq.length - 2] = (byte) ((port >> 8) & 0xFF);
        connectReq[connectReq.length - 1] = (byte) (port & 0xFF);
        out.write(connectReq);

        byte[] connectResp = new byte[10];
        readFully(in, connectResp);
        if (connectResp[0] != 0x05 || connectResp[1] != 0x00) {
          throw new Exception("SOCKS5 connect failed, status: " + connectResp[1]);
        }

        byte[] msgBytes = (message + (char) 10).getBytes("UTF-8");
        out.write(msgBytes);
        out.flush();
        socket.close();
        promise.resolve(true);
      } catch (Exception e) {
        promise.reject("SEND_MESSAGE_ERROR", e.getMessage(), e);
      }
    }).start();
  }

  private void readFully(InputStream in, byte[] buffer) throws Exception {
    int offset = 0;
    while (offset < buffer.length) {
      int read = in.read(buffer, offset, buffer.length - offset);
      if (read < 0) throw new Exception("Connection closed");
      offset += read;
    }
  }

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
    m.putBoolean("running", YggdrasilService.isRunning());
    try {
      String addr = YggdrasilService.getAddress();
      m.putString("address", addr != null ? addr : "");
    } catch (Exception e) {
      m.putString("address", "");
    }
    try {
      String pk = YggdrasilService.getPublicKey();
      m.putString("publicKey", pk != null ? pk : "");
    } catch (Exception e) {
      m.putString("publicKey", "");
    }
    try {
      String peersJson = YggdrasilService.getPeersJSON();
      m.putString("peersJSON", peersJson != null ? peersJson : "[]");
    } catch (Exception e) {
      m.putString("peersJSON", "[]");
    }
    sendEvent("onYggstackStatus", m);
  }

  // ---- LifecycleEventListener ----

  @Override
  public void onHostResume() {
    if (messageListener == null) {
      messageListener = this::onMessengerMessage;
      YggdrasilService.addMessageListener(messageListener);
    }
    if (logListener == null) {
      logListener = msg -> sendEvent("onYggstackLog", msg);
      YggdrasilService.addLogListener(logListener);
    }
    if (statusChangeListener == null) {
      statusChangeListener = this::sendStatusEvent;
      YggdrasilService.addStatusChangeListener(statusChangeListener);
    }
    // Send current status so JS catches up after re-launch
    if (YggdrasilService.isRunning()) {
      sendStatusEvent();
    }
  }

  @Override
  public void onHostPause() {}

  @Override
  public void onHostDestroy() {
    // Don't stop yggdrasil — the foreground service keeps it running
    if (messageListener != null) {
      YggdrasilService.removeMessageListener(messageListener);
      messageListener = null;
    }
    if (logListener != null) {
      YggdrasilService.removeLogListener(logListener);
      logListener = null;
    }
    if (statusChangeListener != null) {
      YggdrasilService.removeStatusChangeListener(statusChangeListener);
      statusChangeListener = null;
    }
  }

  private void onMessengerMessage(String payload) {
    sendEvent("onMessengerMessage", payload);
  }
}
