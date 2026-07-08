package expo.modules.yggstack;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class MessengerClient {

  public static void send(String targetAddr, String message) throws Exception {
    Socket socket = new Socket("127.0.0.1", 1080);
    try {
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
    } finally {
      try { socket.close(); } catch (Exception ignored) {}
    }
  }

  private static void readFully(InputStream in, byte[] buffer) throws Exception {
    int offset = 0;
    while (offset < buffer.length) {
      int read = in.read(buffer, offset, buffer.length - offset);
      if (read < 0) throw new Exception("Connection closed");
      offset += read;
    }
  }
}
