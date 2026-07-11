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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class YggdrasilService extends Service {

  private static final int FOREGROUND_NOTIFICATION_ID = 9001;

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
    startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification());
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
    return builder.build();
  }

  private int getIconId() {
    int icon = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
    return icon != 0 ? icon : android.R.drawable.ic_dialog_info;
  }
}
