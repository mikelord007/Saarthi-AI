# Research Brief: Perception Layer + Task Agent for Android

Context for the coding agent: We are building an Android app with (1) a **perception layer** that understands what's on screen and (2) a **task agent** that uses it to execute multi-step tasks in third-party apps (e.g., "order food on Swiggy" → open app, search, add to cart, checkout). Execution happens **on-device via AccessibilityService** (no ADB, no root, no connected PC). Below is a survey of prior art, organized by relevance, with architecture notes on what to borrow from each.

---

## 0. Start here — required reading order

Before designing anything, clone and read these three codebases in this order. Together they cover the three core subsystems; everything else in this brief is context around them.

1. **Droidrun's portal APK** (https://github.com/droidrun/droidrun) — the AccessibilityService implementation: how to extract the accessibility tree on-device and execute gestures. This is the **perception + actuation** reference.
2. **M3A's agent loop** in the AndroidWorld repo (https://github.com/google-research/android_world) — the prompting, ReAct+Reflexion structure, and structured JSON action schema. This is the **decision loop** reference.
3. **AutoDroid's UI-state serialization** (https://github.com/MobileLLM/AutoDroid, see `droidbot/device_state.py` and `droidbot/input_policy.py` in the underlying DroidBot-GPT) — how to convert a view hierarchy into compact LLM-readable text. This is the **state encoding** reference.

---

## 1. On-device Android agents (AccessibilityService-based) — closest prior art

These run as a single APK on the phone and are the most directly relevant.

### Droidrun / Mobilerun
- Repos: https://github.com/droidrun/droidrun and https://github.com/droidrun/mobilerun
- Site: https://droidrun.ai/
- Architecture: a "Portal" APK on the device exposes the accessibility tree; a Python agent framework (LLM-agnostic: GPT/Claude/Gemini) consumes it. Historically ADB-tethered, moving toward standalone single-APK.
- **Key pattern to steal — hybrid perception:** read the accessibility tree first (structured, cheap, fast: every button, text field, label, checkbox with bounds and text). When the tree is empty or useless (games, WebViews, custom-drawn views), fall back to screenshot + vision model.
- Agent loop per step: send current UI state → LLM returns `think:` + `action:` (launch / tap / type / scroll / swipe). Self-healing: retries, fallback memory, error handling.
- Claims 91.4% on AndroidWorld (their site); earlier benchmark 63% on 116 tasks vs ~30% baselines.

### Panda (Blurr project)
- Open-source Kotlin/Android app. AccessibilityService = "eyes and hands," LLM (Gemini API) = "brain." No root. Supports persistent local memory and voice output.
- Search GitHub for "Blurr Panda Android agent" for the repo.
- Relevant because it is exactly the single-APK, no-PC form factor Handrail needs, including TTS output.

### AgentCPM-GUI (THUNLP / ModelBest)
- Repo: https://github.com/OpenBMB/AgentCPM-GUI
- 8B on-device GUI agent model built on MiniCPM-V. Takes screenshots as input, executes tasks autonomously. Trained with reinforcement fine-tuning; strong GUI grounding from large bilingual Android dataset.
- Relevant if we ever want an on-device or self-hosted perception model instead of cloud VLM calls.

### Other on-device projects worth a skim (GitHub topics: `android-agent`, `mobile-agent`, `android-automation`)
- **AppClaw** — reads screen, reasons, acts; LLM-agnostic, zero telemetry.
- **MobileUse** — hierarchical reflection + proactive exploration; evaluated on AndroidWorld/AndroidLab (NeurIPS 2025).
- **PokeClaw / PocketClaw** — fully on-device with Gemma via LiteRT, no cloud, no API key.
- **Open-AutoGLM, MobiAgent, FIRERPA** — alternative phone-agent frameworks.

---

## 2. ADB-driven research frameworks (PC controls phone)

Not our deployment model, but the best-documented agent architectures. Read these for prompting, memory, and loop design.

### AppAgent (Tencent)
- Repo: https://github.com/TencentQQGYLab/AppAgent
- Multimodal agent (GPT-4V / Qwen-VL-Max) operating smartphone apps via ADB screenshots.
- **Key pattern to steal — two-phase design:** an *exploration phase* where the agent (or a human demo) explores an app and writes documentation of UI elements, then a *deployment phase* that uses that learned knowledge. Also uses numeric tags overlaid on UI elements, and a grid overlay fallback for unlabeled elements.

