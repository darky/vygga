var path = require('path');
var Module = require('module');
var origResolve = Module._resolveFilename;

var stubDir = path.resolve(__dirname, '../stubs/node_modules');

var stubModules = {
  'react-native': true,
  'expo-secure-store': true,
  'react-native-battery-optimization-check': true,
  'expo-status-bar': true,
  '@react-navigation/native': true,
  '@react-navigation/native-stack': true,
  'react-native-safe-area-context': true,
  '@expo/vector-icons/Ionicons': true,
  'expo-clipboard': true,
  'expo-notifications': true,
  'react-native-tcp-socket': true,
  '@react-native-community/netinfo': true,
};

Module._resolveFilename = function(request, parent) {
  if (stubModules[request]) {
    return path.join(stubDir, request, 'index.js');
  }
  return origResolve.apply(this, arguments);
};

/* tweetnacl is a pure JS lib. Ensure the global reference exists
   for node-test compiled CLJS which uses bare `nacl` in direct
   property access patterns (nacl.randomBytes vs (.. nacl ...)). */
var tweetnacl = require('tweetnacl');
global.nacl = tweetnacl;
global.tweetnacl = tweetnacl;
