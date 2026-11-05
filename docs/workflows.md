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
| Prompt | The positive `CLIPTextEncode` text input (found by walking conditioning links) |
| Size / batch | `EmptyLatentImage` width/height/batch_size |
| Seed | `KSampler` seed / `KSamplerAdvanced` & `RandomNoise` noise_seed |
| CFG / steps | `cfg` and `steps` on every `KSampler`/`KSamplerAdvanced` (cfg only for `CFGGuider`); `0` = leave untouched |

Everything else in the graph runs exactly as you built it in the web UI.

## Requirements for a server workflow to run

- Every custom node in it must be installed **on the server** — the app ships
  no nodes, it only sequences what the server already has.
- Subgraph nodes (ComfyUI's built-in subgraphs) are supported; the converter
  flattens them using the server's own `object_info`.
- The app must find a sampler to patch CFG/steps into and a text encoder for
  the prompt. A workflow without them still runs — you just can't drive those
  values from the phone.
- Workflows that need a runtime *image input* (img2img, inpaint, ControlNet
  from a photo) can't be fed from the phone yet — there's no upload path.
  Those nodes will keep whatever file the workflow already references.

## What the app deliberately doesn't do

- **No graph editing.** Build and tweak workflows in the web UI; the phone is
  a runner. This keeps both sides simple and avoids a second, worse node
  editor.
- **No queue reordering / priority.** Pending jobs can be cancelled
  individually or cleared wholesale; running jobs can be interrupted.
- **No multi-server profiles.** One server URL, on purpose.
