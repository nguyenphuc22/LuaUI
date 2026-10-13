# LuaUI Protocol 0.1 — Foundation contract

> **Status:** Accepted for the 0.1 implementation spike
>
> **Scope:** Closed Foundation baseline for the first end-to-end reference sample, plus the capability-gated experimental Runtime 0.2 `TextField` and submit/acknowledgement slices. It is not a stable public wire API.

## Goal

0.1 proves this one path safely:

```text
Server DSL → validated LuaScreen → HTTP JSON → decode + validate
           → generated Compose dispatcher → action → full replacement screen
```

The reference client is Compose Desktop. The common model remains Kotlin Multiplatform-safe; Android, iOS and Web/Wasm targets are deferred until this path is proven.

Foundation baseline remains frozen. Runtime 0.2 adds `TextField` local-draft reconciliation and a narrow, separately capability-gated submit/acknowledgement path. It is not a generic form or action-payload contract.

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

LuaActionRequest(screenId, sourceNodeId, actionId, clientCapabilities, textFieldSubmission?)
LuaActionResponse = Screen | AcknowledgedScreen | Failure
```

Legacy `Submit@1` request vẫn chỉ mang identifiers; `textFieldSubmission` vắng mặt khỏi JSON legacy. Chỉ `SubmitTextFields@1` mới dùng snapshot typed, capability-gated của những `TextField` được khai báo rõ. Dù loại action nào, server vẫn phải re-authorize `screenId`, `sourceNodeId` và `actionId` từ authoritative state; client không được tin cậy chỉ vì đã từng nhận button.

## Foundation baseline and experimental extension

| Kind | Foundation 0.1 contract |
| --- | --- |
| Layout | `LuaColumnNode` |
| Primitive | `LuaTextNode` |
| Control | `LuaButtonNode` |
| Action | `LuaAction.Submit(actionId)` |

Runtime 0.2 có hai extension cộng dồn nhưng opt-in độc lập:

- `LuaTextFieldNode(id, label, initialValue)` được gate bởi `component.text_field@1`. Nó mô tả server baseline; draft mutable vẫn local, không đi theo keystroke và không có trong `Submit@1`.
- `LuaAction.SubmitTextFields(actionId, textFieldNodeIds)` được gate bởi `action.submit_text_fields@1`. `textFieldNodeIds` là allowlist có thứ tự, không rỗng, không lặp, và mỗi ID phải là `LuaTextFieldNode` cùng screen. Request mang `LuaTextFieldSubmission(submissionId, fields)` với value/revision snapshot theo allowlist đó; không có map key/value tự do.

Thành công tường minh của extension thứ hai là `LuaActionResponse.AcknowledgedScreen(screen, acknowledgement)`: full screen replacement mang baseline authoritative/đã chuẩn hóa trong `initialValue`, còn acknowledgement chỉ echo `submissionId`, node ID và revision theo đúng thứ tự — không echo raw value lần hai. `Screen` thường không phải acknowledgement và không được tự reset draft. Chi tiết lifecycle client ở [TextField submission Runtime 0.2](runtime-text-field-submission-0.2.md).

`CustomNode`, business component, image/icon, expression, navigation, patch, realtime, cache and offline behavior remain out of scope.

## Identity, capabilities and limits

- `ScreenId`, `NodeId`, `ActionId` và opaque `SubmissionId` match `[A-Za-z][A-Za-z0-9._:-]{0,127}`. `SubmissionId` chỉ correlation cho acknowledgement, không phải credential hay idempotency key.
- Every node ID is nonblank and unique within one screen. IDs are explicitly authored; never derived from tree position.
- A semantic node that survives a full replacement keeps its ID.
- Exact capability matching is used in the frozen baseline: `component.column@1`, `component.text@1`, `component.button@1`, `action.submit@1`. `LuaCapabilities.foundation` remains exactly that set.
- Các extension yêu cầu capability opt-in riêng: `component.text_field@1` và `action.submit_text_fields@1`.
- Server phải capability-gate **screen load** bằng typed `LuaScreenResponse.Incompatible` trước render. Nếu client vẫn nhận raw `Screen` thiếu capability, validator phải reject an toàn; `LuaActionResponse` hiện không có nhánh `Incompatible` riêng. `AcknowledgedScreen` chỉ hợp lệ với client đã công bố **cả** `component.text_field@1` và `action.submit_text_fields@1`, kể cả khi replacement screen riêng lẻ không dùng extension.
- Validation limits are provisional but enforced: payload ≤ 256 KiB, tree ≤ 1,000 nodes, depth ≤ 32, children per column ≤ 500, text/initial/submitted TextField value ≤ 4,096 characters, button/TextField label ≤ 256 characters, and a typed submission/acknowledgement ≤ 32 fields.

## Validation and evolution

The client always performs `decode → validate → normalize → render`. Invalid/unsupported payloads are converted into a typed error/fallback and never reach a renderer.

For this closed 0.1 contract, unknown JSON fields and unknown polymorphic node/action tags fail safely. Future protocol evolution may only add optional data or capability-gated variants with compatibility fixtures; semantic meaning and stable IDs must not change silently.

## Explicit deferrals

This spec intentionally does **not** decide patch revision/ordering, dynamic capability fallback, custom component schemas, generic local-state payload/persistence, payload cho field type khác, retry/cancellation/idempotency, authentication, cache encryption, gRPC service hay native Protobuf bindings. Local-state runtime ownership nằm ở [ADR-0010](../adr/0010-local-ui-state-ownership-and-reconciliation.md), local draft ở [ADR-0011](../adr/0011-textfield-local-draft-vertical-slice.md), và submit/ack hẹp ở [ADR-0012](../adr/0012-textfield-submission-acknowledgement-vertical-slice.md). Global response ordering và stale discard vẫn chưa được quyết định; các phần còn lại cần ADR/spec riêng trước public release.
