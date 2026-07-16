package expo.modules.audiotrack;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Tests the real Opus JNI bridge using a macOS .dylib built from the same
 * opus_jni.c and libopus that ship on Android. Runs actual encode/decode
 * roundtrips — no mocking.
 */
public class OpusBridgeTest {

    private static final int SAMPLE_RATE = 24000;
    private static final int CHANNELS = 1;
    private static final int APPLICATION_VOIP = 2048;
    private static final int FRAME_SIZE = 480; // 20ms @ 24kHz

    @Test
    public void encoderCreate_returnsNonNullHandle() {
        long enc = OpusBridge.nativeEncoderCreate(SAMPLE_RATE, CHANNELS, APPLICATION_VOIP);
        assertTrue("Encoder handle must be non-zero", enc != 0);
        OpusBridge.nativeEncoderDestroy(enc);
    }

    @Test
    public void encoderCreate_returnsZero_forInvalidSampleRate() {
        long enc = OpusBridge.nativeEncoderCreate(-1, CHANNELS, APPLICATION_VOIP);
        assertEquals(0, enc);
    }

    @Test
    public void decoderCreate_returnsNonNullHandle() {
        long dec = OpusBridge.nativeDecoderCreate(SAMPLE_RATE, CHANNELS);
        assertTrue("Decoder handle must be non-zero", dec != 0);
        OpusBridge.nativeDecoderDestroy(dec);
    }

    @Test
    public void decoderCreate_returnsZero_forInvalidChannels() {
        long dec = OpusBridge.nativeDecoderCreate(SAMPLE_RATE, 255);
        assertEquals(0, dec);
    }

    @Test
    public void encodeDecode_roundtrip_producesSameLengthPcm() {
        long enc = OpusBridge.nativeEncoderCreate(SAMPLE_RATE, CHANNELS, APPLICATION_VOIP);
        long dec = OpusBridge.nativeDecoderCreate(SAMPLE_RATE, CHANNELS);
        assertTrue(enc != 0);
        assertTrue(dec != 0);

        byte[] pcm = new byte[FRAME_SIZE * 2]; // 16-bit samples
        for (int i = 0; i < pcm.length; i += 2) {
            pcm[i] = (byte) (Math.sin(i / 20.0) * 50);
        }

        byte[] opusOut = new byte[4000];
        int opusLen = OpusBridge.nativeEncoderEncode(enc, pcm, FRAME_SIZE, opusOut);
        assertTrue("Encoded opus frame must be positive", opusLen > 0);
        assertTrue("Encoded frame must be smaller than PCM", opusLen < pcm.length);

        byte[] pcmOut = new byte[SAMPLE_RATE * 2 * 120 / 1000];
        int pcmLen = OpusBridge.nativeDecoderDecode(dec, opusOut, opusLen, pcmOut);
        assertTrue("Decoded PCM length must be positive", pcmLen > 0);
        assertEquals("Decoded PCM should match frame size", FRAME_SIZE * 2, pcmLen);

        OpusBridge.nativeEncoderDestroy(enc);
        OpusBridge.nativeDecoderDestroy(dec);
    }

    @Test
    public void decode_returnsNonNegative_forInvalidData() {
        long dec = OpusBridge.nativeDecoderCreate(SAMPLE_RATE, CHANNELS);
        assertTrue(dec != 0);

        byte[] garbage = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        byte[] pcmOut = new byte[SAMPLE_RATE * 2 * 120 / 1000];
        int result = OpusBridge.nativeDecoderDecode(dec, garbage, garbage.length, pcmOut);
        // Opus is resilient with short packets — it may return 0 or a small positive value
        // for very short or invalid data rather than an error code
        assertTrue("Decode result should not crash. Got: " + result, result >= 0);

        OpusBridge.nativeDecoderDestroy(dec);
    }

    @Test
    public void encodeEmptyPcm_returnsNegative() {
        long enc = OpusBridge.nativeEncoderCreate(SAMPLE_RATE, CHANNELS, APPLICATION_VOIP);
        assertTrue(enc != 0);

        byte[] pcm = new byte[10];
        byte[] opusOut = new byte[4000];
        int result = OpusBridge.nativeEncoderEncode(enc, pcm, 5, opusOut);
        assertTrue("Encode with tiny frame should return negative", result < 0);

        OpusBridge.nativeEncoderDestroy(enc);
    }

    @Test
    public void encoderDestroy_doesNotCrash_withZeroHandle() {
        OpusBridge.nativeEncoderDestroy(0);
    }

    @Test
    public void decoderDestroy_doesNotCrash_withZeroHandle() {
        OpusBridge.nativeDecoderDestroy(0);
    }

    @Test
    public void multipleEncoderDecoder_createsIndependentInstances() {
        long enc1 = OpusBridge.nativeEncoderCreate(SAMPLE_RATE, CHANNELS, APPLICATION_VOIP);
        long enc2 = OpusBridge.nativeEncoderCreate(SAMPLE_RATE, CHANNELS, APPLICATION_VOIP);
        assertNotEquals(enc1, enc2);

        long dec1 = OpusBridge.nativeDecoderCreate(SAMPLE_RATE, CHANNELS);
        long dec2 = OpusBridge.nativeDecoderCreate(SAMPLE_RATE, CHANNELS);
        assertNotEquals(dec1, dec2);

        OpusBridge.nativeEncoderDestroy(enc1);
        OpusBridge.nativeEncoderDestroy(enc2);
        OpusBridge.nativeDecoderDestroy(dec1);
        OpusBridge.nativeDecoderDestroy(dec2);
    }

    @Test
    public void encodeWithHighQuality_producesSmallerOutput() {
        long enc = OpusBridge.nativeEncoderCreate(SAMPLE_RATE, CHANNELS, APPLICATION_VOIP);

        byte[] pcm = new byte[FRAME_SIZE * 2];
        for (int i = 0; i < pcm.length; i += 2) {
            short val = (short) (Math.sin(i / 40.0 * Math.PI * 2) * 20000);
            pcm[i] = (byte) (val & 0xFF);
            pcm[i + 1] = (byte) ((val >> 8) & 0xFF);
        }

        byte[] opusOut = new byte[4000];
        int opusLen = OpusBridge.nativeEncoderEncode(enc, pcm, FRAME_SIZE, opusOut);
        assertTrue("Encoded opus length must be positive", opusLen > 0);
        assertTrue("Encoded size " + opusLen + " should be reasonable", opusLen < 500);

        OpusBridge.nativeEncoderDestroy(enc);
    }
}
