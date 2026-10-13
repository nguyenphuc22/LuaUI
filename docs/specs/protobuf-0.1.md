# LuaUI Proto3 0.1 — Canonical schema compatibility spike

> **Status:** Accepted for the Foundation 0.1 compatibility spike
>
> **Scope:** Canonical Proto3 schema, generated JVM bindings and typed compatibility adapter for the Foundation 0.1 baseline plus capability-gated `TextField` and submit/acknowledgement experiments. This is not a stable public wire API and does not switch the HTTP sample away from JSON.

## Source of truth and boundary

The canonical schema source for this spike is [luaui.proto](../../luaui-proto/src/main/proto/luaui/v1/luaui.proto). `:luaui-proto` compiles it with `protoc` into Java bindings; `:luaui-proto-jvm` maps those bindings explicitly to and from the KMP-safe core model.

The mapping is deliberately a JVM boundary. `luaui-core` stays free of the Protobuf Gradle plugin and generated types, so a future non-JVM client can use an appropriate native binding without changing the semantic model. The mapper uses typed `oneof` cases only; it does not use `DynamicMessage`, descriptors or Kotlin reflection on the runtime path.

Typed JSON remains the reference serialization for the Foundation 0.1 HTTP sample. JSON and Protobuf are required to map through the same `LuaScreen`, request, response, capability and error semantics. The reference server validates typed JSON screen/action requests before they reach a controller; the Proto3 adapter applies the same core validators. Neither JSON wire shape nor Protobuf JSON mapping is a second source of truth.

## Contract shape

| Semantic type | Proto3 representation |
| --- | --- |
| `LuaScreen` | `Screen` with explicit ID, protocol/schema versions, root and repeated capabilities |
| `LuaNode` | `Node.id` plus exactly one `column`, `text`, `button` or additive `text_field` `oneof` member |
| `LuaAction` | `Action.action_id` plus `submit` or additive `submit_text_fields` `oneof` member |
| Requests | `ScreenRequest`; `ActionRequest` có optional typed `text_field_submission` chỉ cho extension |
| Screen result | `ScreenResponse = screen | incompatible | failure` |
| Action result | `ActionResponse = screen | acknowledged_screen | failure` |
| Failure | `LuaError` with closed `ErrorCode` enum |

The field numbers in the schema are part of the contract. They are never reordered, reused or given a new semantic meaning. `Node.text_field = 5` is an append-only, capability-gated extension; its fields are only `label` and `initial_value`, never a mutable local draft. Phase 4 also appends `Action.submit_text_fields = 3`, `ActionRequest.text_field_submission = 5` and `ActionResponse.acknowledged_screen = 3`. Removed fields must reserve both their tag and name in a future schema edit.

`SubmitTextFieldsAction` declares an ordered TextField-node allowlist. `TextFieldSubmission` carries client-generated `submission_id` plus `node_id`, raw `value` and `draft_revision`; it is not a generic key/value map or idempotency credential. `AcknowledgedScreen` always pairs a full replacement `Screen` with `TextFieldAcknowledgement`, which echoes only `submission_id`, node IDs and revisions. The normalized authoritative value remains in the replacement screen's `TextFieldNode.initial_value`, not the acknowledgement.

## Decode and validation rules

- `Screen.root`, `ButtonNode.on_click`, `Node.kind`, `Action.kind` and response `result` must be present where applicable.
- Unknown or unset `oneof` cases fail before a renderer or action handler sees the payload.
- `ERROR_CODE_UNSPECIFIED` and unknown enum values fail safely; they do not become an arbitrary core error code.
- Repeated capabilities are canonicalized in `(name, version)` order when encoding. Duplicate names, invalid identifiers and non-positive versions are rejected on both encode and decode.
- A decoded screen passes the existing structural/capability validation before it is returned. The 256 KiB payload limit is checked before parse.
- A decoded `ActionRequest` validates optional submission syntax and bounds. `acknowledged_screen` must contain both a valid screen and a valid non-empty acknowledgement; malformed/missing variants fail before runtime settlement.
- Child order is preserved exactly. Stable IDs retain the same string value as the core/JSON model.

## Compatibility evidence

`LuaProtoCodecTest` verifies a nested Foundation Dashboard fixture with Unicode text through both JSON and Proto3, then asserts the same domain model. A separate TextField fixture guards additive `text_field = 5`; typed submission/ack tests guard append-only action/request/response tags `3`/`5`/`3`, field ordering and mapping. The existing Foundation golden and legacy `Submit` request remain unchanged. The suite also covers every request/response `oneof` branch, error-code mapping, selected descriptor tags and invalid payload behavior.

Run the focused check with:

```bash
./gradlew :luaui-proto:generateProto :luaui-proto-jvm:test
```

Generated files and descriptor sets are build outputs and are not committed. The source `.proto` and compatibility tests are the reviewed artifacts.

## Explicit deferrals

This spike does not add a gRPC service, HTTP Protobuf adapter, WebSocket transport, Protobuf Kotlin/Native bindings, public package publication, patch messages or custom components. It also does not define global response ordering, retry/cancellation/idempotency, partial acknowledgement or payloads for other stateful fields. Those require separate protocol and transport decisions after the 0.1 mapping evidence is reviewed.
