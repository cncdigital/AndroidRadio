# Optional audio effects

The phone's **Mejoras de audio** panel is off by default. Enabling it selects a modest equalizer profile; bass and stereo width are separate opt-ins. Settings persist. **Audio original** restores bypass defaults.

- Equalizer uses the device's actual bands and supported level range: up to +2 dB below 180 Hz, −1 dB between 500 and 2500 Hz, and +1 dB above 5000 Hz.
- BassBoost and Virtualizer require adjustable strength and use 150/1000. Unsupported engines show unavailable instead of fake controls.
- Virtualizer is optional/deprecated platform functionality, not universal spatial audio. Actual activation is checked, including during playback. Its effect depends on output hardware and source material.
- Active effects apply a 0.5 music-level margin (approximately −6 dB). Announcements independently multiply music level by 0.4. Disabling keeps the existing margin until effects are disabled, then restores normal level.
- This is not a limiter and cannot guarantee that vendor processing never clips. It does not restore missing source quality or create physical sub-bass hardware.
- Processing applies to `song:` items only. Commercials and the independent voice player remain unprocessed. Karaoke suspends stereo width and rebuilds session-bound effects safely.
- Each engine fails independently. Losing effect control or a usable output releases only that engine. There is no retry loop. **Comprobar salida** retries the current session, including after switching between outputs that were already connected.
- No microphone permission or Android Auto driving UI changes. No changes to global system spatialization or output volume.

## Verification

Unit tests cover bypass defaults, volume composition, safe disable ordering, invalid/global session rejection, same-session reuse, rebuild/release, route reprobe, individual unsupported/failed engines, control loss, late callbacks, same-session route loss/recovery, commercial bypass and karaoke behavior. An instrumented test covers persisted preferences, default/reset behavior and malformed preference fallback.

Hardware listening and Android Auto checks remain necessary: test real wired/Bluetooth headphones and speaker, switch already-connected outputs, repeatedly enable/disable effects, toggle karaoke, enter/exit background, and play voice/commercial transitions. Start at a low comfortable volume. A successful build/unit suite is not evidence of an audible quality improvement or instrumented/device test execution.
