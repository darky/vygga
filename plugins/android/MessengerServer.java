package expo.modules.yggstack;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessengerServer {

  private static final String TAG = "MessengerServer";

  private static ServerSocket serverSocket;
  private static Thread serverThread;

  private static final CopyOnWriteArrayList<MessageListener> messageListeners = new CopyOnWriteArrayList<>();
  private static final CopyOnWriteArrayList<String> pendingMessages = new CopyOnWriteArrayList<>();

  private static final Pattern EDN_TEXT_PATTERN = Pattern.compile(":text\\s+\"((?:[^\"\\\\]|\\\\.)*)\"");
  private static final Pattern EDN_FROM_PATTERN = Pattern.compile(":from\\s+\"((?:[^\"\\\\]|\\\\.)*)\"");

  public interface MessageListener {
    void onMessage(String message);
  }

  public static void addMessageListener(MessageListener l) {
    messageListeners.add(l);
    while (!pendingMessages.isEmpty()) {
      String msg = pendingMessages.remove(0);
      if (msg != null) l.onMessage(msg);
    }
  }

  public static void removeMessageListener(MessageListener l) {
    messageListeners.remove(l);
  }

  public static List<String> pollPendingMessages() {
    List<String> batch = new java.util.ArrayList<>(pendingMessages);
    pendingMessages.clear();
    return batch;
  }

  public static boolean isRunning() {
    return serverSocket != null && !serverSocket.isClosed();
  }

  public static void start(int port, Context context) {
    if (serverSocket != null) return;
    serverThread = new Thread(() -> {
      try {
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
        Log.i(TAG, "Messenger server listening on " + port);
        while (!serverSocket.isClosed()) {
          Socket client = serverSocket.accept();
          new Thread(() -> {
            try {
              BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), "UTF-8"));
              String line;
              while ((line = reader.readLine()) != null) {
                final String msg = line;
                pendingMessages.add(msg);
                if (context != null) {
                  showMessageNotification(context, msg);
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
    serverThread.start();
  }

  public static void stop() {
    try {
      if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
      }
    } catch (Exception ignored) {}
    serverSocket = null;
    serverThread = null;
  }

  // ---- EDN helpers ----

  private static String extractQuoted(String raw, Pattern p) {
    Matcher m = p.matcher(raw);
    return m.find() ? m.group(1) : null;
  }

  private static void showMessageNotification(Context ctx, String raw) {
    String text = extractQuoted(raw, EDN_TEXT_PATTERN);
    String from = extractQuoted(raw, EDN_FROM_PATTERN);
    if (text == null) return;
    String sender = from != null && from.length() > 8 ? from.substring(0, 8) : "Unknown";
    NotificationHelper.showMessageNotification(ctx, sender, text);
  }
}
