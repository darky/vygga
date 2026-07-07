const {
  createRunOncePlugin,
  withDangerousMod,
} = require('@expo/config-plugins');
const path = require('path');
const fs = require('fs');

const SOUND_SRC = path.join(__dirname, '..', 'assets', 'sounds', 'music_marimba_chord.wav');

function withNotificationSoundCopyRaw(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const rawDir = path.join(cfg.modRequest.platformProjectRoot, 'app', 'src', 'main', 'res', 'raw');
      if (!fs.existsSync(rawDir)) fs.mkdirSync(rawDir, { recursive: true });
      if (fs.existsSync(SOUND_SRC)) {
        fs.copyFileSync(SOUND_SRC, path.join(rawDir, 'music_marimba_chord.wav'));
        console.log('[withNotificationSound] Copied music_marimba_chord.wav to android/app/src/main/res/raw/');
      } else {
        console.warn('[withNotificationSound] WARNING: music_marimba_chord.wav not found at', SOUND_SRC);
      }
      return cfg;
    },
  ]);
}

function withNotificationSound(config) {
  return withNotificationSoundCopyRaw(config);
}

module.exports = createRunOncePlugin(withNotificationSound, 'withNotificationSound', '1.0.0');
