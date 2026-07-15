const {
  withPlugins,
  createRunOncePlugin,
  withAndroidManifest,
  withDangerousMod,
} = require('@expo/config-plugins');
const path = require('path');
const fs = require('fs');

const WIFILOCK_PACKAGE = 'expo.modules.wifilock';
const WIFILOCK_JAVA_SRC = path.join(__dirname, 'android-wifilock');

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

function withWifiLockMainApplicationKt(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const javaDir = path.join(
        cfg.modRequest.platformProjectRoot,
        'app', 'src', 'main', 'java'
      );
      const mainAppPath = findMainApplicationKt(javaDir);
      if (!mainAppPath) {
        console.warn('[withWifiLock] WARNING: MainApplication.kt not found under', javaDir);
        return cfg;
      }
      let code = fs.readFileSync(mainAppPath, 'utf8');

      const importLine = `import ${WIFILOCK_PACKAGE}.WifiLockPackage`;
      const addLine = '          add(WifiLockPackage())';

      if (!code.includes(importLine)) {
        code = code.replace(
          /(import expo\.modules\.\S+)/,
          `$1\n${importLine}`
        );
      }
      if (!code.includes(addLine)) {
        code = code.replace(
          /(PackageList\(this\)\.packages\.apply \{)/,
          `$1\n${addLine}`
        );
      }

      fs.writeFileSync(mainAppPath, code, 'utf8');
      console.log('[withWifiLock] Updated MainApplication.kt');
      return cfg;
    },
  ]);
}

function withWifiLockModuleSources(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const srcDir = path.join(
        cfg.modRequest.platformProjectRoot,
        'app', 'src', 'main', 'java',
        ...WIFILOCK_PACKAGE.split('.')
      );
      if (!fs.existsSync(srcDir)) fs.mkdirSync(srcDir, { recursive: true });

      const javaFiles = fs.readdirSync(WIFILOCK_JAVA_SRC)
        .filter(f => f.endsWith('.java'));
      for (const file of javaFiles) {
        const code = fs.readFileSync(path.join(WIFILOCK_JAVA_SRC, file), 'utf8');
        const dest = path.join(srcDir, file);
        if (file === 'WifiLockPackage.java' && fs.existsSync(dest)) continue;
        fs.writeFileSync(dest, code, 'utf8');
        console.log(`[withWifiLock] Created ${file}`);
      }

      return cfg;
    },
  ]);
}

function withWifiLockPermissions(config) {
  return withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults;
    const perms = manifest.manifest['uses-permission'] || [];
    const needed = [
      'android.permission.ACCESS_WIFI_STATE',
      'android.permission.CHANGE_WIFI_STATE',
    ];
    const existing = perms.map((p) => p.$['android:name']);
    for (const perm of needed) {
      if (!existing.includes(perm)) {
        perms.push({ $: { 'android:name': perm } });
      }
    }
    manifest.manifest['uses-permission'] = perms;
    return cfg;
  });
}

function withWifiLock(config) {
  return withPlugins(config, [
    withWifiLockPermissions,
    withWifiLockModuleSources,
    withWifiLockMainApplicationKt,
  ]);
}

module.exports = createRunOncePlugin(withWifiLock, 'withWifiLock', '1.0.0');
