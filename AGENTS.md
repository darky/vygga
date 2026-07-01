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

## Troubleshooting

| Symptom | Likely Cause |
|---------|-------------|
| `watch for build not running` | `npx shadow-cljs watch app` isn't running |
| Connection refused | nREPL server not started yet |
| `No such namespace: js` | CLJS session not established (shadow build not running) |
| Timeout / no response | App not loaded on device/emulator yet |
