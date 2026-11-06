# How workflows work here

## Two JSON formats

ComfyUI speaks two JSON shapes, and they are *not* interchangeable:

- **API format** — what `POST /prompt` accepts: `{ "3": { "class_type": ..., "inputs": ... } }`.
- **UI graph format** — what the web UI saves under `userdata/workflows/`:
  nodes with positions, widget values, and link arrays.

Bundled workflows in the app are API format and are used as-is. Server
workflows are UI format; the app converts them at run time: it expands the
link graph into API inputs, flattens subgraph nodes, and assigns stable node
ids. If you export via *Save (Export)* instead of the normal save, the app
recognises API format and skips conversion.

## What the app patches into a workflow before running it

| Control | What gets rewritten |
| --- | --- |
| Prompt | The positive `CLIPTextEncode` text input (found by walking conditioning links; through switch nodes only the active branch is touched, so a "prompt enhance on/off" switch picks the right box) |
| Negative prompt | The `negative` conditioning branch's text input — a dedicated `negative_prompt` slot or a second `CLIPTextEncode`. The field only appears for workflows that actually have one; graphs deriving the negative from the positive (ConditioningZeroOut) are left alone |
| Size / batch | `EmptyLatentImage` width/height/batch_size |
| Seed | `KSampler` seed / `KSamplerAdvanced` & `RandomNoise` noise_seed |
| CFG / steps | `cfg` and `steps` on every `KSampler`/`KSamplerAdvanced` (cfg only for `CFGGuider`); `0` = leave untouched |
| Models | The file input of any loader node the server can list files for (`ckpt_name`, `unet_name`, `lora_name`, `vae_name`, `clip_name*`, `control_net_name`, ...) — detection is driven by the server's own `object_info`, so custom GGUF loaders work too |

Everything else in the graph runs exactly as you built it in the web UI.

Workflow switches: boolean inputs of subgraph instances (the web UI's
on/off widgets — e.g. `switch` for prompt-enhance, `thinking`) show up on
the Advanced tab as switches. Flipping one changes the converted graph
before patching, so prompt targeting follows the newly active branch.
Switch states are per-workflow and reset when you change workflow; they
don't rewrite your saved file.

## Requirements for a server workflow to run

- Every custom node in it must be installed **on the server** — the app ships
  no nodes, it only sequences what the server already has.
- Subgraph nodes (ComfyUI's built-in subgraphs) are supported; the converter
  flattens them using the server's own `object_info`.
- The app must find a sampler to patch CFG/steps into and a text encoder for
  the prompt. A workflow without them still runs — you just can't drive those
  values from the phone.
- Runtime *image input* (img2img, inpaint, ControlNet from a photo) works
  for the standard `LoadImage` node: pick a photo on the Prompt tab, the app
  uploads it to the server on Create and points the node at it. Image nodes
  from custom packs that don't use `LoadImage` still run with the file the
  workflow already references.

## What the app deliberately doesn't do

- **No graph editing.** Build and tweak workflows in the web UI; the phone is
  a runner. This keeps both sides simple and avoids a second, worse node
  editor.
- **No queue reordering / priority.** Pending jobs can be cancelled
  individually or cleared wholesale; running jobs can be interrupted.
- **No multi-server profiles.** One server URL, on purpose.
