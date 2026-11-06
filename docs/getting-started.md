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
2. **Size / Batch** tab: width, height, batch count. If the workflow drives
   its own size through a `ResolutionSelector` node (the Comfy-Org
   text-to-image templates for Qwen Image 2.1, FLUX.3 Klein, Krea 2 and MOSS
   all do; image-*edit* templates usually take their size from the input photo
   instead), an **Aspect ratio** chip row appears
   under the sliders: the sliders set how many pixels, the chips set the
   shape, and the preview box follows both. Pick a chip and the sliders snap
   to that shape; move a slider and the app picks the closest offered aspect
   again.
3. **Advanced** tab: seed (random or fixed, `Seed +1` to nudge), CFG scale, and
   step count. CFG/steps set to `0` keep whatever the workflow itself defines;
   any non-zero value overrides every sampler in the graph. Below them the
   **Sampler** and **Scheduler** dropdowns and a **denoise** slider appear for
   workflows that have a `KSampler` / `KSamplerAdvanced` / `KSamplerSelect`
   node — the option lists come from `object_info`, so samplers installed by
   custom nodes show up too. Leaving denoise at *keep* means the slider does
   nothing and the workflow's own value runs; moving it overrides every
   sampler node in the graph (inpaint-style workflows usually want ~1.0).
4. **Image inputs** (Prompt tab): the card under the prompt shows what the
   workflow consumes - how many `LoadImage` nodes it has, whether the size
   sliders apply, and where the prompt text goes. Each image slot gets its
   **own pick button** and shows a thumbnail of what it expects (your pick,
   or the template's own reference file, fetched from the server). Edit
   templates often load *several different* images (e.g. a portrait plus a
   clothing reference), so pick per slot; **Use one photo for all** covers
   workflows whose branches share a single input. Picked photos upload on
   Create and the size sliders snap to the first photo's aspect ratio (a
   square latent fed a portrait photo is what makes outputs look cropped).
   Picks are for this app session; re-picking the same photo doesn't
   re-upload it.
5. **Models** (Advanced tab): when the workflow loads model files the server
   can list (checkpoints, UNETs, LoRAs, VAEs, CLIPs, ControlNets), a dropdown
   appears for each — the same "pick your safetensors" step as in the web UI,
   without the graph. Options come live from the server; selections reset
   when you switch workflows. A LoRA that has a strength widget gets a slider
   right under its dropdown (model strength, and CLIP strength when the node
   has one), so "this LoRA at 0.4" is one thumb-move instead of a graph edit;
   leaving it alone keeps the workflow's value and *Use the workflow's ...
   strength* puts it back.
6. **Prompt presets** (Prompt tab, under the prompt box): save the prompt you
   just wrote with a name and comma-separated tags, star the keepers, and tap
   **Apply** to drop any of them into the prompt box. Search covers name,
   text and tags; the tag chips filter. Presets live on the phone, so they
   work with any workflow and survive server restarts.
7. Hit **Create**. Progress, current node and queue position show live;
   while sampling runs the preview grid shows the **live latent preview**
   streamed from the server (ComfyUI 0.43+). An ongoing notification tracks
   the run, so the generation keeps going (and stays visible) when you leave
   the app. Finished images land in the preview grid, the History tab and the
   ComfyUI output folder.

Create can be tapped again while a generation runs: the app queues another
job behind it (the server works the queue first-come, first-served) and all
images accumulate in one grid. While something is running, **Stop** appears
above Create — that interrupts only the current job, queued ones keep going.
When jobs are pending, **Clear queue** appears above History and drops every
queued job without touching the running one.

Marking runs on the History tab: **☆ keep** / **✕ discard** on a row are marks
on the *phone* (ComfyUI has no such thing, and nothing is sent to the server).
The chips above the list then show All / Kept / Discarded / Unmarked with
counts, and **Delete N discarded** deletes those runs on the server in one go,
after asking first. Marks are per device and are forgotten for runs that leave
the server history.

The gallery is cached: the first view of an output downloads it, every later
view (history list, batch grid, viewer) reads from the local image cache, and
opening the viewer warms the two images on each side so swiping is instant.

Rerunning a past generation: History tab → the refresh icon on a row re-queues
it with its exact original settings. The **Settings** button on a row shows
how that run was actually configured (seed, steps, CFG, sampler, scheduler,
denoise, model files) and **Use these settings on Create** prefills the
Advanced sliders with them — handy for re-rolling a good look with a new
prompt.

## Comfy-Org template library

The Workflows drawer has **Browse Comfy-Org templates**: ~760 ready-made
workflows with preview thumbnails, straight from Comfy's official template
repo (no account, no app store). Tap one, **Import to server** stores it on
your ComfyUI like anything you save in the web UI, and it appears under
Server Workflows. After the import the app tells you if the template uses
custom nodes your server doesn't have - install those (ComfyUI Manager)
before expecting it to run.

Chips under the search box filter the list by category: **Image**
(text-to-image) and **Image input** (img2img, inpainting, editing,
upscaling - these ask for a photo on the Create screen) are on by default;
**Video**, **3D**, **Audio** and **Comfy API** start off because those
workflows can't run on a normal local server (the Comfy API ones call
hosted nodes - the app warns you if you try to import one). Enable a chip
if you want to see them anyway.

## Prompts

- The **prompt library** is an offline dataset of ~20k image prompts baked
  into the app. Search it, tap a result, done. No network involved.
- **Recent prompts**: chips under the prompt box hold your last 10 prompts
  from successful runs; one tap restores one.
- **Enrich** (magic wand) expands a short prompt into a richer one using an
  LLM of your choice. Out of the box it uses Groq's hosted model: add a free
  API key from [console.groq.com](https://console.groq.com) in Settings, and
  nothing talks to Groq until you press the button.
- Settings also has **Enrichment endpoint & model**: point them at any
  OpenAI-compatible chat endpoint — OpenRouter, OpenAI, llama.cpp — or a
  local [Ollama](https://ollama.com) server (`http://<your-server>:11434/v1`
  plus a model name like `qwen2.5:7b-instruct`); no key needed there.
  Anthropic's API speaks a different protocol and is not supported.

## Drafts

Whatever you last had on the Create screen — prompt, negative prompt, size,
batch, seed, CFG, steps, workflow — is restored after an app restart. That
includes workflows you picked from the server: the app re-selects one right
after connecting.
