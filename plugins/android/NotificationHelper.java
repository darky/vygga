package expo.modules.yggstack;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {

  private static final String CHANNEL_ID = "yggdrasil_service";
  private static final String MSG_CHANNEL_ID = "messages_channel";
  private static final int FOREGROUND_NOTIFICATION_ID = 9001;
  private static int msgNotifId = 1000;

  public static void createChannels(Context ctx) {
    createServiceChannel(ctx);
    createMessageChannel(ctx);
  }

  private static void createServiceChannel(Context ctx) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
        CHANNEL_ID,
        "Yggdrasil Service",
        NotificationManager.IMPORTANCE_LOW
      );
      channel.setDescription("Keeps the app running to receive messages");
      channel.setShowBadge(false);
      NotificationManager mgr = ctx.getSystemService(NotificationManager.class);
      if (mgr != null) mgr.createNotificationChannel(channel);
    }
  }

  private static void createMessageChannel(Context ctx) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
        MSG_CHANNEL_ID,
        "Messages",
        NotificationManager.IMPORTANCE_HIGH
      );
      channel.setDescription("Incoming chat messages");
      channel.setSound(
        android.net.Uri.parse("android.resource://" + ctx.getPackageName() + "/raw/music_marimba_chord"),
        new android.media.AudioAttributes.Builder()
          .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
          .build()
      );
      channel.enableVibration(true);
      NotificationManager mgr = ctx.getSystemService(NotificationManager.class);
      if (mgr != null) mgr.createNotificationChannel(channel);
    }
  }

  public static Notification buildForeground(Context ctx, String title, String text) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
      .setContentTitle(title)
      .setContentText(text)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW);
    int icon = getIconId(ctx);
    if (icon != 0) builder.setSmallIcon(icon);
    return builder.build();
  }

  public static void showMessageNotification(Context ctx, String sender, String text) {
    NotificationManager mgr = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (mgr == null) return;
    Intent openIntent = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
    PendingIntent contentIntent = PendingIntent.getActivity(
      ctx, 0, openIntent,
      PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification notification = new NotificationCompat.Builder(ctx, MSG_CHANNEL_ID)
      .setSmallIcon(getIconId(ctx))
      .setContentTitle("Message from " + sender)
      .setContentText(text)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(contentIntent)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .build();
    mgr.notify("vygga_messages", msgNotifId++, notification);
  }

  static int getForegroundNotificationId() {
    return FOREGROUND_NOTIFICATION_ID;
  }

  private static int getIconId(Context ctx) {
    int icon = ctx.getResources().getIdentifier("ic_launcher", "mipmap", ctx.getPackageName());
    return icon != 0 ? icon : android.R.drawable.ic_dialog_info;
  }
}
