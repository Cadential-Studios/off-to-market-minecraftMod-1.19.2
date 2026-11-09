# Off To Market — Forge → Fabric 1.20.1 Port Audit (Phase 1)

Audited: 2026-10-31/11-01, against `ScottyVenable/off-to-market-minecraftMod-1.19.2`
at commit `151fb97` (v0.6.7). Read-only audit via the GitHub connector; no
Forge-repo files were touched. Forge baseline: MC 1.19.2, Forge 43.5.0,
ForgeGradle `[6.0,6.2)`, official (Mojmap) mappings, Java 17.
Codebase: **129 Java files, 77 resource files**.

Target: **Fabric, MC 1.20.1, Java 17, Fabric Loom, Mojang (official)
mappings** — see §10 for the mappings decision.

---

## 1. Entry points & registration style

**Forge today**
- Main class `com.offtomarket.mod.OffToMarket`, `@Mod("offtomarket")`.
  Constructor grabs the mod event bus and registers four
  `DeferredRegister`s (blocks, items, block entities, menu types), adds an
  `FMLCommonSetupEvent` listener, registers itself on
  `MinecraftForge.EVENT_BUS`, and registers one COMMON `ForgeConfigSpec`.
- `commonSetup` (inside `enqueueWork`): `ModNetwork.register()`,
  `ModdedItemConfig.loadAllConfigs()`, `CustomPriceConfig.loadAll()`,
  `TownLoader.loadAll()`, `CustomMenuLoader.loadAll()`,
  `ModCompatibility.initialize()` (runtime scan of other mods' items →
  dynamic towns).
- Client init is a separate `@Mod.EventBusSubscriber(Dist.CLIENT)` class
  (`event/ClientEvents`) on `FMLClientSetupEvent` → `MenuScreens.register` ×6.
- Registration style: classic `DeferredRegister` + `RegistryObject`, items
  take `.tab(ModCreativeTab.TAB)` (a `CreativeModeTab` subclass whose icon is
  the gold coin).

**Fabric 1.20.1 equivalent**
- `ModInitializer` (`onInitialize`) + `ClientModInitializer`
  (`onInitializeClient`) entrypoints declared in `fabric.mod.json`. No event
  bus, no deferred registration: plain `Registry.register(BuiltInRegistries.X,
  new ResourceLocation("offtomarket", name), thing)` calls from static init /
  explicit `register()` methods called in `onInitialize`.
- Creative tab: in 1.20.1 vanilla, `CreativeModeTab` is itself a registry —
  register one via `BuiltInRegistries.CREATIVE_MODE_TAB` with
  `CreativeModeTab.builder(...)` (icon = gold coin stack, `displayItems`
  adds every item). This preserves the Forge tab exactly.
- Content/config loading moves from `FMLCommonSetupEvent.enqueueWork` to
  `onInitialize` (file configs) + `ServerLifecycleEvents.SERVER_STARTING` /
  datapack reload listeners (town & menu JSON — see §8).

## 2. Content inventory (what has to be ported)

