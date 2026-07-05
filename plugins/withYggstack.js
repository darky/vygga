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

function withYggstackMainApplicationKt(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const mainAppPath = path.join(
        cfg.modRequest.platformProjectRoot,
        'app', 'src', 'main', 'java',
        'com', 'anonymous', 'reagentexpo', 'MainApplication.kt'
      );
      if (!fs.existsSync(mainAppPath)) {
        console.warn('[withYggstack] WARNING: MainApplication.kt not found at', mainAppPath);
        return cfg;
      }
      let code = fs.readFileSync(mainAppPath, 'utf8');

      const importLine = `import ${YGGSTACK_PACKAGE}.YggstackPackage`;
      const addLine = '            add(YggstackPackage())';

      if (!code.includes(importLine)) {
        code = code.replace(
          /(import expo\.modules\.\S+)/,
          `$1\n${importLine}`
        );
      }
      if (!code.includes(addLine)) {
        code = code.replace(
          /(\/\/ add\(MyReactNativePackage\(\)\))/,
          `${addLine}\n          $1`
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
      'android.permission.POST_NOTIFICATIONS',
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

      const moduleCode = fs.readFileSync(path.join(YGGSTACK_JAVA_SRC, 'YggstackModule.java'), 'utf8');
      const moduleFile = path.join(srcDir, 'YggstackModule.java');
      fs.writeFileSync(moduleFile, moduleCode, 'utf8');
      console.log('[withYggstack] Created YggstackModule.java');

      const packageCode = fs.readFileSync(path.join(YGGSTACK_JAVA_SRC, 'YggstackPackage.java'), 'utf8');
      const packageFile = path.join(srcDir, 'YggstackPackage.java');
      if (!fs.existsSync(packageFile)) {
        fs.writeFileSync(packageFile, packageCode, 'utf8');
        console.log('[withYggstack] Created YggstackPackage.java');
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
