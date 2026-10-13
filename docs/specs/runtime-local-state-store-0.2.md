# LuaUI Runtime 0.2 — LocalStateStore Phase 3

> **Status:** Implemented, experimental Runtime 0.2 API
>
> **Scope:** Local, ephemeral interaction state cho một screen session. Vertical slice hiện thực typed draft cho `TextField`; Phase 4 dùng nó qua một submit/acknowledgement boundary riêng. LocalStateStore không tự sở hữu wire payload, network scheduling, patch engine, persistence, Compose state container hay public/stable API.

## Mục tiêu

`LuaLocalStateStore` tách local interaction khỏi immutable UI definition, đồng thời giữ interaction tương thích qua full-screen replacement mà không cho server âm thầm ghi đè draft của user.

```text
LuaScreen response → ScreenStore / NodeStore candidate
                 → LocalStateStore reconciliation candidate
                 → one screen-session successor → host state assignment → Compose
```

Contract này cụ thể hóa [ADR-0010](../adr/0010-local-ui-state-ownership-and-reconciliation.md) và được triển khai lần đầu qua [ADR-0011](../adr/0011-textfield-local-draft-vertical-slice.md). [ADR-0012](../adr/0012-textfield-submission-acknowledgement-vertical-slice.md) bổ sung một boundary submit/ack hẹp trên `LuaScreenSession`; nó không mở generic StateStore hay generic action payload contract.

## Ownership boundary

| Thành phần | Sở hữu | Không sở hữu |
| --- | --- | --- |
| `LuaNodeStore` | Server-described immutable definition, node index | Draft/focus/scroll local |
| `LuaScreenStore` | Full-response lifecycle và validated `Ready` snapshot | Local state, network scheduling, Compose state |
| `LuaLocalStateStore` | Typed local semantic entries của một screen session | `LuaScreen` mutation, Compose controller, persistence, arbitrary server command |
| Compose renderer | `mutableStateOf`, focus/scroll/gesture/animation object platform | Server state hoặc runtime-global local map |

Local UI state is client-owned and never mutates a LuaUI definition.

## Identity, compatibility và lifetime

Một entry chỉ tồn tại trong một screen session đang sống. Identity logic là:

```text
(screenId, nodeId, typed slot, renderer state-compatibility key)
```

`screenId` chỉ guard candidate cùng logical screen; **screen session instance do host sở hữu** mới là navigation boundary. Mở lại cùng `screenId` tạo LocalStateStore mới, trừ khi một continuation API được đặc tả sau này. `nodeId` stable cho phép retain trong cùng session; typed slot và compatibility key ngăn reuse state khi component hoặc semantics đổi. Renderer/component—not arbitrary server input—định nghĩa slot, state schema/version và rule compatible.

Vertical slice hiện có một slot cố định `TextFieldDraft/v1`: node phải vẫn là `LuaTextFieldNode`, nên đổi sang bất kỳ node type nào cùng ID cũng prune state. Không có compatibility key do server gửi; key này thuộc implementation renderer/runtime.

Local state bắt đầu memory-only và bị clear khi session đóng, screen/session khác được mở, host explicit reset, user/account boundary đổi hoặc process kết thúc. Host phải báo user/account boundary bằng cách kết thúc session vì runtime core không tự biết auth context. Nó không được tự persist, cache, log, telemetry hay gửi trong mỗi keystroke/action.

## Bề mặt đã hiện thực

- `LuaTextFieldNode(id, label, initialValue)` là definition capability-gated bởi `component.text_field@1`. `LuaCapabilities.foundation` vẫn giữ nguyên, nên client Foundation cũ không tự quảng cáo renderer mới.
- `LuaLocalStateStore` chỉ giữ `LuaTextFieldDraft(value, serverBaseline, isDirty, draftRevision)` trong map private typed theo `LuaNodeId`; `draftRevision` tăng khi value đổi. Slot generation là chi tiết nội bộ để phân biệt một ID đã bị prune rồi xuất hiện lại; không có `Map<NodeId, Any?>` public hoặc mutable global state.
- `LuaScreenSession` đóng gói `LuaScreenStore` và `LuaLocalStateStore`. `accept(...)` chỉ reconcile sau khi ScreenStore tạo `Ready(nodeStore)` hợp lệ; host publish đúng một successor session vào observable state. `prepareAction(...)` và `complete(...)` là boundary Phase 4 để snapshot/settle một submit đã được khai báo, không phải scheduler hay generic action engine.
- Compose Material 3 đọc draft qua `LuaRenderContext` và gửi keystroke về callback session. `LuaScreenHost(screenSession = ...)` là entry point ưu tiên; overload NodeStore/raw Screen **có** LocalStateStore cần cùng `screenId` và callback phải publish `session = session.updateTextField(...)`. Overload Foundation raw Screen cũ reject TextField rõ ràng. Renderer không `remember` raw text.
- `LuaAction.Submit@1` và JSON legacy của nó không đổi: draft không được serialize hoặc gửi ở mỗi keystroke/action. Chỉ `SubmitTextFields@1`, sau `action.submit_text_fields@1`, mới gửi snapshot typed tại submit boundary; xem [TextField submission Runtime 0.2](runtime-text-field-submission-0.2.md).

## Full-screen reconciliation contract

