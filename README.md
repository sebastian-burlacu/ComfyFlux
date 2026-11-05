<a id="readme-top"></a>

[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![Apache License][license-shield]][license-url]

<div align="center">
  <a href="https://github.com/sebastian-burlacu/ComfyFlux">
    <img src="app/src/main/res/mipmap-hdpi/ic_launcher_round.webp" alt="Logo" width="80" height="80">
  </a>

  <h3 align="center">ComfyFlux</h3>

  <p align="center">
    A native Android client for ComfyUI.
    <br />
    <a href="https://github.com/sebastian-burlacu/ComfyFlux/releases/latest"><strong>Download the latest APK</strong></a>
  </p>
</div>

## About

ComfyUI usually runs on the one machine in the house with enough GPU to be worth
using, and that machine is rarely the one in your hand. ComfyFlux connects to a
ComfyUI server on your local network and gives you a focused mobile front end
for the parts that matter on a phone: pick a workflow, set the prompt, size and
seed, queue the job, and review the results.

This is a maintained fork of the original [ComfyFlux](https://github.com/eltonkola/ComfyFlux)
by Elton Kola. The fork focuses on reliability against current ComfyUI servers
(current node graphs, workflow handling, connection management, signed release
builds).

### Features

- Connects to any ComfyUI server on the local network (`--listen`), with clear
  diagnostics when it cannot
- Runs workflows from the phone: size, batch, prompt and seed are patched into
  the workflow before queueing
- Saved server workflows from the ComfyUI web UI, fetched and run directly
- Queue screen with per-job cancel and a one-tap "Clear queue" that
  drops all pending jobs while leaving the running one alone
- History view with the generated images, saved locally in the app;
  any past generation can be re-run with its exact original settings
- Prompt database with local search to reuse prompts quickly
- In-app logs (Settings -> Logs): server-reported failures like failed
  executions and rejected prompts, plus client-side connection, timeout
  and conversion errors; filterable to errors, clearable
- Notifications when a generation finishes or fails while the app is in
  the background; failed generations get a "Retry with same settings"
  button on the Create screen; "Seed +1" reroll for iterating on a
  result without reshuffling everything else
- No account required. The app talks to your own server; the only optional
  remote call is Groq-powered prompt enrichment, off until you add a key

## Getting Started

1. Run ComfyUI so it listens on the local network:
   ```
   python main.py --listen
   ```
2. Install the [latest release](https://github.com/sebastian-burlacu/ComfyFlux/releases/latest)
   on your phone and make sure it is on the same network as the server.
3. Enter the server address in the app, for example `http://192.168.1.5:8188`
   (the port ComfyUI prints at startup). A working address is remembered.
4. Pick a workflow, set the prompt, press generate.

### Workflows

The app ships with tested local image workflows, and it also lists the
workflows you save in the ComfyUI web UI: they are fetched from the server,
converted to the API format the queue endpoint expects (including subgraphs),
and patched with your prompt, size and seed before queueing.

## Built With

* [![Kotlin][kotlin-shield]][kotlin-url]
* [![Jetpack Compose][compose-shield]][compose-url]
* [![Ktor][ktor-shield]][ktor-url]
* [![Coil][coil-shield]][coil-url]

## Contributing

Issues and pull requests are welcome. Fork the repo, create a branch, and open
a pull request.

## License

Distributed under the Apache License 2.0. See `LICENSE` for more information.

## Acknowledgments

* [ComfyUI](https://github.com/comfyanonymous/ComfyUI) — the server this app drives
* [ComfyFlux](https://github.com/eltonkola/ComfyFlux) — the original project this fork builds on
* [Stable Diffusion Prompts](https://huggingface.co/datasets/Gustavosta/Stable-Diffusion-Prompts) — prompt dataset
* [Composables Icons](https://www.composables.com/icons)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- MARKDOWN LINKS & IMAGES -->
[forks-shield]: https://img.shields.io/github/forks/sebastian-burlacu/ComfyFlux.svg?style=for-the-badge
[forks-url]: https://github.com/sebastian-burlacu/ComfyFlux/network/members
[stars-shield]: https://img.shields.io/github/stars/sebastian-burlacu/ComfyFlux.svg?style=for-the-badge
[stars-url]: https://github.com/sebastian-burlacu/ComfyFlux/stargazers
[issues-shield]: https://img.shields.io/github/issues/sebastian-burlacu/ComfyFlux.svg?style=for-the-badge
[issues-url]: https://github.com/sebastian-burlacu/ComfyFlux/issues
[license-shield]: https://img.shields.io/github/license/sebastian-burlacu/ComfyFlux.svg?style=for-the-badge
[license-url]: https://github.com/sebastian-burlacu/ComfyFlux/blob/main/LICENSE

[kotlin-shield]: https://img.shields.io/badge/Kotlin-7F52FF.svg?style=for-the-badge&logo=Kotlin&logoColor=white
[kotlin-url]: https://kotlinlang.org/
[compose-shield]: https://img.shields.io/badge/Jetpack%20Compose-4285F4.svg?style=for-the-badge&logo=Jetpack-Compose&logoColor=white
[compose-url]: https://developer.android.com/compose
[ktor-shield]: https://img.shields.io/badge/Ktor-087CFA.svg?style=for-the-badge&logo=Ktor&logoColor=white
[ktor-url]: https://ktor.io/
[coil-shield]: https://img.shields.io/badge/Coil-000000.svg?style=for-the-badge&logo=Coil&logoColor=white
[coil-url]: https://github.com/coil-kt/coil
