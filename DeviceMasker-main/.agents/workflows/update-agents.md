---
description: Audit and update AGENTS.md files
---

Update only these guides: `AGENTS.md`, `app/AGENTS.md`, `common/AGENTS.md`, `xposed/AGENTS.md`.

Rules:
- Read `memory-bank/`, `docs/public/ARCHITECTURE.md`, existing guides, and `graphify-out/GRAPH_REPORT.md` when present.
- For Xposed/libxposed claims, read `.agents/skills/libxposed/SKILL.md` first.
- Change only critical stale/unsafe/misleading guidance; do not rewrite for style.
- Keep root `AGENTS.md` permanent-rule focused; keep detailed/current state in Memory Bank or docs.
- Preserve project trees, but make folder notes purpose-based, not count-based.
- Never allow AIDL as config delivery, runtime ID generation in `:xposed`, Timber/Compose in `:xposed`, hardcoded prefs keys, or unsafe direct `.intercept { ... }` examples.
- Never claim hook success from app launch/build/service connection alone; require LSPosed/logcat hook evidence and real spoof values when possible.
- Keep logs/evidence under `logs/`: build in `logs/build/`, device in `logs/device/`, scratch in `logs/tmp/`.

Verify example:
```powershell
git diff --check -- AGENTS.md app\AGENTS.md common\AGENTS.md xposed\AGENTS.md
Select-String -Path AGENTS.md,app\AGENTS.md,common\AGENTS.md,xposed\AGENTS.md -Pattern 'AGENTS1.md','isMinifyEnabled = false','R8 breaks','AIDL config channel','AIDL delivers spoof config','xi\.hook\(m\)\.intercept \{','YukiHookAPI','docs/reports','11 hookers','test files','tests pass proves hooks'
```

Report changed files, stale issues fixed, and verification results.