1. `LuaScreenStore` tạo candidate `LuaNodeStore` đã validate cho response cùng `screenId`.
2. Runtime tạo candidate LocalStateStore detached và reconcile từng entry với node snapshot mới.
3. Host publish definition successor và LocalStateStore successor trong **một** state assignment/session snapshot.

Invalid, partial hoặc wrong-screen payload không reconcile hay tạo local-state snapshot mới. `Loading`, `Failure` và `Incompatible` không render tree; screen session giữ cùng immutable LocalStateStore để retry trong session đó, nhưng không vượt qua lifetime boundary.

| Điều kiện ở successor hợp lệ | Kết quả local entry |
| --- | --- |
| Cùng session, `NodeId`, typed slot và compatibility key | Retain theo policy của slot |
| Node bị remove, đổi ID hoặc state contract không compatible | Prune ngay |
| ID từng bị prune rồi xuất hiện lại | Treat như entry mới; không revive giá trị cũ |
| Node chỉ di chuyển trong tree nhưng identity/contract giữ nguyên | Retain |
| Navigation, session mới, explicit reset, account boundary | Clear toàn bộ session store |

## Policy theo slot

### Draft input

Draft có `value`, server-provided baseline và dirty/pristine status. Khi node tương thích:

- Draft dirty giữ local `value`; full response chỉ cập nhật baseline, không overwrite text user đang sửa hoặc tự clear `dirty`.
- Draft pristine được reseed bằng default/baseline mới từ server.
- Keystroke chỉ cập nhật local state. Giá trị chỉ đi lên server một lần tại boundary `SubmitTextFields` typed/capability-gated; debounce, blur và những payload khác chưa có contract.
- `LuaScreenSession.accept(...)` không coi bất kỳ full response nào là acknowledgement hoặc reset. Chỉ `complete(preparedAction, AcknowledgedScreen)` có thể settle draft: acknowledgement phải khớp exact submission ID/node order/revision với snapshot, cùng session lifetime; local slot generation, revision và value vẫn phải khớp để ack chậm không overwrite keystroke mới. Khi reconcile acknowledgement response, revision/value đã đổi kể từ submit vẫn được giữ—even nếu user đã gõ quay về baseline cũ khiến draft tạm pristine—rồi baseline chuẩn hóa mới lấy từ `initialValue` của replacement screen.

### Expanded/selection — chưa hiện thực

Expanded/collapsed và temporary selection là override local. Definition chỉ seed default khi entry local chưa tồn tại. Component tự định nghĩa khi selection không còn valid và phải prune thay vì giữ reference mơ hồ.

### Focus và scroll — chưa hiện thực

Runtime không giữ `FocusRequester`, `ScrollState` hay `LazyListState`. LocalStateStore chỉ có thể giữ typed focus intent hoặc scroll anchor theo node/container semantic; renderer/Compose adapter sở hữu controller thực tế. Restore là best-effort sau composition, có clamp/fallback khi layout/anchor thay đổi và không được cướp interaction mới của user.

Gesture và animation thuộc Compose renderer, không phải durable StateStore và không có continuity guarantee qua replacement.

## Safety, privacy và API constraints

- Không dùng generic `Map<NodeId, Any?>`, Compose object hay shared mutable global state. API implementation phải typed, immutable-at-publication và KMP-safe.
- Stateful component có dữ liệu nhạy cảm phải có explicit local retention policy; raw value không được persist/log/telemetry và phải clear đúng boundary.
- Slice không truncate local draft khi gõ. `SubmitTextFields` giới hạn request ở 32 fields, mỗi raw value tối đa 4.096 ký tự; giới hạn UX/semantic riêng theo field vẫn cần contract trước production use.
- Server-authoritative reset, remote focus, `UpdateState`, retention metadata hoặc forced overwrite cần wire contract versioned, capability negotiation và security review riêng.
- Patch/revision/realtime conflict, global stale-response ordering, action cancellation/retry/idempotency, offline persistence/encryption và multi-device sync không được suy đoán từ Phase 3/4. Guard acknowledgement chỉ bảo vệ settlement local cho snapshot TextField đã chuẩn bị.

## Evidence kiểm thử

- `LuaScreenSessionTest` kiểm tra draft dirty/pristine, baseline mới, node move, type change/remove prune, non-revival, loading/failure/incompatible, invalid/cross-screen response, reset/session mới, normalized acknowledgement, edit-after-submit và replay/session-boundary safety.
- `DashboardHttpIntegrationTest` đưa HTTP load, local edit, legacy `Submit` replacement giữ dirty draft, và typed Product filter được normalize/settle qua acknowledgement. Client thiếu capability bị `Incompatible` trước render.
- Core/Proto3 tests kiểm tra capability, submission/ack limits, JSON legacy/new mapping và Proto3 tags append-only; runtime tests đi qua validation, NodeStore candidate, reconciliation và local settlement của TextField.

## Evolution boundary

`TextField` vẫn là vertical slice experimental. Expanded/selection, focus/scroll semantic hint, payload cho field khác, partial acknowledgement/inline errors, patch/revision conflict, global stale-response ordering, retry/idempotency, offline persistence/encryption và multi-device sync vẫn cần spec/ADR riêng. Xem [ADR-0011](../adr/0011-textfield-local-draft-vertical-slice.md), [ADR-0012](../adr/0012-textfield-submission-acknowledgement-vertical-slice.md) và [câu hỏi mở](../architecture/open-questions.md#offline--local-edits).
