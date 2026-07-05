package expo.modules.yggstack;

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

import link.yggdrasil.yggstack.mobile.Mobile;
import link.yggdrasil.yggstack.mobile.Yggstack;
import link.yggdrasil.yggstack.mobile.LogCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
public class YggstackModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

  private static final String TAG = "YggstackModule";
  private Yggstack instance;
  private boolean running = false;
  private boolean foregroundServiceActive = false;
  private ServerSocket messengerServer;
  private Thread messengerThread;

  YggstackModule(ReactApplicationContext context) {
    super(context);
    context.addLifecycleEventListener(this);
  }

  @ReactMethod
  public void setForegroundServiceActive(boolean active, Promise promise) {
    foregroundServiceActive = active;
    promise.resolve(true);
  }

  @Override
  public String getName() {
    return "YggstackModule";
  }

  @ReactMethod
  public void generateConfig(Promise promise) {
    try {
      promise.resolve(Mobile.generateConfig());
    } catch (Exception e) {
      promise.reject("YGGSTACK_GEN_CONFIG_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void start(String configJSON, String socksAddress, String nameserver, Promise promise) {
    try {
      if (running) {
        promise.resolve(true);
        return;
      }
      instance = Mobile.newYggstack();
      instance.setLogLevel("info");
      instance.setLogCallback(new LogCallback() {
        @Override
        public void onLog(String message) {
          sendEvent("onYggstackLog", message);
        }
      });
      instance.loadConfigJSON(configJSON);
      instance.start(socksAddress, nameserver);
      running = true;
      sendEvent("onYggstackStatus", makeStatusMap());
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "start error", e);
      promise.reject("YGGSTACK_START_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void stop(Promise promise) {
    try {
      if (instance != null) {
        instance.stop();
      }
      running = false;
      sendEvent("onYggstackStatus", makeStatusMap());
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_STOP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void isRunning(Promise promise) {
    promise.resolve(running);
  }

  @ReactMethod
  public void addPeer(String uri, Promise promise) {
    try {
      if (instance != null) {
        instance.addPeer(uri);
      }
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_ADD_PEER_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void removePeer(String uri, Promise promise) {
    try {
      if (instance != null) {
        instance.removePeer(uri);
      }
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOVE_PEER_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getPeers(Promise promise) {
    try {
      if (instance != null) {
        promise.resolve(instance.getPeers());
      } else {
        promise.resolve("[]");
      }
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_PEERS_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getAddress(Promise promise) {
    try {
      if (instance != null) {
        promise.resolve(instance.getAddress());
      } else {
        promise.resolve("");
      }
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_ADDRESS_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getPublicKey(Promise promise) {
    try {
      if (instance != null) {
        promise.resolve(instance.getPublicKey());
      } else {
        promise.resolve("");
      }
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_PUBKEY_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void getPeersJSON(Promise promise) {
    try {
      if (instance != null) {
        promise.resolve(instance.getPeersJSON());
      } else {
        promise.resolve("[]");
      }
    } catch (Exception e) {
      promise.reject("YGGSTACK_GET_PEERS_JSON_ERROR", e.getMessage(), e);
    }
  }

  private WritableMap makeStatusMap() {
    WritableMap m = Arguments.createMap();
    m.putBoolean("running", running);
    try {
      String addr = instance != null ? instance.getAddress() : "";
      m.putString("address", addr != null ? addr : "");
    } catch (Exception e) {
      m.putString("address", "");
    }
    try {
      String pk = instance != null ? instance.getPublicKey() : "";
      m.putString("publicKey", pk != null ? pk : "");
    } catch (Exception e) {
      m.putString("publicKey", "");
    }
    try {
      String peersJson = instance != null ? instance.getPeersJSON() : "[]";
      m.putString("peersJSON", peersJson != null ? peersJson : "[]");
    } catch (Exception e) {
      m.putString("peersJSON", "[]");
    }
    return m;
  }

   private void sendEvent(String eventName, Object data) {
    if (getReactApplicationContext().hasActiveReactInstance()) {
      getReactApplicationContext()
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, data);
    }
  }

  private void readFully(InputStream in, byte[] buffer) throws Exception {
    int offset = 0;
    while (offset < buffer.length) {
      int read = in.read(buffer, offset, buffer.length - offset);
      if (read < 0) throw new Exception("Connection closed");
      offset += read;
    }
  }

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

  @ReactMethod
  public void addRemoteTCPMapping(int remotePort, String localAddr, Promise promise) {
    try {
      if (instance != null) {
        instance.addRemoteTCPMapping(remotePort, localAddr);
      }
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOTE_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void removeRemoteTCPMapping(int remotePort, String localAddr, Promise promise) {
    try {
      if (instance != null) {
        instance.removeRemoteTCPMapping(remotePort, localAddr);
      }
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_REMOVE_REMOTE_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void clearRemoteMappings(Promise promise) {
    try {
      if (instance != null) {
        instance.clearRemoteMappings();
      }
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject("YGGSTACK_CLEAR_REMOTE_MAP_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void startMessengerServer(int port, Promise promise) {
    if (messengerServer != null) {
      promise.resolve(true);
      return;
    }
    messengerThread = new Thread(() -> {
      try {
        messengerServer = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
        Log.i(TAG, "Messenger server listening on " + port);
        promise.resolve(true);
        while (!messengerServer.isClosed()) {
          Socket client = messengerServer.accept();
          new Thread(() -> {
            try {
              BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), "UTF-8"));
              String line;
              while ((line = reader.readLine()) != null) {
                sendEvent("onMessengerMessage", line);
              }
              client.close();
            } catch (Exception ignored) {}
          }).start();
        }
      } catch (Exception e) {
        Log.e(TAG, "Messenger server error", e);
        promise.reject("START_SERVER_ERROR", e.getMessage(), e);
      }
    });
    messengerThread.start();
  }

  @ReactMethod
  public void stopMessengerServer(Promise promise) {
    try {
      if (messengerServer != null && !messengerServer.isClosed()) {
        messengerServer.close();
      }
    } catch (Exception ignored) {}
    messengerServer = null;
    messengerThread = null;
    promise.resolve(true);
  }

  @ReactMethod
  public void addListener(String eventName) {}

  @ReactMethod
  public void removeListeners(int count) {}

  @Override
  public void onHostResume() {}
  @Override
  public void onHostPause() {}
  @Override
  public void onHostDestroy() {
    if (foregroundServiceActive) return;
    try {
      if (instance != null) instance.stop();
    } catch (Exception ignored) {}
    try {
      if (messengerServer != null && !messengerServer.isClosed()) messengerServer.close();
    } catch (Exception ignored) {}
    running = false;
  }
}
