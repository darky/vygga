package expo.modules.yggstack;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import link.yggdrasil.yggstack.mobile.Mobile;
import link.yggdrasil.yggstack.mobile.Yggstack;
import link.yggdrasil.yggstack.mobile.LogCallback;

public class YggdrasilService extends Service {

  private static final String TAG = "YggdrasilService";
  private static final String CHANNEL_ID = "yggdrasil_service";
  private static final int NOTIFICATION_ID = 9001;
  private static final String PREFS_NAME = "yggdrasil_service_prefs";
  private static final String PREF_CONFIG = "configJSON";
  private static final String PREF_SOCKS = "socksAddress";
  private static final String PREF_NS = "nameserver";

  private static Yggstack instance;
  private static boolean running = false;
  private static ServerSocket messengerServer;
  private static Thread messengerThread;

  private static String lastConfigJSON;
  private static String lastSocksAddress;
  private static String lastNameserver;

  private static boolean jsActive = false;
  private static int msgNotifId = 1000;
  private static Context appContext;

  private static final CopyOnWriteArrayList<MessageListener> messageListeners = new CopyOnWriteArrayList<>();
  private static final CopyOnWriteArrayList<String> pendingMessages = new CopyOnWriteArrayList<>();
  private static final CopyOnWriteArrayList<LogListener> logListeners = new CopyOnWriteArrayList<>();
  private static final CopyOnWriteArrayList<Runnable> statusChangeListeners = new CopyOnWriteArrayList<>();

  // Pattern to extract :text and :from from EDN message {:type "message", :from "addr", :text "body", ...}
  private static final Pattern EDN_TEXT_PATTERN = Pattern.compile(":text\\s+\"((?:[^\"\\\\]|\\\\.)*)\"");
  private static final Pattern EDN_FROM_PATTERN = Pattern.compile(":from\\s+\"((?:[^\"\\\\]|\\\\.)*)\"");

  public interface MessageListener {
    void onMessage(String message);
  }

  public interface LogListener {
    void onLog(String message);
  }

  public static void addMessageListener(MessageListener l) {
    messageListeners.add(l);
    // Flush any messages that arrived while no listener was active
    while (!pendingMessages.isEmpty()) {
      String msg = pendingMessages.remove(0);
      if (msg != null) l.onMessage(msg);
    }
  }

  public static void removeMessageListener(MessageListener l) { messageListeners.remove(l); }

  public static List<String> pollPendingMessages() {
    List<String> batch = new java.util.ArrayList<>(pendingMessages);
    pendingMessages.clear();
    return batch;
  }

  public static void setJsActive(boolean active) {
    jsActive = active;
  }

  private static String extractQuoted(String raw, Pattern p) {
    Matcher m = p.matcher(raw);
    return m.find() ? m.group(1) : null;
  }

  private static void showMessageNotification(Context ctx, String raw) {
    String text = extractQuoted(raw, EDN_TEXT_PATTERN);
    String from = extractQuoted(raw, EDN_FROM_PATTERN);
    if (text == null) return;
    String sender = from != null && from.length() > 8 ? from.substring(0, 8) : "Unknown";
    NotificationManager mgr = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (mgr == null) return;
    Intent openIntent = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
    PendingIntent contentIntent = PendingIntent.getActivity(
      ctx, 0, openIntent,
      PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification notification = new NotificationCompat.Builder(ctx, "messages_channel")
      .setSmallIcon(getIconId(ctx))
      .setContentTitle("Message from " + sender)
      .setContentText(text)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(contentIntent)
      .build();
    mgr.notify("vygga_messages", msgNotifId++, notification);
  }

  private static int getIconId(Context ctx) {
    int icon = ctx.getResources().getIdentifier("ic_launcher", "mipmap", ctx.getPackageName());
    return icon != 0 ? icon : android.R.drawable.ic_dialog_info;
  }

  public static void addLogListener(LogListener l) { logListeners.add(l); }
  public static void removeLogListener(LogListener l) { logListeners.remove(l); }

  public static void addStatusChangeListener(Runnable l) { statusChangeListeners.add(l); }
  public static void removeStatusChangeListener(Runnable l) { statusChangeListeners.remove(l); }

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

  private static void notifyLog(String message) {
    for (LogListener l : logListeners) {
      l.onLog(message);
    }
  }

  private static void notifyStatusChange() {
    for (Runnable l : statusChangeListeners) {
      l.run();
    }
  }

  private static void persistConfig(Context context) {
    if (lastConfigJSON == null) return;
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(PREF_CONFIG, lastConfigJSON)
      .putString(PREF_SOCKS, lastSocksAddress)
      .putString(PREF_NS, lastNameserver)
      .apply();
  }

  private static void clearPersistedConfig(Context context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
  }

  private static boolean hasPersistedConfig(Context context) {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(PREF_CONFIG);
  }

  private static void restorePersistedConfig(Context context) {
    if (lastConfigJSON != null) return;
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    lastConfigJSON = prefs.getString(PREF_CONFIG, null);
    lastSocksAddress = prefs.getString(PREF_SOCKS, null);
    lastNameserver = prefs.getString(PREF_NS, null);
  }

  private static void startYggdrasilInternal(String configJSON, String socksAddress, String nameserver) throws Exception {
    instance = Mobile.newYggstack();
    instance.setLogLevel("info");
    instance.setLogCallback(message -> notifyLog(message));
    instance.loadConfigJSON(configJSON);
    instance.start(socksAddress, nameserver);
    running = true;
    notifyStatusChange();
  }

  // Called by the module to start the service + yggdrasil
  public static void startYggdrasil(Context context, String configJSON, String socksAddress, String nameserver) throws Exception {
    if (running) return;

    lastConfigJSON = configJSON;
    lastSocksAddress = socksAddress;
    lastNameserver = nameserver;
    persistConfig(context);

    startYggdrasilInternal(configJSON, socksAddress, nameserver);

    Intent intent = new Intent(context, YggdrasilService.class);
    intent.setAction("start");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(intent);
    } else {
      context.startService(intent);
    }
  }

