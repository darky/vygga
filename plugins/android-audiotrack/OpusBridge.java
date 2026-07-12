package expo.modules.audiotrack;

public class OpusBridge {

  static {
    System.loadLibrary("opus_jni");
  }

  // Encoder
  public static native long nativeEncoderCreate(int sampleRate, int channels, int application);
  public static native int nativeEncoderEncode(long encoder, byte[] pcm, int frameSize, byte[] output);
  public static native void nativeEncoderDestroy(long encoder);

  // Decoder
  public static native long nativeDecoderCreate(int sampleRate, int channels);
  public static native int nativeDecoderDecode(long decoder, byte[] opus, int opusLen, byte[] output);
  public static native void nativeDecoderDestroy(long decoder);

  private OpusBridge() {}
}
