# The optional integrations are not ported

`JadeIntegration` and `ModMenuIntegration` exist in `mc262` and deliberately do
not exist here yet.

Both are compile-only against artifacts pinned to 26.2 — Jade comes from a jar
in `mc262/libs/` that no public maven serves for 26.2, and ModMenu is
`com.terraformersmc:modmenu:20.0.1`, which is a 26.2 build. Porting them means
choosing the 1.21.1 releases of both and checking their APIs, which is real work
for two features that are inert when the mods are absent.

Neither is load-bearing. Jade's integration hides its tooltip during a duel;
ModMenu's supplies the config screen's front door. The loader stores an
entrypoint as a string and only classloads it on demand, so a missing ModMenu
entrypoint is inert rather than an error — exactly as it is for a player who
does not have ModMenu installed.

Bring them back by porting the two files from `mc262`, adding the 1.21.1
coordinates as `modCompileOnly` — not `compileOnly`; on an obfuscated version a
mod dependency has to be remapped — and re-adding the `modmenu` entrypoint to
`fabric.mod.json`.
