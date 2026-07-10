package expo.modules.yggstack;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

public class MessengerServer {

  private static final String TAG = "MessengerServer";

  private static ServerSocket serverSocket;
  private static Thread serverThread;

  private static final CopyOnWriteArrayList<MessageListener> messageListeners = new CopyOnWriteArrayList<>();

  public interface MessageListener {
    void onNewMessage(String message);
  }

  public static void addMessageListener(MessageListener l) {
    messageListeners.add(l);
  }

  public static void removeMessageListener(MessageListener l) {
    messageListeners.remove(l);
  }

  public static boolean isRunning() {
    return serverSocket != null && !serverSocket.isClosed();
  }

  public static void start(int port) {
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
                for (MessageListener l : messageListeners) {
                  l.onNewMessage(msg);
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
}