### Mobile-Agent family (Alibaba X-PLUG) — v1 → v3.5
- Repo: https://github.com/X-PLUG/MobileAgent
- Paper (v3): https://arxiv.org/abs/2508.15144
- **Key pattern to steal — multi-agent role decomposition:** GUI-Owl instantiated as specialized role agents (manager/planner, worker/executor, reflector, notetaker) that coordinate and share observations and reasoning traces for long-horizon tasks.
- GUI-Owl-1.5 model family (2B/4B/8B/32B, Instruct + Thinking variants, built on Qwen3-VL) is open-source SOTA on 20+ benchmarks: OSWorld 56.5, AndroidWorld 71.6, ScreenSpot-Pro 80.3. Weights on ModelScope/HuggingFace; free-tier API via Alibaba Bailian.

### AutoDroid + DroidBot-GPT (Tsinghua MobileLLM)
- Repos: https://github.com/MobileLLM/AutoDroid , https://github.com/MobileLLM/DroidBot-GPT , https://github.com/MobileLLM/AutoDroid-0shot
- Papers: DroidBot-GPT https://arxiv.org/abs/2304.07061 ; AutoDroid "Empowering LLM to use Smartphone for Intelligent Task Automation"
- **Key pattern to steal — text-serialized UI state:** converts the Android view hierarchy to a text list of elements + available actions, prompts the LLM to choose the next action. Two files define the whole customization surface: one for LLM policy, one for UI-state serialization — a clean separation of perception encoding from decision policy.
- AutoDroid adds **app-specific memory injection** (knowledge distilled from offline app exploration, injected into prompts) — big success-rate gains over the zero-shot baseline.
- DroidTask dataset: screenshots + view hierarchies + task traces (Google Cloud link in repo README).

---

## 3. Perception / grounding building blocks

### OmniParser (Microsoft)
- Repo: https://github.com/microsoft/OmniParser ; model: https://huggingface.co/microsoft/OmniParser-v2.0
- Pure-vision screen parser: converts a screenshot into a structured list of elements — interactable region bounding boxes + captions describing each icon's likely function. V2 hits 39.5% on ScreenSpot-Pro grounding.
- OmniTool: OmniParser + any VLM (GPT-4o, Claude, Qwen2.5-VL, DeepSeek) controlling a Windows VM — reference for wiring a parser to a decision model.
- Use case for us: the vision-fallback half of the perception layer, when the accessibility tree is empty.

### OS-Atlas (OS-Copilot)
- Repo: https://github.com/OS-Copilot/OS-Atlas ; paper: https://arxiv.org/abs/2410.23218
- Foundation *grounding* models (4B from InternVL2, 7B from Qwen2-VL). Input: screenshot + instruction; output: normalized 0–1000 coordinates (point or bbox). Designed to be a drop-in grounding module for a planner LLM — GPT-4o + OS-Atlas beat GPT-4o + SeeClick/SoM on OSWorld.
- **Key pattern:** planner model and grounding model as separate components. The planner says *what* to click in natural language; the grounder resolves *where*.

### UI-TARS (ByteDance)
- Repos: https://github.com/bytedance/UI-TARS , https://github.com/bytedance/UI-TARS-desktop
- End-to-end native GUI agent model: screenshot in, human-like actions out — perception, reasoning, grounding, memory unified in one model rather than a prompt-orchestrated pipeline. UI-TARS-1.5-7B open-sourced; UI-TARS-2 covers GUI + game + code + tool use. Provides desktop/mobile prompt templates. Beat GPT-4 on AndroidWorld (46.6 vs 34.5) at release.
- Counterpoint architecture to the pipeline approach — worth understanding both.

### Also relevant
- **SeeClick, UGround, Aria-UI, Aguvis** — other grounding models, surveyed in https://arxiv.org/abs/2505.13227 (OSWorld-G / Jedi: benchmarks + data for computer-use grounding).
- **CogAgent (THUDM)** — early influential VLM GUI agent.

---

## 4. Benchmarks & environments (also the best reference agent code)

### AndroidWorld (Google Research) — most important repo in this section
- Repo: https://github.com/google-research/android_world ; paper: https://arxiv.org/abs/2405.14573
- 116 parameterized tasks across 20 real apps on an emulated Pixel 6; ground-truth rewards read from Android system state via adb (not UI heuristics or LLM judges). Docker support included.
- **Ships full source for the reference agents M3A and T3A.** M3A is a multimodal ReAct + Reflexion agent consuming either the a11y tree or Set-of-Mark annotated screenshots and emitting structured JSON actions. Notable finding: text-only a11y-tree input often matches or beats multimodal input — good news for an a11y-first design.
- Use: (a) read M3A/T3A source as the canonical observe→think→act loop; (b) evaluate our agent against it.

