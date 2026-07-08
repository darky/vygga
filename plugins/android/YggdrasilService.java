package expo.modules.yggstack;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

public class YggdrasilService extends Service {

  private static final String TAG = "YggdrasilService";

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
    NotificationHelper.createChannels(this);
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    startForeground(
      NotificationHelper.getForegroundNotificationId(),
      NotificationHelper.buildForeground(this,
        "Yggdrasil Messenger",
        YggdrasilManager.isRunning() ? "Listening for messages..." : "Reconnecting..."));

    if (!YggdrasilManager.isRunning()) {
      YggdrasilManager.restorePersistedConfig(this);
      String config = YggdrasilManager.getLastConfigJSON();
      if (config != null) {
        try {
          Log.i(TAG, "Auto-restarting yggdrasil from onStartCommand");
          YggdrasilManager.start(this, config,
            YggdrasilManager.getLastSocksAddress() != null
              ? YggdrasilManager.getLastSocksAddress() : "127.0.0.1:1080",
            YggdrasilManager.getLastNameserver() != null
              ? YggdrasilManager.getLastNameserver() : "");
        } catch (Exception e) {
          Log.e(TAG, "Auto-restart yggdrasil failed", e);
        }
      }
    }

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
    if (YggdrasilManager.hasPersistedConfig(this)) {
      stopForeground(false);
      super.onDestroy();
      return;
    }
    YggdrasilManager.stopInternal();
    MessengerServer.stop();
    stopForeground(true);
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
