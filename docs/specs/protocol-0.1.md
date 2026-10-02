# LuaUI Protocol 0.1 — Foundation contract

> **Status:** Accepted for the 0.1 implementation spike
>
> **Scope:** Closed, intentionally small contract for the first end-to-end reference sample. It is not a stable public wire API.

## Goal

0.1 proves this one path safely:

```text
Server DSL → validated LuaScreen → HTTP JSON → decode + validate
           → generated Compose dispatcher → Submit → full replacement screen
```

The reference client is Compose Desktop. The common model remains Kotlin Multiplatform-safe; Android, iOS and Web/Wasm targets are deferred until this path is proven.

## Reference serialization decision

Proto3 schema tại [luaui.proto](../../luaui-proto/src/main/proto/luaui/v1/luaui.proto) là canonical source cho compatibility spike 0.1. HTTP adapter vẫn dùng **closed typed JSON model** serialized by `kotlinx.serialization`; điều này không thiết lập JSON hay Proto3 hiện tại thành public/stable LuaUI wire API.

JSON model vẫn deliberately closed và strict: không `JsonObject`, raw `bytes`, arbitrary maps hay custom node escape hatch. Generated JVM bindings và mapper đã chứng minh JSON/Proto3 cùng semantic contract; xem [Proto3 0.1 spec](protobuf-0.1.md) trước khi mở rộng wire format.

## Envelope

```text
LuaScreen
├── id: ScreenId
├── protocolVersion: 1
├── schemaVersion: 1
├── root: LuaNode
└── requiredCapabilities: Set<LuaCapability>
```

Transport requests/responses are:

```text
LuaScreenRequest(screenId, clientCapabilities)
LuaScreenResponse = Screen | Incompatible | Failure

LuaActionRequest(screenId, sourceNodeId, actionId, clientCapabilities)
LuaActionResponse = Screen | Failure
```

The action request carries identifiers only. The server must re-authorize `screenId`, `sourceNodeId` and `actionId` against authoritative state; a client must never be trusted merely because it previously received a button.

## Closed component and action set

| Kind | 0.1 contract |
| --- | --- |
| Layout | `LuaColumnNode` |
| Primitive | `LuaTextNode` |
| Control | `LuaButtonNode` |
| Action | `LuaAction.Submit(actionId)` |

`CustomNode`, business component, TextField, image/icon, expression, navigation, patch, realtime, cache and offline behavior are out of scope for 0.1.

## Identity, capabilities and limits

- `ScreenId`, `NodeId` and `ActionId` match `[A-Za-z][A-Za-z0-9._:-]{0,127}`.
- Every node ID is nonblank and unique within one screen. IDs are explicitly authored; never derived from tree position.
- A semantic node that survives a full replacement keeps its ID.
- Exact capability matching is used in 0.1: `component.column@1`, `component.text@1`, `component.button@1`, `action.submit@1`.
- Unsupported required capability returns a typed `Incompatible` response before render.
- Validation limits are provisional but enforced: payload ≤ 256 KiB, tree ≤ 1,000 nodes, depth ≤ 32, children per column ≤ 500, text ≤ 4,096 characters and button label ≤ 256 characters.

## Validation and evolution

The client always performs `decode → validate → normalize → render`. Invalid/unsupported payloads are converted into a typed error/fallback and never reach a renderer.

For this closed 0.1 contract, unknown JSON fields and unknown polymorphic node/action tags fail safely. Future protocol evolution may only add optional data or capability-gated variants with compatibility fixtures; semantic meaning and stable IDs must not change silently.

## Explicit deferrals

This spec intentionally does **not** decide patch revision/ordering, dynamic capability fallback, custom component schemas, local-state wire representation/persistence, action payloads/retry, authentication, cache encryption, gRPC service or native Protobuf bindings. Local-state runtime ownership is decided separately in [ADR-0010](../adr/0010-local-ui-state-ownership-and-reconciliation.md), but its protocol representation remains deferred. The remaining items require their own ADR/spec before public release.
