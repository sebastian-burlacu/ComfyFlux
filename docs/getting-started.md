# Getting started

## What this app is

ComfyFlux is an Android front end for a ComfyUI server you run yourself. You
queue and monitor image generations from your phone; the graph stays on the
desktop where it's comfortable to edit. One server URL, no account, no cloud.

## Before you install the app

Your ComfyUI must listen on the network, not just localhost:

```
python main.py --listen
```

Find the machine's LAN address (e.g. `ip addr` / `ifconfig` on Linux) and note
the port (default `8188`). The phone and the server must be on the same network
(or otherwise routed to each other).

## First connection

Open the app. The server field defaults to the URL configured at build time;
change it if needed (`http://192.168.1.5:8188` style — plain HTTP, no TLS).
The app shows OS/Python stats when the server answers; if it doesn't, the
error under the field tells you why (wrong address, server down, firewall).

Android 13+ asks for a notification permission on first start. Say yes if you
want a buzz when a background generation finishes; everything else works
without it.

## Workflows

Two sources, picked from the drawer on the Create screen:

- **Bundled** — a few workflows ship in the app (FLUX dev/schnell, SDXL).
- **Server** — anything you saved in the ComfyUI web UI under
  `userdata/workflows/`. The app lists them automatically once connected and
  converts them at run time. To make a workflow available, just save it in the
  web UI with *Save* (Ctrl+S); refresh the app's connection (pull the status
  tab / reopen the drawer) to pick up new files.

## Generating

1. Pick a workflow, write a prompt.
2. **Size / Batch** tab: width, height, batch count.
3. **Advanced** tab: seed (random or fixed, `Seed +1` to nudge), CFG scale, and
   step count. CFG/steps set to `0` keep whatever the workflow itself defines;
   any non-zero value overrides every sampler in the graph.
4. **Models** (Advanced tab): when the workflow loads model files the server
   can list (checkpoints, UNETs, LoRAs, VAEs, CLIPs, ControlNets), a dropdown
   appears for each — the same "pick your safetensors" step as in the web UI,
   without the graph. Options come live from the server; selections reset
   when you switch workflows.
5. Hit generate. Progress, current node and queue position show live; finished
   images land in the History tab and in the ComfyUI output folder.

Rerunning a past generation: History tab → the refresh icon on a row re-queues
it with its exact original settings.

## Prompts

- The **prompt library** is an offline dataset of ~20k image prompts baked
  into the app. Search it, tap a result, done. No network involved.
- **Recent prompts**: chips under the prompt box hold your last 10 prompts
  from successful runs; one tap restores one.
- **Enrich** (magic wand) expands a short prompt into a richer one using
  Groq's hosted LLM. It's completely optional: add a free API key from
  [console.groq.com](https://console.groq.com) in Settings, and nothing talks
  to Groq until you press the button.

## Drafts

Whatever you last had on the Create screen — prompt, size, batch, seed, CFG,
steps, bundled workflow — is restored after an app restart. Server-selected
workflows aren't (their list only exists after connecting).
