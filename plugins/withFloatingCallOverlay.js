const {
  createRunOncePlugin,
  withDangerousMod,
} = require('@expo/config-plugins');
const path = require('path');
const fs = require('fs');

const PACKAGE = 'expo.modules.floatingcalloverlay';
const JAVA_SRC = path.join(__dirname, 'android-floating-overlay');

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
        if (file === 'FloatingCallOverlayPackage.java' && fs.existsSync(dest)) continue;
        fs.writeFileSync(dest, code, 'utf8');
        console.log('[withFloatingCallOverlay] Created ' + file);
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
        console.warn('[withFloatingCallOverlay] WARNING: MainApplication.kt not found');
        return cfg;
      }
      let code = fs.readFileSync(mainAppPath, 'utf8');

      const importLine = 'import ' + PACKAGE + '.FloatingCallOverlayPackage';
      const addLine = '          add(FloatingCallOverlayPackage())';

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
      console.log('[withFloatingCallOverlay] Updated MainApplication.kt');
      return cfg;
    },
  ]);
}

function withFloatingCallOverlay(config) {
  return withModuleSources(
    withMainApplication(config)
  );
}

module.exports = createRunOncePlugin(withFloatingCallOverlay, 'withFloatingCallOverlay', '1.0.0');
