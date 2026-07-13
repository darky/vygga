const {
  createRunOncePlugin,
  withDangerousMod,
  withAndroidManifest,
} = require('@expo/config-plugins');
const path = require('path');
const fs = require('fs');

const PACKAGE = 'expo.modules.audiotrack';
const JAVA_SRC = path.join(__dirname, 'android-audiotrack');

function findMainApplicationKt(javaDir) {
  if (!fs.existsSync(javaDir)) return null;
  const entries = fs.readdirSync(javaDir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(javaDir, entry.name);
    if (entry.isDirectory()) {
      const found = findMainApplicationKt(fullPath);
      if (found) return found;
    } else if (entry.name === 'MainApplication.kt') {
      return fullPath;
    }
  }
  return null;
}

function withModuleSources(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const srcDir = path.join(
        cfg.modRequest.platformProjectRoot,
        'app', 'src', 'main', 'java',
        ...PACKAGE.split('.')
      );
      if (!fs.existsSync(srcDir)) fs.mkdirSync(srcDir, { recursive: true });

      const javaFiles = fs.readdirSync(JAVA_SRC)
        .filter(f => f.endsWith('.java'));
      for (const file of javaFiles) {
        const code = fs.readFileSync(path.join(JAVA_SRC, file), 'utf8');
        const dest = path.join(srcDir, file);
        if (file === 'AudioTrackPackage.java' && fs.existsSync(dest)) continue;
        fs.writeFileSync(dest, code, 'utf8');
        console.log('[withAudioTrack] Created ' + file);
      }

      const jniSrc = path.join(JAVA_SRC, 'jniLibs');
      const jniDst = path.join(
        cfg.modRequest.platformProjectRoot,
        'app', 'src', 'main', 'jniLibs'
      );
      if (fs.existsSync(jniSrc)) {
        copyRecursiveSync(jniSrc, jniDst);
        console.log('[withAudioTrack] Copied jniLibs');
      }

      return cfg;
    },
  ]);
}

function withMainApplication(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const javaDir = path.join(
        cfg.modRequest.platformProjectRoot,
        'app', 'src', 'main', 'java'
      );
      const mainAppPath = findMainApplicationKt(javaDir);
      if (!mainAppPath) {
        console.warn('[withAudioTrack] WARNING: MainApplication.kt not found under', javaDir);
        return cfg;
      }
      let code = fs.readFileSync(mainAppPath, 'utf8');

      const importLine = 'import ' + PACKAGE + '.AudioTrackPackage';
      const addLine = '          add(AudioTrackPackage())';

      if (!code.includes(importLine)) {
        code = code.replace(
          /(import expo\.modules\.\S+)/,
          '$1\n' + importLine
        );
      }
      if (!code.includes(addLine)) {
        code = code.replace(
          /(PackageList\(this\)\.packages\.apply \{)/,
          '$1\n' + addLine
        );
      }

      fs.writeFileSync(mainAppPath, code, 'utf8');
      console.log('[withAudioTrack] Updated MainApplication.kt');
      return cfg;
    },
  ]);
}

function withPermissions(config) {
  return withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults;
    const perms = manifest.manifest['uses-permission'] || [];
    const existing = perms.map((p) => p.$['android:name']);
    const needed = ['android.permission.RECORD_AUDIO'];
    for (const perm of needed) {
      if (!existing.includes(perm)) {
        perms.push({ $: { 'android:name': perm } });
      }
    }
    manifest.manifest['uses-permission'] = perms;
    return cfg;
  });
}

function copyRecursiveSync(src, dest) {
  if (!fs.existsSync(dest)) fs.mkdirSync(dest, { recursive: true });
  const entries = fs.readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const s = path.join(src, entry.name);
    const d = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyRecursiveSync(s, d);
    } else {
      fs.copyFileSync(s, d);
    }
  }
}

function withAudioTrack(config) {
  return withModuleSources(
    withMainApplication(
      withPermissions(config)
    )
  );
}

module.exports = createRunOncePlugin(withAudioTrack, 'withAudioTrack', '1.0.0');
