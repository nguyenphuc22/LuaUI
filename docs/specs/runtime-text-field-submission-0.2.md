# LuaUI Runtime 0.2 — TextField Submission Phase 4

> **Status:** Implemented, experimental Runtime 0.2 API
>
> **Scope:** Một vertical slice submit typed cho `TextField` và acknowledgement tường minh. Đây không phải generic form engine, idempotency/retry protocol, field-error model, patch ordering hay public/stable API.

## Mục tiêu

Phase 4 nối draft client-owned đã có ở LocalStateStore với một action server-authorized mà không để full-screen replacement tự ý xóa input mới hơn.

```text
validated Button action + LocalStateStore draft
    → prepareAction() snapshots allowlisted fields
    → typed request over transport
    → server re-authorizes + normalizes
    → AcknowledgedScreen(full screen + acknowledgement)
    → complete() validates local snapshot
    → reconcile then settle matching draft
```

## Contract hẹp

| Thành phần | Hành vi Phase 4 |
| --- | --- |
| Capability | `action.submit_text_fields@1`, không thuộc `LuaCapabilities.foundation` |
| Action definition | `LuaAction.SubmitTextFields(actionId, textFieldNodeIds)`; danh sách có thứ tự và chỉ là allowlist |
| Request | Nullable `textFieldSubmission = LuaTextFieldSubmission(submissionId, fields)`; mỗi field có `nodeId`, `value`, `draftRevision` |
| Success response | `LuaActionResponse.AcknowledgedScreen(screen, acknowledgement)` |
| Acknowledgement | Echo exact submission ID, node order và revision; không echo raw value lần hai |
| Authoritative value | `LuaTextFieldNode.initialValue` trong full replacement screen |

`Submit@1` không thay đổi: vẫn chỉ mang identifiers và nhận plain `Screen | Failure`. Plain `Screen` không phải acknowledgement.

## Client flow

Host sở hữu coroutine/network scheduling và tạo ID opaque unique cho một invocation. API ưu tiên là:

```kotlin
val prepared = session.prepareAction(
    sourceNodeId = buttonId,
    action = action,
    submissionId = LuaSubmissionId("screen.submit.42"),
) ?: return

val response = transport.dispatchAction(prepared.request)
session = session.complete(prepared, response)
```

`prepareAction` chỉ thành công khi screen `Ready`, source node là button hiện tại với action tương ứng, client có required capability, mọi declared field vẫn là `LuaTextFieldNode` và request tạo ra qua syntax/limit validation của session. Nó snapshot value/revision theo đúng thứ tự action declaration; app không tự dựng map giá trị tùy ý. Local draft không bị truncate; nếu snapshot vượt request limit, host nhận `null` và tự quyết định UX phản hồi.

`complete` áp acknowledgement khi và chỉ khi:

1. `LuaPreparedAction` thuộc cùng local screen-session lifetime và chưa được settle thành công trước đó;
2. response là `AcknowledgedScreen` có screen hợp lệ cùng screen ID;
3. submission ID, số lượng, thứ tự node ID và draft revision trùng snapshot;
4. local slot generation, revision và value hiện tại vẫn là snapshot đã gửi;
5. node vẫn là TextField compatible trong replacement screen.

Runtime validate replacement trước, rồi atomically claim prepared invocation một lần trước khi reconcile/settle. Khi mọi điều kiện khớp, draft thành pristine ở `initialValue` server trả về. Nếu user gõ sau khi gửi, runtime giữ value/revision mới hơn khi reconcile—even nếu value đó quay về baseline cũ và tạm thời là pristine—sau đó cập nhật baseline mới; ack cũ không overwrite draft mới. Nếu node bị remove/type-change, entry bị prune theo policy Phase 3 và không revive.

`All-or-nothing` mô tả việc server chấp nhận toàn submission và acknowledgement phải khớp toàn bộ snapshot; nó không ghi đè freshness của từng draft local. Sau khi acknowledgement hợp lệ, mỗi field chưa đổi kể từ lúc gửi có thể settle độc lập; field đã được user sửa tiếp vẫn dirty và giữ value mới hơn.

## Server validation và authorization

Core validator kiểm tra identifier, field list rỗng/trùng, revision âm, value length (mặc định 4,096 ký tự) và tối đa 32 field. Điều đó chỉ là validation cú pháp.

Server controller **phải** kiểm tra lại từ state authoritative:

- `screenId`, source button và action ID/type;
- capability client;
- exact action allowlist và node type;
- business validation/normalization của raw value.

Reference sample trim Product filter, trả baseline chuẩn hóa trong `screen`, rồi echo ID/revision trong acknowledgement. Payload raw không được log hay đưa vào error response.

## Compatibility và wire format

`textFieldSubmission` nullable dùng `@EncodeDefault(NEVER)`: request legacy không phát JSON key `null` mới. JSON strict thêm tags `submit_text_fields` và `acknowledged_screen` chỉ sau capability gate. Proto3 dùng tag append-only:

- `Action.submit_text_fields = 3`;
- `ActionRequest.text_field_submission = 5`;
- `ActionResponse.acknowledged_screen = 3`.

Unknown/unset variants và malformed acknowledgement bị transport/codec reject trước runtime. `AcknowledgedScreen` cũng bị transport/ScreenStore reject khi client không quảng cáo cả `component.text_field@1` và `action.submit_text_fields@1`, kể cả nếu replacement screen có Foundation shape. Foundation `Submit` JSON/Proto fixture giữ nguyên.

## Ngoài phạm vi

- global ordering/stale discard cho mọi full-screen response;
- retry, cancellation, progress, idempotency key hoặc offline action queue;
- partial acknowledgement, inline field errors, optimistic update hoặc remote forced overwrite;
- field type ngoài TextField, persistence, encryption, telemetry, patch/realtime conflict;
- cap semantic riêng cho unbounded local draft ngoài giới hạn request tại submit boundary.

Các hạng mục trên cần contract/capability/ADR riêng. Xem [ADR-0012](../adr/0012-textfield-submission-acknowledgement-vertical-slice.md) và [câu hỏi mở](../architecture/open-questions.md#expressions--actions).

## Evidence kiểm thử

- Core: capability, allowlist reference, request/ack limits và strict JSON legacy/new round trip.
- Proto3: `oneof`/tag append-only, typed submission/ack mapping và Foundation fixture không đổi.
- Runtime: normalized settlement, edit-after-submit, mismatch/replay/session boundary, raw response không auto-reset và prune/non-revival.
- HTTP sample: allowlisted Product filter đi tới server, được normalize và settle qua acknowledgement; legacy Refresh vẫn giữ dirty draft.
