const { createRunOncePlugin, withAppBuildGradle } = require('@expo/config-plugins');

function withBuildVariants(config) {
  return withAppBuildGradle(config, (cfg) => {
    if (cfg.modResults.contents.includes('applicationIdSuffix ".dev"')) {
      return cfg;
    }

    cfg.modResults.contents = cfg.modResults.contents
      .replace(
        '            signingConfig signingConfigs.debug\n        }',
        '            signingConfig signingConfigs.debug\n            applicationIdSuffix ".dev"\n            resValue "string", "app_name", "Vygga Dev"\n        }'
      )
      .replace(
        '            signingConfig signingConfigs.debug\n            def enableShrinkResources',
        '            signingConfig signingConfigs.debug\n            resValue "string", "app_name", "Vygga"\n            def enableShrinkResources'
      );

    return cfg;
  });
}

module.exports = createRunOncePlugin(withBuildVariants, 'withBuildVariants', '1.0.0');
