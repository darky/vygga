# REPL-Driven Development

This project uses a Babashka-based nREPL client (`scripts/nrepl_eval.clj`) to evaluate ClojureScript code directly on the running Android app via the shadow-cljs nREPL server.

## Prerequisites

Before evaluating any CLJS code, these must be running:

1. **shadow-cljs watch** — builds CLJS and runs the nREPL server
2. **Expo/Android** — loads the app, providing a CLJS runtime to evaluate against

## Usage

Pipe ClojureScript code to the script via stdin:

```bash
echo '(js/alert "Hello from REPL")' | bb scripts/nrepl_eval.clj
```

The script:
- Reads the nREPL port from `.shadow-cljs/nrepl.port`
- Connects to the running shadow-cljs nREPL server
- Clones a session and switches it to CLJS for the `:app` build
- Evaluates the code and prints `:out`, `:err`, and `:value`

## Common Operations

### Check REPL connection
```bash
echo '(+ 1 2 3)' | bb scripts/nrepl_eval.clj
```
Expected: `6`

### Show an alert on the device
```bash
echo '(js/alert "Hello from REPL!")' | bb scripts/nrepl_eval.clj
```

### Read re-frame app-db state
```bash
echo '(-> @re-frame.db/app-db clj->js js/JSON.stringify js/JSON.parse (js/console.log))' | bb scripts/nrepl_eval.clj
```

### Dispatch a re-frame event
```bash
echo '(re-frame.core/dispatch [:inc-counter])' | bb scripts/nrepl_eval.clj
```

### Call a component function / deref a subscription
```bash
echo '@(re-frame.core/subscribe [:get-counter])' | bb scripts/nrepl_eval.clj
```

### Add a log statement for debugging
```bash
echo '(js/console.log "debug:" (clj->js {:counter 42}))' | bb scripts/nrepl_eval.clj
```

## Multi-line code

Use `printf` or echo with newlines:

```bash
printf '(let [x 42]\n  (js/alert (str "The answer is " x)))' | bb scripts/nrepl_eval.clj
```

## Yggdrasil Network (yggstack Native Module)

The app embeds [yggstack](https://github.com/DrewCyber/yggstack) via gomobile JNI bindings for Yggdrasil network access.

### Stack
- **Go** — `vendor/yggstack/mobile/yggstack.go` (gomobile bindings)
- **AAR** — `vendor/yggstack/android-build/yggstack.aar` (built via `./mobile/build-android.sh`)
- **Config Plugin** — `plugins/withYggstack.js` (copies AAR + registers native module at prebuild)
- **Java Module** — `YggstackModule.java` (exposes Go methods to React Native JS)
- **CLJS Bridge** — `example.yggstack` (re-frame events/subs wrapping native module)

### First-time Setup (dev build)

```bash
# Build AAR (if not already done)
cd vendor/yggstack && ANDROID_HOME=$HOME/Library/Android/sdk ./mobile/build-android.sh

# Generate native Android project + run config plugin
npx expo prebuild

# Build and install dev build on device
npx expo run:android
```

After this, the normal CLJS workflow (shadow-cljs watch + nREPL) continues to work.

### Yggdrasil REPL commands

```bash
# Check connection status
echo '@(re-frame.core/subscribe [:yggstack/status])' | bb scripts/nrepl_eval.clj

# Check peer count
echo '@(re-frame.core/subscribe [:yggstack/peer-count])' | bb scripts/nrepl_eval.clj

# Check IPv6 address
echo '@(re-frame.core/subscribe [:yggstack/address])' | bb scripts/nrepl_eval.clj

# Start Yggdrasil
echo '(re-frame.core/dispatch [:yggstack/start])' | bb scripts/nrepl_eval.clj

# Stop Yggdrasil
echo '(re-frame.core/dispatch [:yggstack/stop])' | bb scripts/nrepl_eval.clj

# Add a peer
echo '(re-frame.core/dispatch [:yggstack/add-peer "tls://example.com:443"])' | bb scripts/nrepl_eval.clj

# Remove a peer
echo '(re-frame.core/dispatch [:yggstack/remove-peer "tls://example.com:443"])' | bb scripts/nrepl_eval.clj
```

## Troubleshooting

| Symptom | Likely Cause |
|---------|-------------|
| `watch for build not running` | `npx shadow-cljs watch app` isn't running |
| Connection refused | nREPL server not started yet |
| `No such namespace: js` | CLJS session not established (shadow build not running) |
| Timeout / no response | App not loaded on device/emulator yet |
