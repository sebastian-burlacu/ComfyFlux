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

Settings has **Hide built-in workflows**, which drops the samples that ship
with the app from that list; your own server and device workflows then sit at
the top instead of under a pile of samples.

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
   step count. Both fields come pre-filled with the number that workflow really
   has in its sampler — the label says so, `CFG (workflow: 3.5)` — so you tweak
   a value instead of guessing what was there. Clearing a field defers to the
   graph again; any non-zero value overrides every sampler in the graph. This covers the
   newer *guide-graph* workflows too (Qwen Image 2.1, FLUX.2, MiniMax H3, the
   Comfy-Org `RandomNoise` + `CFGGuider`/`BasicGuider` + `BasicScheduler`
   templates), where the knobs are split across nodes: CFG goes to the guider
   (`CFGGuider`, or `cfg_conds` on a `DualCFGGuider`), the step count goes to
   the node that builds the sigma schedule (`BasicScheduler`,
   `Flux2Scheduler`, `SDTurboScheduler`, …). Templates that park those numbers
   behind `PrimitiveInt`/`PrimitiveFloat` nodes — often behind a
   `ComfySwitchNode` that offers two of them, which is exactly how the FLUX.2
   template sets its steps — are written at the primitive instead of breaking
   the link, so the slider actually takes effect there.
   Below them the **Sampler** and **Scheduler** dropdowns and a **denoise**
   slider appear for workflows that have a `KSampler` / `KSamplerAdvanced` /
   `KSamplerSelect` node (sampler name) or a `BasicScheduler`-style scheduler
   node (scheduler, denoise) — the option lists come from `object_info`, so
   samplers installed by custom nodes show up too. Leaving denoise at *keep*
   means the slider does nothing and the workflow's own value runs; moving it
   overrides every sampler node in the graph (inpaint-style workflows usually
   want ~1.0).
   Where a graph genuinely has nothing to write, the app says so under the
   fields instead of letting a slider do nothing: FLUX.2 and the Qwen turbo
   graphs fix their own guidance (no CFG widget), and a graph that samples a
   literal `ManualSigmas` list has no step count to change — edit that list in
   ComfyUI.
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
   re-upload it. When the graph consumes a **MASK** (`SetLatentNoiseMask`,
   `InpaintModelConditioning`, `LoadImageMask`), each picked photo gets a
   **Mask** button: paint over the areas to regenerate, then Apply. The mask
   rides in the photo's own alpha channel (ComfyUI reads masks off alpha), so
   that slot uploads a fresh file and *Mask ✓* with the clear icon drops the
   paint. Workflows without a mask input show no button - it would do nothing.
5. **Models** (Advanced tab): when the workflow loads model files the server
   can list (checkpoints, UNETs, LoRAs, VAEs, CLIPs, ControlNets), a dropdown
   appears for each — the same "pick your safetensors" step as in the web UI,
   without the graph. Options come live from the server; selections reset
   when you switch workflows. A LoRA that has a strength widget gets a slider
   right under its dropdown (model strength, and CLIP strength when the node
   has one), so "this LoRA at 0.4" is one thumb-move instead of a graph edit;
   leaving it alone keeps the workflow's value and *Use the workflow's ...
   strength* puts it back. Stacked-LoRA graphs (`lora_0` … `lora_3` feeding one
   apply chain) get a strength slider per loader too, not just per file.
6. **Other strengths** (Advanced tab, under Models): nodes that *apply*
   something rather than load it also carry a strength the app can drive —
   `ControlNetApply`/`ControlNetApplyAdvanced`, differential diffusion,
   conditioning-area strength and friends. Each gets its own **… strength**
   slider grouped under the node that holds it, with the same
   *use the workflow's value* reset. Strengths the graph computes itself (the
   input is fed by another node that isn't a plain number box) are left alone
   rather than snapped to a literal.
