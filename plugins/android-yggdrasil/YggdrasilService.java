package expo.modules.yggstack;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class YggdrasilService extends Service {

  private static final int FOREGROUND_NOTIFICATION_ID = 9001;
  private static final int KEEPALIVE_REQUEST_CODE = 9002;
  private static final long KEEPALIVE_INTERVAL_MS = 600000L;

  public static void startYggdrasil(Context context, String configJSON, String socksAddress, String nameserver) throws Exception {
    if (YggdrasilManager.isRunning()) return;
    YggdrasilManager.start(context, configJSON, socksAddress, nameserver);
    Intent intent = new Intent(context, YggdrasilService.class);
    intent.setAction("start");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(intent);
    } else {
      context.startService(intent);
    }
  }

  public static void stopYggdrasil(Context context) throws Exception {
    YggdrasilManager.stop(context);
    context.stopService(new Intent(context, YggdrasilService.class));
  }

  @Override
  public void onCreate() {
    super.onCreate();
    YggdrasilManager.setAppContext(getApplicationContext());
    createServiceChannel();
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    if (intent != null && "keepalive".equals(intent.getAction())) {
      try {
        YggdrasilManager.retryPeersNow();
      } catch (Exception e) {
        Log.w("YggdrasilService", "keepalive retryPeersNow error", e);
      }
      scheduleKeepalive();
      return START_STICKY;
    }
    startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification());
    scheduleKeepalive();
    return START_STICKY;
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
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
    cancelKeepalive();
    YggdrasilManager.stopInternal();
    stopForeground(true);
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  // ---- Foreground notification ----

  private void createServiceChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
        "yggdrasil_service",
        "Yggdrasil Service",
        NotificationManager.IMPORTANCE_LOW
      );
      channel.setDescription("Keeps the app running to receive messages");
      channel.setShowBadge(false);
      NotificationManager mgr = getSystemService(NotificationManager.class);
      if (mgr != null) mgr.createNotificationChannel(channel);
    }
  }

  private Notification buildForegroundNotification() {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "yggdrasil_service")
      .setContentTitle("Yggdrasil Messenger")
      .setContentText(YggdrasilManager.isRunning() ? "Listening for messages..." : "Reconnecting...")
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW);
    int icon = getIconId();
    if (icon != 0) builder.setSmallIcon(icon);
    Notification notification = builder.build();
    notification.flags |= Notification.FLAG_NO_CLEAR;
    return notification;
  }

  private int getIconId() {
    int icon = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
    return icon != 0 ? icon : android.R.drawable.ic_dialog_info;
  }

  // ---- Doze keepalive ----

  private void scheduleKeepalive() {
    Intent intent = new Intent(this, YggdrasilService.class);
    intent.setAction("keepalive");
    PendingIntent pi = PendingIntent.getService(this, KEEPALIVE_REQUEST_CODE, intent,
      PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    if (alarm == null) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
        SystemClock.elapsedRealtime() + KEEPALIVE_INTERVAL_MS, pi);
    } else {
      alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
        SystemClock.elapsedRealtime() + KEEPALIVE_INTERVAL_MS, pi);
    }
    Log.d("YggdrasilService", "Keepalive scheduled in " + KEEPALIVE_INTERVAL_MS + "ms");
  }

  private void cancelKeepalive() {
    Intent intent = new Intent(this, YggdrasilService.class);
    PendingIntent pi = PendingIntent.getService(this, KEEPALIVE_REQUEST_CODE, intent,
      PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
    if (pi != null) {
      AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
      if (alarm != null) alarm.cancel(pi);
      pi.cancel();
      Log.d("YggdrasilService", "Keepalive cancelled");
    }
  }
}
