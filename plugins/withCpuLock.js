const {
  withPlugins,
  createRunOncePlugin,
  withAndroidManifest,
  withDangerousMod,
} = require('@expo/config-plugins');
const path = require('path');
const fs = require('fs');

const CPULOCK_PACKAGE = 'expo.modules.cpulock';
const CPULOCK_JAVA_SRC = path.join(__dirname, 'android-cpulock');

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

function withCpuLockMainApplicationKt(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const javaDir = path.join(
        cfg.modRequest.platformProjectRoot,
        'app', 'src', 'main', 'java'
      );
      const mainAppPath = findMainApplicationKt(javaDir);
      if (!mainAppPath) {
        console.warn('[withCpuLock] WARNING: MainApplication.kt not found under', javaDir);
        return cfg;
      }
      let code = fs.readFileSync(mainAppPath, 'utf8');

      const importLine = `import ${CPULOCK_PACKAGE}.CpuLockPackage`;
      const addLine = '          add(CpuLockPackage())';

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
      console.log('[withCpuLock] Updated MainApplication.kt');
      return cfg;
    },
  ]);
}

function withCpuLockModuleSources(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const srcDir = path.join(
        cfg.modRequest.platformProjectRoot,
        'app', 'src', 'main', 'java',
        ...CPULOCK_PACKAGE.split('.')
      );
      if (!fs.existsSync(srcDir)) fs.mkdirSync(srcDir, { recursive: true });

      const javaFiles = fs.readdirSync(CPULOCK_JAVA_SRC)
        .filter(f => f.endsWith('.java'));
      for (const file of javaFiles) {
        const code = fs.readFileSync(path.join(CPULOCK_JAVA_SRC, file), 'utf8');
        const dest = path.join(srcDir, file);
        if (file === 'CpuLockPackage.java' && fs.existsSync(dest)) continue;
        fs.writeFileSync(dest, code, 'utf8');
        console.log(`[withCpuLock] Created ${file}`);
      }

      return cfg;
    },
  ]);
}

function withCpuLockPermissions(config) {
  return withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults;
    const perms = manifest.manifest['uses-permission'] || [];
    const needed = [
      'android.permission.WAKE_LOCK',
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

function withCpuLock(config) {
  return withPlugins(config, [
    withCpuLockPermissions,
    withCpuLockModuleSources,
    withCpuLockMainApplicationKt,
  ]);
}

module.exports = createRunOncePlugin(withCpuLock, 'withCpuLock', '1.0.0');
