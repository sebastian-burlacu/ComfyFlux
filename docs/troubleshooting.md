# Troubleshooting & bug reports

## The log first

Settings → **Logs** shows what the app actually did, newest first. Every entry
is tagged:

- **server** — something your ComfyUI reported: a failed execution (with node
  type and message), a rejected prompt, an interrupt, connection successes.
- **client** — app-side failures: connection attempts, timeouts, websocket
  losses, workflow fetch/conversion errors.

Use *Errors only* to cut noise. The log lives in memory: restarting the app
clears it, so export anything you might need before closing the app.

## Verbose logging

Settings → **Verbose logging: On** makes the log keep debug-level detail:
every HTTP call with endpoint, status, payload size and duration, websocket
connect/events, and full stack traces for failures. Leave it off normally —
it fills the buffer fast — turn it on the moment something misbehaves,
reproduce once, then export.

## Exporting a log

In the Logs screen, tap the download icon. The app writes a text file and
opens the share sheet (mail, Drive, chat, wherever). File name is
`comfyflux-logs-<date>-<time>.txt`.

## Reading common messages

| Message | Meaning |
| --- | --- |
| `Connection refused` / `Failed to resolve host` | Server isn't listening on the network (`--listen`), wrong IP/port, or phone on a different network |
| `Workflow 'name' came back empty (HTTP 404)` | The file listed in the drawer no longer matches the server (renamed/deleted since the last refresh) — reconnect to refresh the list |
| `prompt rejected: ...` | ComfyUI refused the graph before running: usually a node type that isn't installed on the server. The full `node_errors` follow in the log |
| `execution failed: ... CUDA out of memory` | The GPU ran out. Close whatever else owns VRAM or lower resolution/batch. If the text looks like a remote API error (`type=api_error`), it came from a custom node's upstream service, not your machine |
| `Generation timed out after 15 minutes` | No terminal event within the limit; long video/large workflows may need patience, dead ones need a server restart |
| `Prompt enrichment failed: HTTP 500 ...` | Groq's hosted model hiccuped; generation is unaffected — retry or type the prompt yourself |

**A generation that fails before reaching the server** (fetch/convert errors)
never appears in ComfyUI's console at all — no server logs doesn't mean no
client error. The export is the full story from the phone's side.

## Filing a bug report

1. Settings → Verbose logging: **On**.
2. Reproduce the problem once.
3. Settings → Logs → export icon → keep the file.
4. Open an issue: **https://github.com/sebastian-burlacu/ComfyFlux/issues**
   with the app version (Settings footer / about), what you expected, what
   happened, and the exported log attached or pasted.

Version numbers matter: each release is tagged `v*` on the same page, and the
issue template history shows what's already fixed.

## Known limitations

- Generation always runs the *server's* copy of a server workflow; there's no
  local editing.
- No image upload / img2img input from the phone yet.
- Connections are plain HTTP; keep the server on a trusted LAN (no TLS, no
  auth in either side).
- Notifications only fire for generations started in this app instance.
- History reflects the server's history endpoint: purging history in the web
  UI removes those entries (the downloaded images stay in your gallery/history
  viewer).
