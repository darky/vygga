package expo.modules.yggstack;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

import link.yggdrasil.yggstack.mobile.Mobile;
import link.yggdrasil.yggstack.mobile.Yggstack;

public class YggdrasilManager {

  private static final String TAG = "YggdrasilManager";

  private static Yggstack instance;
  private static boolean running = false;
  private static Context appContext;

  private static final CopyOnWriteArrayList<LogListener> logListeners = new CopyOnWriteArrayList<>();
  private static final CopyOnWriteArrayList<Runnable> statusChangeListeners = new CopyOnWriteArrayList<>();

  public interface LogListener {
    void onLog(String message);
  }

  public static void setAppContext(Context ctx) {
    appContext = ctx;
  }

  public static Context getAppContext() {
    return appContext;
  }

  // ---- Status ----

  public static boolean isRunning() {
    return running;
  }

  public static String getAddress() {
    try {
      return instance != null ? instance.getAddress() : "";
    } catch (Exception e) {
      return "";
    }
  }

  public static String getPublicKey() {
    try {
      return instance != null ? instance.getPublicKey() : "";
    } catch (Exception e) {
      return "";
    }
  }

  public static String getPeersJSON() {
    try {
      return instance != null ? instance.getPeersJSON() : "[]";
    } catch (Exception e) {
      return "[]";
    }
  }

  public static String getPeers() {
    try {
      return instance != null ? instance.getPeers() : "[]";
    } catch (Exception e) {
      return "[]";
    }
  }

  // ---- Log listeners ----

  public static void addLogListener(LogListener l) { logListeners.add(l); }
  public static void removeLogListener(LogListener l) { logListeners.remove(l); }

  private static void notifyLog(String message) {
    for (LogListener l : logListeners) {
      l.onLog(message);
    }
  }

  // ---- Status change listeners ----

  public static void addStatusChangeListener(Runnable l) { statusChangeListeners.add(l); }
  public static void removeStatusChangeListener(Runnable l) { statusChangeListeners.remove(l); }

  private static void notifyStatusChange() {
    for (Runnable l : statusChangeListeners) {
      l.run();
    }
  }

  // ---- Lifecycle ----

  private static void startInternal(String configJSON, String socksAddress, String nameserver) throws Exception {
    instance = Mobile.newYggstack();
    instance.setLogLevel("info");
    instance.setLogCallback(message -> notifyLog(message));
    instance.loadConfigJSON(configJSON);
    instance.start(socksAddress, nameserver);
    running = true;
    notifyStatusChange();
  }

  public static void start(Context context, String configJSON, String socksAddress, String nameserver) throws Exception {
    if (running) return;
    startInternal(configJSON, socksAddress, nameserver);
  }

  public static void stop(Context context) throws Exception {
    if (instance != null) {
      instance.stop();
    }
    running = false;
    instance = null;
    notifyStatusChange();
  }

  public static void stopInternal() {
    try {
      if (instance != null) instance.stop();
    } catch (Exception ignored) {}
    running = false;
    instance = null;
  }

  // ---- Peers ----

  public static void retryPeersNow() throws Exception {
    if (instance != null) instance.retryPeersNow();
  }

  public static void addPeer(String uri) throws Exception {
    if (instance != null) instance.addPeer(uri);
  }

  public static void removePeer(String uri) throws Exception {
    if (instance != null) instance.removePeer(uri);
  }

  // ---- TCP mappings ----

  public static void addRemoteTCPMapping(int remotePort, String localAddr) throws Exception {
    if (instance != null) instance.addRemoteTCPMapping(remotePort, localAddr);
  }

  public static void removeRemoteTCPMapping(int remotePort, String localAddr) throws Exception {
    if (instance != null) instance.removeRemoteTCPMapping(remotePort, localAddr);
  }

  public static void clearRemoteMappings() throws Exception {
    if (instance != null) instance.clearRemoteMappings();
  }

  // ---- UDP mappings ----

  public static void addRemoteUDPMapping(int remotePort, String localAddr) throws Exception {
    if (instance != null) instance.addRemoteUDPMapping(remotePort, localAddr);
  }

  public static void removeRemoteUDPMapping(int remotePort, String localAddr) throws Exception {
    if (instance != null) instance.removeRemoteUDPMapping(remotePort, localAddr);
  }

  public static void addLocalUDPMapping(String localAddr, String remoteAddr) throws Exception {
    if (instance != null) instance.addLocalUDPMapping(localAddr, remoteAddr);
  }

  public static void removeLocalUDPMapping(String localAddr, String remoteAddr) throws Exception {
    if (instance != null) instance.removeLocalUDPMapping(localAddr, remoteAddr);
  }

  // ---- Config generation ----

  public static String generateConfig() {
    try {
      return Mobile.generateConfig();
    } catch (Exception e) {
      Log.e(TAG, "generateConfig error", e);
      return null;
    }
  }
}
