This Interactive AI Holograms exhibit uses Oracle AI services such as Speech AI as well as Oracle Database Select AI and Vector Search features, either directly or via the Oracle AI Optimizer and Toolkit.
It takes your spoken questions, conducts vector searches, and I, an Unreal Metahuman, provide the answers verbal and/or via data visualizations.
It is completely open source and available for you to try.
Thanks for checking it out.

## Offline TTS quick notes

- `TTS_ENGINE` controls whether Google Cloud or offline Coqui handles speech. Keep the UI voice picker for Google Cloud; the same `languageCode` and `voiceName` are now also passed into Coqui automatically.
- Coqui now defaults to the Tacotron2-DDC voice for both `BALANCED` and `QUALITY`. To switch models without code changes, set `COQUI_MODEL_BALANCED` or `COQUI_MODEL_QUALITY` (for example `COQUI_MODEL_QUALITY=tts_models/en/ljspeech/glow-tts`).
- Coqui maps U.S. English voices to female speakers by default (it relies on the single-speaker LJSpeech models). You can still pick any Google Cloud female voice (for example `en-US-Wavenet-F` or `Aoede`) and the system will reuse that choice when it falls back to the cloud engine.
- To force the highest Coqui fidelity offline, set `TTS_QUALITY=QUALITY`. Leave it unset or `BALANCED` for faster replies.