7. **Graph knobs** (Advanced tab, under strengths): widgets your graph
   exposes that no panel above owns - `FluxGuidance.guidance`,
   `ModelSamplingSD3.shift`, or a custom node's own sliders and dropdowns -
   listed per node and driven by one control each. Detection is generic over
   the server's specs (FLOAT/INT/BOOLEAN/COMBO), so an unusual node shows up
   without waiting for a whitelist entry; inputs other panels already drive
   (prompt, models, sampler, size, CFG/steps/seed) never appear twice. Linked
   values are written through their source primitive box rather than being
   flattened, and *use the workflow's value* restores the graph's own number.
   Selections persist per workflow like the other Settings.
8. **Prompt presets** (Prompt tab, under the prompt box): save the prompt you
   just wrote with a name and comma-separated tags, star the keepers, and tap
   **Apply** to drop any of them into the prompt box. Search covers name,
   text and tags; the tag chips filter. Presets live on the phone, so they
   work with any workflow and survive server restarts.
9. Hit **Create**. Progress, current node and queue position show live;
   while sampling runs the preview grid shows the **live latent preview**
   streamed from the server (ComfyUI 0.43+). An ongoing notification tracks
   the run, so the generation keeps going (and stays visible) when you leave
   the app. Finished images land in the preview grid, the History tab and the
   ComfyUI output folder.
   Finishing (or failing) also raises a notification when the app is in the
   background. That covers runs you started **elsewhere** too: while the app is
   open it watches the server queue, so a generation you kicked off in the web
   UI or on another phone still announces itself when it lands.

**Before** you queue, the Create screen says what this server cannot provide: a
card above the workflow-needs card names the model files the template asks for
that the server does not have — including the ones the app swapped for a
different file to keep the workflow importable — plus any node types the server
does not know. *Show all* and *Copy list* hand you the exact names to download.
Substituted runs often still work; the card is there so a bad result is
explainable instead of mysterious.

Create can be tapped again while a generation runs: the app queues another
job behind it (the server works the queue first-come, first-served) and all
images accumulate in one grid. While something is running, **Stop** appears
above Create — that interrupts only the current job, queued ones keep going.
The Stop button follows the *server*, not just this app's own session: if the
web UI (or another phone) started the run, you still get Stop, not just Clear
queue, as long as the app is open and watching. When jobs are pending, **Clear
queue** appears above History and drops every queued job without touching the
running one. To drop a *single* queued job, open the drawer's **Queue** tab and
use the remove button on that row; the delete call addresses the job by its
prompt id, which every queue entry carries. The `#12` on the row is the
server's queue position, shown so you can tell which job is which.

Marking runs on the History tab: **☆ keep** / **✕ discard** on a row are marks
on the *phone* (ComfyUI has no such thing, and nothing is sent to the server).
The chips above the list then show All / Kept / Discarded / Unmarked with
counts, and **Delete N discarded** deletes those runs on the server in one go,
after asking first. Marks are per device and are forgotten for runs that leave
the server history.

Video and audio workflows work too. `SaveVideo`, `SaveAudio` and the animated
savers finish a graph with a clip or a track instead of a PNG, and the app files
those outputs by kind: the batch grid and the History rows show a labelled tile
instead of a thumbnail, and tapping one opens the viewer, which plays mp4/webm
clips and audio with a scrub bar. Formats the phone itself cannot decode (a
ProRes `.mov`, for instance) keep an **open in another app** button, which hands
the server URL to a player that can. Saving puts clips in `Movies/ComfyFlux` and
audio in `Music/ComfyFlux` (stills stay in `Pictures`), and the finished-run
notification says what arrived — "1 video" reads differently from "4 images".
Clips stream from the server while playing, so they are not part of the image
cache; saving is the copy that stays on the phone.

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

### Settings that follow the workflow

Model picks, strength sliders, sampler/scheduler/denoise, the aspect chip and
CFG/steps are remembered **per workflow** (keyed by server + workflow name, the
60 most recent kept), not per app session. Switch from a Qwen graph to a FLUX
graph and each gets its own values back — including after a restart. A choice
the current workflow or server can no longer offer is dropped instead of sent,
so a LoRA you picked on one graph can't leak into another.