### Others
- **AndroidLab** — alternative Android agent benchmark.
- **MobileGym** — browser-hosted Android simulator; parallel, verifiable eval + online RL training.
- **LlamaTouch AgentEnv** — hosts AutoDroid and other agent implementations: https://github.com/LlamaTouch/AutoDroid
- **FingerTip-20K** — proactive/personalized mobile-agent benchmark: https://github.com/tsinghua-fib-lab/FingerTip-20K
- **AndroidWorld leaderboard**: https://google-research.github.io/android_world/

---

## 5. Non-Android computer-use agents (architecture transfer)

- **Anthropic computer-use reference implementation** — Docker-based demo in the anthropic-quickstarts GitHub repo; the canonical screenshot→coordinate-action loop with a defined tool/action schema. Good template for action-space design and the driver code between model and device.
- **browser-use** — https://github.com/browser-use/browser-use — leading web agent. Its perception layer extracts the DOM into an indexed element list (the web analog of the a11y tree), with ongoing hybrid-vision work. Study its element-indexing, action schema, and failure handling.
- **Agent-S / Agent S3 (Simular)** — https://github.com/simular-ai/Agent-S — open framework pairing a frontier planner (e.g., GPT-5) with a local grounding model (UI-TARS-1.5-7B). Clean example of the planner/grounder split with configurable grounding endpoints.
- **UI-TARS Desktop / Agent TARS** — Electron GUI-agent app: screen capture, grounding, safe input control, event streaming, MCP tool integration, ADB operator for Android experiments.
- **OmniTool (Microsoft)** — OmniParser + pluggable VLM controlling a Windows 11 VM.

---

## 6. Surveys & curated lists (feed these for breadth)

- **Awesome Mobile Agents** — https://github.com/aialt/awesome-mobile-agents — papers + datasets for mobile and PC GUI agents, with a comparison table of methods (input modality, model, training).
- **"GUI Agents with Foundation Models: A Comprehensive Survey"** — https://arxiv.org/abs/2411.04890
- **"Large Language Model-Brained GUI Agents: A Survey"** — https://arxiv.org/abs/2411.18279 — huge tables of projects with platform / perception / action columns.

---

## 7. Architecture synthesis — recommended design distilled from the above

**Perception layer (two tiers):**
1. **Tier 1 — Accessibility tree (default).** AccessibilityService `AccessibilityNodeInfo` traversal → serialize to a compact indexed element list (id, role, text/content-desc, bounds, clickable/scrollable/editable flags). This is DroidBot-GPT/AutoDroid/Droidrun's approach and what M3A's text mode consumes. Cheap, fast, structured, and text-only input performs surprisingly well on AndroidWorld.
2. **Tier 2 — Vision fallback.** Screenshot (MediaProjection or AccessibilityService.takeScreenshot) → either (a) Set-of-Mark: overlay numeric tags on Tier-1 bounds and send the annotated image, or (b) when the tree is empty (WebViews, Flutter/custom canvases, games): OmniParser-style parsing or a grounding model (OS-Atlas class) or a VLM with coordinate output.

**Task agent loop (M3A/Droidrun pattern):**
- ReAct-style: observation → reasoning ("think") → single structured JSON action → execute via AccessibilityService gestures (tap/swipe/type via `dispatchGesture` + `ACTION_SET_TEXT`) → new observation.
- Add Reflexion: after each action, compare before/after state; on no-change or error, reflect and retry differently (Droidrun's self-healing, MobileUse's hierarchical reflection).
- Bounded steps per task; explicit `task_complete` / `task_infeasible` actions.

**Action space (keep small and structured):**
`launch_app(name)`, `tap(element_index | x,y)`, `long_press`, `type(text)`, `scroll(direction)`, `swipe`, `back`, `home`, `wait`, `ask_user(question)`, `done(result)`.

**Memory (AutoDroid/AppAgent pattern):**
- App-specific knowledge store: learned notes about app flows ("in Swiggy, checkout button appears after cart page scroll") injected into prompts. Can be seeded by an offline exploration phase per app.
- Per-task scratchpad: running summary of steps taken + key facts (order details, prices) so long tasks don't overflow context.

**Optional planner/grounder split (Agent-S/OS-Atlas pattern):**
- If a single LLM struggles with coordinates, split: big model plans in natural language, small grounding model resolves element locations. With a11y-tree input this is usually unnecessary (element indices sidestep grounding entirely) — reserve for vision-fallback screens.

**Safety/confirmation:**
- Anything irreversible (payment, sending messages, deleting) → pause and confirm with the user before acting. All ADB-era frameworks warn about unintended actions; on-device with real accounts this is mandatory.

**Evaluation:**
- Wire the agent into AndroidWorld (or a subset of its tasks) early to get an objective success-rate signal and compare directly against M3A/T3A and Droidrun's published numbers.