  // Called by the module to stop yggdrasil + stop the service
  public static void stopYggdrasil(Context context) throws Exception {
    if (instance != null) {
      instance.stop();
    }
    running = false;
    instance = null;
    lastConfigJSON = null;
    lastSocksAddress = null;
    lastNameserver = null;
    clearPersistedConfig(context);

    context.stopService(new Intent(context, YggdrasilService.class));
    notifyStatusChange();
  }

  public static void addPeer(String uri) throws Exception {
    if (instance != null) instance.addPeer(uri);
  }

  public static void removePeer(String uri) throws Exception {
    if (instance != null) instance.removePeer(uri);
  }

  public static void addRemoteTCPMapping(int remotePort, String localAddr) throws Exception {
    if (instance != null) instance.addRemoteTCPMapping(remotePort, localAddr);
  }

  public static void removeRemoteTCPMapping(int remotePort, String localAddr) throws Exception {
    if (instance != null) instance.removeRemoteTCPMapping(remotePort, localAddr);
  }

  public static void clearRemoteMappings() throws Exception {
    if (instance != null) instance.clearRemoteMappings();
  }

  public static void startMessengerServer(int port) throws Exception {
    if (messengerServer != null) return;
    messengerThread = new Thread(() -> {
      try {
        messengerServer = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
        Log.i(TAG, "Messenger server listening on " + port);
        while (!messengerServer.isClosed()) {
          Socket client = messengerServer.accept();
          new Thread(() -> {
            try {
              BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), "UTF-8"));
              String line;
              while ((line = reader.readLine()) != null) {
                final String msg = line;
                pendingMessages.add(msg);
                if (!jsActive && appContext != null) {
                  showMessageNotification(appContext, msg);
                }
                for (MessageListener l : messageListeners) {
                  l.onMessage(msg);
                }
              }
              client.close();
            } catch (Exception ignored) {}
          }).start();
        }
      } catch (Exception e) {
        Log.e(TAG, "Messenger server error", e);
      }
    });
    messengerThread.start();
  }

  public static void stopMessengerServer() {
    try {
      if (messengerServer != null && !messengerServer.isClosed()) {
        messengerServer.close();
      }
    } catch (Exception ignored) {}
    messengerServer = null;
    messengerThread = null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    appContext = getApplicationContext();
    createNotificationChannel();
    createMessageNotificationChannel();
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    Notification notification = buildNotification("Yggdrasil Messenger",
      running ? "Listening for messages..." : "Reconnecting...");
    startForeground(NOTIFICATION_ID, notification);

    if (!running) {
      if (lastConfigJSON == null) {
        restorePersistedConfig(this);
      }
      if (lastConfigJSON != null) {
        try {
          Log.i(TAG, "Auto-restarting yggdrasil from onStartCommand");
          startYggdrasilInternal(lastConfigJSON,
            lastSocksAddress != null ? lastSocksAddress : "127.0.0.1:1080",
            lastNameserver != null ? lastNameserver : "");
        } catch (Exception e) {
          Log.e(TAG, "Auto-restart yggdrasil failed", e);
        }
      }
    }

    return START_STICKY;
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    // Swiping from recents: some OEMs kill the process even with stopWithTask=false.
    // Schedule a re-start in 1s to come right back if killed.
    Intent restartIntent = new Intent(this, YggdrasilService.class);
    restartIntent.setAction("start");
    PendingIntent pendingIntent = PendingIntent.getService(
      this, 1, restartIntent,
      PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
    AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pendingIntent);

    super.onTaskRemoved(rootIntent);
  }

  @Override
  public void onDestroy() {
    if (hasPersistedConfig(this)) {
      // Expected restart (task removed or OEM killing) — don't clean up yggdrasil
      stopForeground(false);
      super.onDestroy();
      return;
    }
    // Clean shutdown (user stopped via UI)
    if (running) {
      try {
        if (instance != null) instance.stop();
      } catch (Exception ignored) {}
      running = false;
      instance = null;
    }
    stopMessengerServer();
    stopForeground(true);
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
        CHANNEL_ID,
        "Yggdrasil Service",
        NotificationManager.IMPORTANCE_LOW
      );
      channel.setDescription("Keeps the app running to receive messages");
      channel.setShowBadge(false);
      NotificationManager manager = getSystemService(NotificationManager.class);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
  }

  private void createMessageNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
        "messages_channel",
        "Messages",
        NotificationManager.IMPORTANCE_HIGH
      );
      channel.setDescription("Incoming chat messages");
      channel.setSound(
        android.net.Uri.parse("android.resource://" + getPackageName() + "/raw/music_marimba_chord"),
        new android.media.AudioAttributes.Builder()
          .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
          .build()
      );
      channel.enableVibration(true);
      NotificationManager manager = getSystemService(NotificationManager.class);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
  }

  private Notification buildNotification(String title, String text) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(title)
      .setContentText(text)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW);
    int icon = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
    if (icon != 0) builder.setSmallIcon(icon);
    return builder.build();
  }

  public static String generateConfig() {
    try {
      return Mobile.generateConfig();
    } catch (Exception e) {
      Log.e(TAG, "generateConfig error", e);
      return null;
    }
  }
}
