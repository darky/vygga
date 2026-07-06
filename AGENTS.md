# REPL-Driven Development

## Unit Tests

After each feature implementation or code change, run the test suite:

```bash
npm test
```

This compiles ClojureScript via shadow-cljs then runs the tests under Node.js.

For a quicker re-run (skip the compile step), use:

```bash
npm run test:quick
```

For TDD workflow, keep shadow-cljs watch running in a separate terminal:

```bash
# Terminal 1 (stays running)
npx shadow-cljs watch test

# Terminal 2 (re-run as needed)
npm run test:quick
```

### Test files structure

Tests live in `test/vygga/` mirroring `src/main/vygga/`. Each source file has a corresponding `*_test.cljs` file. Test namespaces use the suffix `-test` (discovered automatically by shadow-cljs's `-test$` regex).

### Test conventions

You are a TDD developer. Every new feature or code change should be well covered with unit tests. Before submitting any change:

1. Run `npm test` to verify all tests pass (existing + new)
2. Run `clj-kondo --lint src/main test` to catch regressions

Follow these conventions when writing tests:

1. **Test data first** — set up db state or input data at the top of each `deftest`
2. **Re-frame events** — use `re-frame.core/dispatch-sync` for synchronous testing; mock side-effect fx handlers via `re-frame.core/reg-fx`
3. **Subscriptions** — test the handler function's data transformation directly; avoid `rf/subscribe` outside reactive context
4. **JS interop** — native-only modules (`react-native`, `expo-*`, `@react-native-async-storage/async-storage`) are stubbed via `test/stubs/node_modules/`; the preload script at `test/support/preload.js` redirects `require()` for these modules at the Node.js level

Run clj-kondo on both source and test directories:

```bash
clj-kondo --lint src/main test
```

Then run the test suite before submitting any changes.

## Code Quality

After each finished task, run clj-kondo to lint Clojure(script) files:

```bash
clj-kondo --lint src/main
```

Then check for unused vars with Carve:

```bash
# Check (dry-run, won't modify files)
bb check-unused-vars

# Interactive removal (Y/n/i for each var)
bb remove-unused-vars
```

Then format Clojure files with cljfmt via babashka:

```bash
# Check formatting
bb check-format

# Auto-fix formatting
bb fix-format
```

## Performance: Choose the Right Data Structure

Re-frame event handlers run on every dispatch — O(n) scans in hot paths
compound as conversations grow. Follow these principles:

1. **Index what you look up by.** If you find a message by `:id`, store
   `{:messages [...], :msg-index {id -> idx}}` for O(1) updates instead of
   scanning every message with `mapv` or `some`. Same for contact-by-address
   lookups — a reverse index map turns O(n) into O(1).
2. **Use maps for uniqueness, not linear scan.** `(some #(= % uri) peers)` is
   O(n); a `set` or a map key check with `contains?` is O(1).
3. **Memoize in subscriptions, not in render.** Sorting contacts in a
   `reg-sub` runs once per db change; sorting in the component body runs
   on every re-render.
4. **Prefer seqs for traversal, vectors for indexing.** `map`, `filter`,
   `remove`, and `rseq` produce lazy seqs in O(1) — they wrap without
   copying. `mapv`, `filterv`, `vec`, and `reverse` realize into new
   collections. Use seqs when you only need to walk forward; use vectors
   only when you need random access by index.

## Clojure(script) Style: Minimize Nesting

Deeply nested async chains (`->` + `.then` + `fn` + `let` + `if`) cause
bracket-matching bugs. Follow these rules:

1. **Extract helper functions** when nesting exceeds 3 levels.
2. **Flatten chains with data-passing** — use maps as intermediate results
   between sequential `.then` steps instead of nesting deeper to keep
   variables in scope.
3. **Bind complex expressions to `let` vars first**, then branch on simple
   predicates. Never embed complex expressions directly inside `if` branches.
4. **Write small functions.** If a `defn` body exceeds ~15 lines or has 3+
   levels of `->`/`.then`/`let` nesting, split it up.
5. **Run `clj-kondo --lint src/main` immediately after writing any non-trivial
   async chain** to catch bracket errors early.

### Prefer this pattern for async chains

```clojure
(defn prepend-to-index!
  [cid entry]
  (let [promise (js/Promise.resolve)]
    (-> (.then promise #(load-manifest cid))
        (.then #(decide-action cid % entry))
        (.then #(execute-action cid %)))))
```

### Avoid deeply nested `->` + `fn` + `let` pyramids

```clojure
;; Don't: impossible to match brackets
(defn prepend-to-index!
  [cid entry]
  (-> (load-manifest cid)
      (.then (fn [m]
               (-> (load-chunk cid (:curr m))
                   (.then (fn [c]
                            (let [...]
                              (if (...)
                                (-> ... (.then ...))
                                (let [...]
                                  (-> ... (.then ...) (.then ...))))))))))))
```

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

### Add a log statement for debugging
```bash
echo '(js/console.log "debug:" (clj->js {:greeting "hello"}))' | bb scripts/nrepl_eval.clj
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

## Multi-Device Workflow (2 Android phones)

The eval script supports targeting specific devices via environment variables.

### List connected runtimes

```bash
LIST_RUNTIMES=true bb scripts/nrepl_eval.clj
# => [{:client-id "aaa-111..." :host :react-native :build-id :app ...}
#     {:client-id "bbb-222..." :host :react-native :build-id :app ...}]
```

Each connected device appears as a map with a `:client-id` — the handle used for targeting.

### Target a specific device

```bash
RUNTIME_ID=aaa-111... echo '(js/alert "Hello from device 1")' | bb scripts/nrepl_eval.clj
```

### Daily workflow

```bash
# 0. Build native APK (once, or after native changes)
npx expo run:android                                         # installs on first device
adb -s <serial2> install -r android/app/build/outputs/apk/debug/app-debug.apk   # second device

# 1. Start shadow-cljs watch (stays running)
npx shadow-cljs watch app

# 2. Launch the app on both phones (tap the app icon)

# 3. List connected runtimes
LIST_RUNTIMES=true bb scripts/nrepl_eval.clj
# Note both client-ids from the output

# 4. Terminal A — targets device 1
RUNTIME_ID=aaa-111... echo '@(re-frame.core/subscribe [:yggstack/status])' | bb scripts/nrepl_eval.clj

# 5. Terminal B — targets device 2
RUNTIME_ID=bbb-222... echo '@(re-frame.core/subscribe [:yggstack/status])' | bb scripts/nrepl_eval.clj

# 6. After hot reload / app restart, client-ids change — re-list and update terminals
```

### Multiple eval on same device in one go

```bash
RUNTIME_ID=aaa-111... printf '(inc 1)\n(inc 2)' | bb scripts/nrepl_eval.clj
```

## Troubleshooting

| Symptom | Likely Cause |
|---------|-------------|
| `watch for build not running` | `npx shadow-cljs watch app` isn't running |
| Connection refused | nREPL server not started yet |
| `No such namespace: js` | CLJS session not established (shadow build not running) |
| Timeout / no response | App not loaded on device/emulator yet |
| `LIST_RUNTIMES` returns empty vector | No devices connected yet — launch the app on each phone |
| `RUNTIME_ID` eval returns stale result | Device client-id changed after hot reload — re-list and update |