| Kind | Count | Names |
|---|---|---|
| Blocks | 5 | `trading_post`, `trading_ledger` (renamed from `trading_bin`), `market_board`, `mailbox`, `finance_table` |
| Block entities | 5 | one per block, 1:1 |
| Items (registered) | 13 | 5 block items + `copper_coin`, `silver_coin`, `gold_coin`, `ledger`, `guide_book`, `coin_bag`, `animal_trade_slip`, `route_marker` |
| Menu types | 6 | `trading_post`, `trading_ledger`, `market_board`, `coin_bag`, `mailbox`, `finance_table` |
| Screens | 6 menu-bound + 4 free-standing | menu: TradingPost, TradingLedger, MarketBoard, CoinBag, Mailbox, FinanceTable; free-standing: CoinExchangeScreen, GuideBookScreen, MasterLedgerScreen, CustomMenuScreen (+ `OtmGuiTheme`, `CoinRenderer`, toast/depot/route client renderers) |
| Sound events | 15 defined | `coins_collect`, `coins_jingle`, `shipment_send`, `shipment_arrive`, `item_sold`, `quest_accept`, `quest_complete`, `worker_hire`, `worker_level_up`, `diplomat_send`, `diplomat_return`, `diplomat_proposal`, `ui_click`, `ui_tab_switch`, `notification`, `trader_level_up` — **⚠ the `ModSounds` DeferredRegister is never registered** (no call site exists; the main class doesn't call `ModSounds.register`). On Forge today these sounds silently don't exist as registry entries. Fabric port should register them for real. |
| Data/logic classes | 23 in `data/` | pricing, towns, shipments, quests, workers, diplomats, mail, market climate, supply/demand — loader-agnostic Java except where noted below |
| Debug | 4 classes + commands | `DebugCommands` (Brigadier via `RegisterCommandsEvent`), `DebugOverlay` (Forge overlay), `DebugHooks`, `DebugConfig` |

**Fabric equivalents**
- Blocks/block entities: vanilla `Block`/`BlockEntity` + `BlockEntityType.Builder`
  — the Forge code already uses vanilla patterns (`BlockBehaviour.Properties.of(Material.WOOD)`
  → in 1.20.1 Fabric/Mojmap: `Properties.of().mapColor(...)` — note `Material`
  was removed in 1.20.x in favour of `MapColor`/settings; each block's
  properties need a small translation, values preserved).
- Menus: Forge uses `IForgeMenuType.create((windowId, inv, data) -> …)`
  reading a `BlockPos` (or a slot int for the coin bag) from the extra-data
  buffer. Fabric equivalent: **extended screen handlers** — Fabric API's
  `ExtendedScreenHandlerType` (1.20.1) whose factory receives the same buffer;
  server opens via `ExtendedScreenHandlerFactory`. This is a clean 1:1.

## 3. Networking — the biggest single work package

**Forge today**: one `SimpleChannel` (`offtomarket:main`, protocol "1"),
**43 registered messages** (sequential IDs 0–42 in `ModNetwork.register()`),
each a class with static `encode`/`decode`/`handle`, handlers running via
`ctx.get().enqueueWork(...)` (all 43 do). Direction split: **39 C2S, 4 S2C**.

| # | Packet | Dir | # | Packet | Dir |
|---|---|---|---|---|---|
| 0 | SendShipmentPacket | C2S | 22 | CancelShipmentPacket | C2S |
| 1 | SelectTownPacket | C2S | 23 | CollectReturnedItemsPacket | C2S |
| 2 | CollectCoinsPacket | C2S | 24 | AdjustShipmentPricePacket | C2S |
| 3 | RequestReturnPacket | C2S | 25 | CreateRequestPacket | C2S |
| 4 | SetDistancePacket | C2S | 26 | ShowToastPacket | **S2C** |
| 5 | SetPricePacket | C2S | 27 | MarkNoteReadPacket | C2S |
| 6 | RefreshMarketPacket | C2S | 28 | DeleteNotePacket | C2S |
| 7 | CoinExchangePacket | C2S | 29 | DeleteAllReadNotesPacket | C2S |
| 8 | BuyMarketItemPacket | C2S | 30 | WithdrawBinItemPacket | C2S |
| 9 | CartCheckoutPacket | C2S | 31 | UpgradeCaravanWeightPacket | C2S |
| 10 | CollectOrderPacket | C2S | 32 | OpenCustomMenuPacket | **S2C** |
| 11 | CoinSlotConvertPacket | C2S | 33 | DepositCoinsPacket | C2S |
| 12 | AcceptQuestPacket | C2S | 34 | WithdrawCoinsPacket | C2S |
| 13 | DeliverQuestPacket | C2S | 35 | WithdrawAmountPacket | C2S |
| 14 | HireWorkerPacket | C2S | 36 | DepositAmountPacket | C2S |
| 15 | FireWorkerPacket | C2S | 37 | DispatchScoutPacket | C2S |
| 16 | SendDiplomatPacket | C2S | 38 | SyncDepotContainersPacket | **S2C** |
| 17 | CollectDiplomatPacket | C2S | 39 | CreateCustomOrderPacket | C2S |
| 18 | AcceptDiplomatPacket | C2S | 40 | CancelCustomOrderPacket | C2S |
| 19 | DeclineDiplomatPacket | C2S | 41 | DispatchCustomOrderPacket | C2S |
| 20 | UpdateBinSettingsPacket | C2S | 42 | CartCheckoutResultPacket | **S2C** |
| 21 | CollectShipmentCoinsPacket | C2S | | | |

(44 packet classes exist in `network/`; `ModNetwork` + 43 registered — one
class file is an extra/legacy file beyond the registered set as read at
v0.6.7; the port should reconcile the exact set against the live tree at
port time, since Antigravity is actively shipping.)

**Fabric 1.20.1 equivalent**
- `ServerPlayNetworking.registerGlobalReceiver(ResourceLocation, handler)` for
  C2S and `ClientPlayNetworking` for S2C — one channel ID per packet
  (`offtomarket:<name>`), replacing numeric IDs. (1.20.1 predates the 1.20.5
  `CustomPacketPayload` system; do **not** design for payloads.)
- `encode`/`decode` bodies port almost verbatim (`FriendlyByteBuf` is the
  same class); `handle` bodies port with `ctx.get().enqueueWork` →
  `server.execute(...)` / context executor, and sender validation
  (open menu, distance, ownership) must be re-checked per handler — the Forge
  handlers already do this; keep the checks, change the plumbing.
- Menu state sync that Forge does via packets stays packets; scalar sync via
  `ContainerData` is loader-neutral already.

## 4. Persistence — better news than expected

**Forge today** (three stores, **no capabilities in use** —
`capability/ModCapabilities.java` is an empty placeholder whose own javadoc
says all state lives on the Trading Post block entity):

1. **`TradingData extends SavedData`** — world-level store, name
   `offtomarket_trading`, obtained via
   `level.getDataStorage().computeIfAbsent(...)` on the (overworld)
   `ServerLevel`. Holds a **shared-state `CompoundTag` blob** (trader level/XP,
   shipments, quests, workers, diplomats, market listings, economy stats,
   town inventories — block entities serialise their shared slice into it), a
   `TownDepotManager`, the set of registered Trading Post positions, and a
   last-ticked game time.
2. **Block-entity NBT** — all 5 BEs use vanilla `saveAdditional`/`load` +
   `getUpdateTag`/`ClientboundBlockEntityDataPacket` sync. Inventories use
   vanilla `Container`/`ContainerHelper` (the Trading Ledger BE implements
   `Container` directly and virtualises adjacent-container contents) — **not**
   Forge's `ItemStackHandler`/capabilities.
3. **Item NBT** — coin bag contents, ledger/slip state ride in item stack
   tags (loader-neutral).

**Fabric 1.20.1 equivalent**
- `SavedData` + `DimensionDataStorage.computeIfAbsent` exist identically in
  vanilla 1.20.1 — `TradingData` ports nearly unchanged (class is in the
  `data/` package and is already vanilla-API code).
- BE NBT/`getUpdateTag` patterns are vanilla and identical. Adjacent-container
  scanning uses vanilla `Container` — identical.
- **No attachment system needed.** (Fabric's Cardinal Components / Fabric API
  data attachments would be the fallback if capabilities had been real —
  they're not.) This removes the single scariest porting unknown.

## 5. Config

**Forge today**: `ModConfig` — one COMMON `ForgeConfigSpec`, **45 defined
values** in 5 sections (timing 4, pricing 6, currency 3, leveling 4, quests
2+, plus worker/economy sections below the part read line-by-line), baked
into `public static` fields on `ModConfigEvent`. File:
`config/offtomarket-common.toml`. Plus two hand-rolled JSON config loaders:
`CustomPriceConfig` (custom_prices) and `ModdedItemConfig`, plus
`DebugConfig`, all read from the config dir at setup.

**Decision: ForgeConfigAPIPort (FCAP) for the spec, hand-rolled loaders
stay hand-rolled.**
- Reasoning: the entire codebase reads config as `ModConfig.<staticField>`
  in dozens of places (e.g. `CoinType.formatValue` reads
  `ModConfig.goldOnlyMode` directly). FCAP runs real `ForgeConfigSpec` code
  on Fabric, produces the **same `offtomarket-common.toml` file with the
  same keys**, and requires zero call-site changes beyond the
  `ModConfigEvent` wiring (FCAP fires its own config events / the statics
  can be baked on server start). Cloth Config + a JSON/Cloth schema would
  be the "native Fabric" answer but forces a rewrite of every config read
  and changes the on-disk format players/pack makers already use — rejected
  for Phase 1; revisit only if FCAP's 1.20.1 build proves unmaintained at
  port time (it has a 1.20.1 release line via the Fuzs ecosystem).
- The JSON loaders (`CustomPriceConfig`, `ModdedItemConfig`) are plain
  file/Gson code — loader-neutral, port as-is; only their *trigger* changes
  (see §8).

## 6. Events used (Forge → Fabric)

| Forge | Where | Fabric 1.20.1 equivalent |
|---|---|---|
| `FMLCommonSetupEvent` | network + loaders + mod-compat scan | `ModInitializer.onInitialize` (+ `SERVER_STARTING` for scan-after-registries) |
| `FMLClientSetupEvent` | screen registration | `ClientModInitializer` + `MenuScreens.register` (exists on Fabric as vanilla client class usage via Fabric API's screen handler registration — in practice `MenuScreens.register` is called directly in client init) |
| `TickEvent.ServerTickEvent` (END) | `SupplyDemandManager.onServerTick`, `DebugHooks.onServerTick` | Fabric API `ServerTickEvents.END_SERVER_TICK` |
| `ServerStoppingEvent` | reset static managers | `ServerLifecycleEvents.SERVER_STOPPING` |
| `RegisterCommandsEvent` | `/otm` debug commands (Brigadier) | Fabric API `CommandRegistrationCallback` — command bodies are Brigadier already |
| `ModConfigEvent` | bake config statics | FCAP config event / bake on `SERVER_STARTING` |
| `MissingMappingsEvent` | remaps legacy `trading_bin` → `trading_ledger` for old worlds | **No Fabric equivalent.** Fabric has no missing-mapping remap hook. Mitigation: none needed for new Fabric worlds; Forge-world migration is out of scope (see Risks) |
| `@Mod.EventBusSubscriber` classes | `ModConfig`, `ClientEvents` | plain registration calls in entrypoints |

No `AttachCapabilitiesEvent`, no registry events beyond DeferredRegister,
no player/login events found in the main flow — the event surface is small
and maps cleanly.

## 7. Client-only code

- 6 menu screens + 4 free-standing screens (`CoinExchangeScreen`,
  `GuideBookScreen`, `MasterLedgerScreen`, `CustomMenuScreen`), theme helper
  `OtmGuiTheme`, `CoinRenderer`.
- World/UI rendering: `client/render/RouteMarkerRenderer` +
  `ClientDepotTracker` (depot/route visuals driven by
  `SyncDepotContainersPacket`), `client/NotificationToastRenderer`,
  `debug/DebugOverlay` (Forge render overlay → Fabric API
  `HudRenderCallback`).
- Dist discipline on Forge is via the `Dist.CLIENT` subscriber class;
  Fabric enforces it structurally (client entrypoint + `environment:
  client` on the client class in `fabric.mod.json`). Everything under
  `client/` and `event/ClientEvents` stays client-side; `data/` classes
  referenced by screens must be checked for client-class leakage during
  the screen port (Phase 5 risk item).

## 8. Data files & "datagen"

**There is no datagen.** Zero `GatherDataEvent`/providers; no
`src/generated`. All 77 resource files are hand-authored and port as-is:

- 13 recipes (`data/offtomarket/recipes/`, incl. the 4 coin-conversion
  recipes), 4 block loot tables, 4 blockstates, 16 model files, 21 textures,
  `en_us.json`, vanilla axe-mineable tag override.
- **Town data**: 9 handcrafted towns as datapack JSON
  (`data/offtomarket/towns/*.json` + `_index`/`_template`) loaded by
  `TownLoader`; **custom menus** likewise (`data/offtomarket/custom_menus/`).
  Forge loads them at common setup. Fabric equivalent: implement both as
  `SimpleJsonResourceReloadListener`s (Fabric API resource-loader) so
  `/reload` works and datapacks can override — a small, principled upgrade,
  not a behaviour change.
- Audit catches to fix during the port: `finance_table` has **no blockstate
  and no loot table** in the tree at v0.6.7 (the other 4 blocks have both);
  `ModSounds` never registered (§2).
- JSON formats are loader-neutral with one exception class: any
  `forge:mod_loaded`-style conditions in data files would need swapping for
  Fabric's `fabric:all_mods_loaded` equivalents — none were spotted in the
  listed files, but a grep pass over the JSONs is a Phase-2 checklist item.
  The dynamic mod-compat towns are generated by *runtime item scanning*
  (`ModCompatibility` iterates other mods' registered items) — on Fabric the
  scan reads `BuiltInRegistries.ITEM` the same way; categorisation
  heuristics are loader-neutral.

## 9. JEI / REI

**Neither is integrated today.** `build.gradle` contains only the stock MDK
*commented-out* JEI lines; no plugin class exists. (The skill file's
"optional JEI/REI" is aspirational, matching the CurseForge roadmap.)
Fabric plan: skip for the port's core phases; when added, **REI** is the
native Fabric choice (Architectury + Cloth Config present in most Fabric
packs), JEI also ships a Fabric 1.20.1 build — decide at that phase, not now.

## 10. Key decisions

1. **Mappings: Mojang official (Mojmap), via `loom.officialMojangMappings()`.**
   The entire Forge codebase is Mojmap; porting to Yarn would rename every
   Minecraft class/method/field and triple the diff noise. Cost: less
   alignment with Fabric-community docs (mostly Yarn) — acceptable, and
   Loom + Fabric API fully support Mojmap projects.
2. **Same modid `offtomarket` on Fabric.** Required for registry IDs, data
   files, network channel, and saved-data name to line up 1:1. Coexistence
   implications: the Forge and Fabric jars can never be installed together
   (same modid, duplicate registry IDs — and loaders are mutually exclusive
   anyway); CurseForge/Modrinth listings must mark loader clearly per file;
   world saves are **not** cross-loader portable in practice (block/entity
   registry identity would match, but `TradingData` + BE NBT were written
   by 1.19.2 code and 1.20.1 changes chunk/data formats — treat saves as
   non-transferable; new worlds for the Fabric edition).
3. **Config: ForgeConfigAPIPort** (§5).
4. **Networking: per-packet channel IDs** (§3), no numeric-ID compatibility
   needed (different loader, different protocol peers).

## 11. Phased port plan (each phase compiles & runs before the next starts)

- **Phase 1 (this deliverable)** — audit + Loom skeleton + coins proof.
- **Phase 2 — Core registries & data**: all 13 items, 5 blocks, 5 BEs,
  sounds registered properly, creative tab, all hand-authored resources
  copied, town/menu reload listeners, `TradingData` + depot manager,
  config via FCAP. Exit: dedicated server starts, blocks place, BEs tick.
- **Phase 3 — Networking**: all 43 packets re-plumbed (channels, execute-on-
  server, validation preserved), depot sync + toast + custom-menu S2C.
  Exit: two-client LAN test of a full shipment round-trip.
- **Phase 4 — Menus & economy logic**: 6 menus + `data/` economy (pricing,
  supply/demand tick, quests, workers, diplomats, mail) wired end-to-end.
  Exit: in-game trade loop (buy/sell/ship/collect) on a dedicated server.
- **Phase 5 — Client**: 10 screens, renderers, overlay, toasts. Exit:
  screenshot pass of every screen (fleet rule: SEE what you build).
- **Phase 6 — Polish & parity**: debug commands, missing-mapping note,
  finance_table blockstate/loot-table gaps, JEI/REI decision, GameTests
  for coin conversion + shipment lifecycle, release packaging
  (`OffToMarketFabric-v0.1-alpha` naming per fleet convention).

## 12. Risks (ranked)

1. **A moving target**: Antigravity is shipping Forge changes nightly
   (v0.6.5→0.6.7 in one evening). Any port snapshot goes stale; the port
   must pin a Forge commit per phase and re-diff, or it will silently
   diverge from the "real" mod. Process risk, not code risk — and the
   biggest one.
2. **Trading Post shared-state architecture**: the global `TradingData`
   CompoundTag-blob sync (every post pushes shared state; BEs serialise
   slices into it) is intricate, order-sensitive code concentrated in a
   ~1200-line `TradingPostBlockEntity`. It ports (vanilla APIs) but is the
   easiest place to introduce economy-breaking bugs; Phase 4 needs the
   shipment-lifecycle GameTests *before* behaviour sign-off, plus the
   Shipment-Note-class dupe regression mindset applied to every packet.
3. **1.19.2 → 1.20.1 vanilla drift**: `Material` removal (block props),
   sign/menu/render changes, `CreativeModeTab` becoming a registry — each
   is small, but there will be dozens of them across 129 files; budget for
   a long tail of compile-fix cycles in Phases 2 and 5. No single item is
   a blocker; the aggregate is the schedule risk.
4. **FCAP dependency**: ForgeConfigAPIPort is a third-party port of Forge
   code; if its 1.20.1 build lags or misbehaves, fallback is a small
   hand-rolled TOML reader that bakes the same statics (the spec is only
   45 values) — contained, but a real fork in the road to decide in
   Phase 2's first day.
5. **No missing-mappings equivalent** (§6): pre-rename `trading_bin`
   worlds can't be auto-remapped on Fabric. Accepted (new worlds), but
   worth one line in the Fabric edition's eventual README so nobody
   expects Forge-save migration.

## 13. Dual-loader portability rules (Scotty's directive, 2026-11-01)

Scotty's standing instruction from here on: **all new Off To Market work —
on either edition — is chosen so it ports cleanly to the other loader.**
No Forge-side implementation choice should force a rewrite for Fabric, and
vice versa. This section is the rulebook: what's already portable, what
isn't, and the rules that keep it that way.

### 13.1 Inventory: Forge-only vs vanilla in the current code (v0.6.7)

| Subsystem | Verdict | Evidence from the tree |
|---|---|---|
| **Game logic** (`data/`, 23 classes: pricing, towns, shipments, quests, workers, diplomats, mail, supply/demand) | ✅ Vanilla / pure Java | Works on vanilla types (NBT, ItemStack, registries-by-lookup). This is the bulk of the mod and it is already loader-neutral. |
| **Persistence** | ✅ Vanilla | `SavedData` + BE `saveAdditional`/`load` + item NBT (§4). No capabilities anywhere. |
| **GUI screens** (10 screens) | ✅ Vanilla | Screens extend vanilla `Screen`/`AbstractContainerScreen`; widgets come from `net.minecraft.client.gui.components` (Button, EditBox, …) in 9 files. No Forge GUI extensions, no `ScreenEvent` hooks, no custom slider classes in use today. (If a slider is ever needed: vanilla `AbstractSliderButton` exists on both loaders — subclass it, never a Forge-only widget.) |
| **Menus/containers** | 🟡 Mostly vanilla | `AbstractContainerMenu` + `ContainerData` are vanilla; the Forge-only parts are the *registration* (`IForgeMenuType.create` with extra-data buffer) and the *opening call* (`NetworkHooks.openGui` in all 5 blocks + `CoinBagItem`). Both are one-call glue at the edges. |
| **Registration** | ❌ Forge-only | `DeferredRegister`/`RegistryObject` (6 registry classes) + `@Mod` main class + `mods.toml`. The Fabric side is plain `Registry.register` (§1). Unavoidable loader glue — keep it *only* in `registry/` and the entrypoint. |
| **Networking** | ❌ Forge-only plumbing, ✅ portable payloads | `SimpleChannel` + `NetworkDirection` registration is Forge; the 43 packets' encode/decode byte layouts and handler *logic* are vanilla `FriendlyByteBuf` code (§3). |
| **Config** | ❌ Forge-only | `ForgeConfigSpec` + `ModConfigEvent` baking (§5). Mitigated on Fabric by ForgeConfigAPIPort; the *shape* (static fields read everywhere) is the portable part — preserve it. |
| **Events** | ❌ Forge-only, but concentrated | Every `@SubscribeEvent` in the mod lives in just **6 files**: `OffToMarket` (tick/stopping/commands/missing-mappings), `event/ClientEvents` (client setup), `config/ModConfig` (config bake), and the 3 client render classes below. The game code itself never touches the event bus directly — an accident of structure worth making a rule (13.2 #4). |
| **Client rendering extras** | ❌ Forge-only, confined to 3 files | `client/NotificationToastRenderer`, `client/render/RouteMarkerRenderer`, `debug/DebugOverlay` are the only files importing `net.minecraftforge.client.*` (Forge render/HUD events → Fabric: `HudRenderCallback` / world-render events). `ClientDepotTracker` is state-only. |
| **Dist handling** | 🟡 Forge idiom | `DistExecutor` in `GuideBookItem`, `LedgerItem`, `CartCheckoutResultPacket`, `OpenCustomMenuPacket` (open a screen / show a toast from common code). Fabric equivalent: a one-method client-call indirection per use site. Small, but each new use adds a porting papercut — see rule #6. |
| **Content loading** | ✅ Portable pattern | Town/menu JSON loaders read datapack-style resources (§8); loaders become reload listeners on Fabric. |
| **Sounds** | ✅ Vanilla types | `SoundEvent`s are vanilla (only their registration was Forge — and is currently missing entirely, §2). |

Net: the Forge-only surface is **registration, channel plumbing, config,
6 event files, 3 render files, and 2 glue calls (openGui, DistExecutor)**.
Everything else — the entire economy — is already written the portable way.

### 13.2 Standing rules for future features (both editions)

1. **Vanilla first.** If a vanilla class does the job (widgets, containers,
   SavedData, NBT, Brigadier, `FriendlyByteBuf`), use it. A Forge/Fabric
   API needs a written reason in the PR/commit that adopts it.
2. **Logic never imports loader packages.** Classes under `data/` (and any
   new game-logic package) must not import `net.minecraftforge.*` or
   `net.fabricmc.*`. If logic needs something loader-provided (send a
   packet, open a menu, read config), it calls a mod-owned interface in
   the shared layer; each loader implements it in its shell.
3. **Glue stays in its rooms.** Registration → `registry/` + entrypoint
   only. Networking → `network/` only. Config → `config/` only, always
   exposed to logic as baked static fields or a config interface — never
   let `ForgeConfigSpec` types (or Cloth types) leak into logic signatures.
4. **Events are translated at the border.** Loader event handlers (the 6
   files of 13.1, and their Fabric counterparts) may only *translate* an
   event into a call on shared logic (`SupplyDemandManager.onServerTick`
   is the model). No game rules inside event handlers.
5. **Packets are contracts.** A packet's byte layout is defined once, in
   the shared layer's terms (fields + order), and both loaders' codecs
   implement that layout. Handler bodies validate (menu open, distance,
   ownership) then delegate to shared logic — the validation checklist is
   part of the contract, not per-loader improvisation.
6. **No new `DistExecutor`-style escapes.** Client-only effects from
   common code go through the shared client-call interface (rule 2), so
   each effect has exactly one Forge implementation and one Fabric
   implementation, both findable by name.
7. **New persistent state goes in vanilla stores.** SavedData for
   world-level, BE NBT for per-block, item NBT for per-stack. Capabilities
   / data attachments / Cardinal Components require Scotty-level sign-off
   because each one is a porting debt by definition.
8. **Resources are shared, period.** Models, textures, lang, recipes,
   loot tables, town/menu JSON live in one place and ship in both jars
   byte-identical. Datagen, when adopted, generates *from* the shared
   definitions for both loaders (no hand-edits in generated output).
9. **Pin the port.** Every Forge release names the commit the Fabric
   edition is synced to (and vice versa); a feature isn't "done" until
   it's in shared logic or explicitly filed as loader-specific debt.

### 13.3 Recommended package split (for the port and for new Forge work)

```
com.offtomarket.core.*     ← shared, zero loader imports (rules 2–5 enforced
   data/  content/  item-behaviour logic, menu logic, packet contracts,
   config model (names/defaults/ranges as data), economy, towns
com.offtomarket.forge.*    ← thin shell: @Mod entrypoint, DeferredRegisters,
   SimpleChannel codecs, ForgeConfigSpec binding, event translators,
   NetworkHooks/DistExecutor implementations
com.offtomarket.fabric.*   ← thin shell: ModInitializer/ClientModInitializer,
   Registry.register calls, PlayNetworking codecs, FCAP binding,
   Fabric event translators
assets/data (resources)    ← single shared tree, copied into both jars
```

The current Forge tree is ~80% of the way to this shape already: `data/`,
`content/`, most of `item/` and `menu/` become `core` nearly as-is; the
shell absorbs `registry/`, `network/` registration halves, `event/`,
`config/` binding halves, and the 3 client render classes.

### 13.4 Multiloader now, or two parallel projects?

**Recommendation: two parallel projects today, structured for a later
merge — do NOT stop to build a multiloader monorepo mid-port.**

- *For multiloader (Architectury-style common + two shells):* one source
  of truth for logic; fixes land once; the codebase is unusually well
  suited (13.1: logic is already vanilla). This is the right *end state*.
- *Against doing it now:* (a) the Forge side is a moving target —
  Antigravity ships nightly; restructuring his live tree mid-sprint to
  satisfy a common-module build would collide with that work and break
  his release cadence; (b) the build tooling differs fundamentally
  (ForgeGradle vs Loom, reobf vs remap) and gluing them is its own
  project; (c) mappings are shared (Mojmap both sides) which removes the
  usual multiloader pain — meaning the *cost of staying parallel is
  unusually low*: shared files can be kept identical by copy/sync, since
  both sides compile against the same Minecraft names.
- *The bridge:* follow 13.3's split **inside each project** (same package
  names, same shared files), keep shared files byte-identical between the
  two trees (a sync checklist per release, rule 9), and treat any file
  that *can't* be identical as a bug in the split. When the Fabric edition
  reaches feature parity and the Forge release cadence slows, folding the
  two trees into a true common+shells build becomes a mechanical move
  instead of a rewrite — that's the moment to do it.

*End of Phase 1 audit. Skeleton + coin proof live beside this file.*
