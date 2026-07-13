#include <opus.h>
#include <stdlib.h>
#include <string.h>

static OpusEncoder *encoder = NULL;
static OpusDecoder *decoder = NULL;

int opus_enc_init(int sampleRate, int channels, int application) {
    if (encoder) { opus_encoder_destroy(encoder); encoder = NULL; }
    int err;
    encoder = opus_encoder_create(sampleRate, channels, application, &err);
    return err;
}

int opus_enc_encode(const unsigned char *pcm, int frameSize,
                    unsigned char *output, int maxOutBytes) {
    if (!encoder) return -1;
    return opus_encode(encoder, (const opus_int16 *)pcm, frameSize,
                       output, maxOutBytes);
}

void opus_enc_destroy() {
    if (encoder) { opus_encoder_destroy(encoder); encoder = NULL; }
}

int opus_dec_init(int sampleRate, int channels) {
    if (decoder) { opus_decoder_destroy(decoder); decoder = NULL; }
    int err;
    decoder = opus_decoder_create(sampleRate, channels, &err);
    return err;
}

int opus_dec_decode(const unsigned char *opus, int opusLen,
                    unsigned char *pcm, int maxPcmBytes) {
    if (!decoder) return -1;
    int samples = opus_decode(decoder, opus, opusLen,
                              (opus_int16 *)pcm, maxPcmBytes / 2, 0);
    return samples >= 0 ? samples * 2 : samples;
}

void opus_dec_destroy() {
    if (decoder) { opus_decoder_destroy(decoder); decoder = NULL; }
}

void opus_destroy_all() {
    opus_enc_destroy();
    opus_dec_destroy();
}
