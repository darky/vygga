const {
  withPlugins,
  createRunOncePlugin,
  withAppBuildGradle,
  withAndroidManifest,
  withDangerousMod,
} = require('@expo/config-plugins');
const path = require('path');
const fs = require('fs');

const YGGSTACK_AAR_SRC = path.join(
  __dirname, '..', 'vendor', 'yggstack', 'android-build', 'yggstack.aar'
);
const YGGSTACK_PACKAGE = 'expo.modules.yggstack';
const YGGSTACK_JAVA_SRC = path.join(__dirname, 'android');

function withYggstackCopyAAR(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const libsDir = path.join(cfg.modRequest.platformProjectRoot, 'app', 'libs');
      if (!fs.existsSync(libsDir)) fs.mkdirSync(libsDir, { recursive: true });
      if (fs.existsSync(YGGSTACK_AAR_SRC)) {
        fs.copyFileSync(YGGSTACK_AAR_SRC, path.join(libsDir, 'yggstack.aar'));
        console.log('[withYggstack] Copied yggstack.aar to android/app/libs/');
      } else {
        console.warn('[withYggstack] WARNING: yggstack.aar not found at', YGGSTACK_AAR_SRC);
      }
      return cfg;
    },
  ]);
}

function withYggstackAppBuildGradle(config) {
  return withAppBuildGradle(config, (cfg) => {
    const depLine = "    implementation files('libs/yggstack.aar')";
    if (!cfg.modResults.contents.includes(depLine)) {
      cfg.modResults.contents = cfg.modResults.contents.replace(
        /(dependencies\s*\{)/,
        `$1\n${depLine}`
      );
    }
    return cfg;
  });
}

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

function withYggstackMainApplicationKt(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const javaDir = path.join(
        cfg.modRequest.platformProjectRoot,
        'app', 'src', 'main', 'java'
      );
      const mainAppPath = findMainApplicationKt(javaDir);
      if (!mainAppPath) {
        console.warn('[withYggstack] WARNING: MainApplication.kt not found under', javaDir);
        return cfg;
      }
      let code = fs.readFileSync(mainAppPath, 'utf8');

      const importLine = `import ${YGGSTACK_PACKAGE}.YggstackPackage`;
      const addLine = '          add(YggstackPackage())';

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
      console.log('[withYggstack] Updated MainApplication.kt');
      return cfg;
    },
  ]);
}

function withYggstackPermissions(config) {
  return withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults;
    const perms = manifest.manifest['uses-permission'] || [];
    const needed = [
      'android.permission.INTERNET',
      'android.permission.ACCESS_NETWORK_STATE',
      'android.permission.FOREGROUND_SERVICE',
      'android.permission.FOREGROUND_SERVICE_DATA_SYNC',
      'android.permission.POST_NOTIFICATIONS',
    ];
    const existing = perms.map((p) => p.$['android:name']);
    for (const perm of needed) {
      if (!existing.includes(perm)) {
        perms.push({ $: { 'android:name': perm } });
      }
    }
    manifest.manifest['uses-permission'] = perms;

    // Add YggdrasilService as a foreground service
    const app = manifest.manifest.application || [];
    if (!Array.isArray(app)) {
      manifest.manifest.application = [app];
    }
    const appElem = manifest.manifest.application[0];
    if (appElem) {
      const services = appElem['service'] || [];
      const fullServiceName = `${YGGSTACK_PACKAGE}.YggdrasilService`;
      const hasService = services.some(
        (s) => s.$['android:name'] === fullServiceName
      );
      if (!hasService) {
        services.push({
          $: {
            'android:name': fullServiceName,
            'android:foregroundServiceType': 'dataSync',
            'android:exported': 'false',
            'android:stopWithTask': 'false',
          },
        });
        appElem['service'] = services;
      }
    }

    return cfg;
  });
}

function withYggstackModuleSources(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const srcDir = path.join(
        cfg.modRequest.platformProjectRoot,
        'app', 'src', 'main', 'java',
        ...YGGSTACK_PACKAGE.split('.')
      );
      if (!fs.existsSync(srcDir)) fs.mkdirSync(srcDir, { recursive: true });

      const javaFiles = fs.readdirSync(YGGSTACK_JAVA_SRC)
        .filter(f => f.endsWith('.java'));
      for (const file of javaFiles) {
        const code = fs.readFileSync(path.join(YGGSTACK_JAVA_SRC, file), 'utf8');
        const dest = path.join(srcDir, file);
        // Only overwrite YggstackPackage.java if it doesn't exist (user may customise)
        if (file === 'YggstackPackage.java' && fs.existsSync(dest)) continue;
        fs.writeFileSync(dest, code, 'utf8');
        console.log(`[withYggstack] Created ${file}`);
      }

      return cfg;
    },
  ]);
}

function withYggstack(config) {
  return withPlugins(config, [
    withYggstackCopyAAR,
    withYggstackAppBuildGradle,
    withYggstackPermissions,
    withYggstackModuleSources,
    withYggstackMainApplicationKt,
  ]);
}

module.exports = createRunOncePlugin(withYggstack, 'withYggstack', '1.0.0');
