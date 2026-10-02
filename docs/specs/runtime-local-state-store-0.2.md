# LuaUI Runtime 0.2 — LocalStateStore Phase 3

> **Status:** Accepted architecture; implementation planned
>
> **Scope:** Local, ephemeral interaction state cho một screen session. Đây không phải patch engine, persistence, action payload contract, Compose state container hay public/stable API.

## Mục tiêu

`LuaLocalStateStore` sẽ tách local interaction khỏi immutable UI definition, đồng thời giữ những interaction tương thích qua full-screen replacement mà không cho server âm thầm ghi đè draft của user.

```text
LuaScreen response → ScreenStore / NodeStore candidate
                 → LocalStateStore reconciliation candidate
                 → one screen-session successor → host state assignment → Compose
```

Contract này cụ thể hóa [ADR-0010](../adr/0010-local-ui-state-ownership-and-reconciliation.md). Nó chốt policy trước khi LuaUI có `TextField`, stateful control wire schema hay public StateStore implementation.

## Ownership boundary

| Thành phần | Sở hữu | Không sở hữu |
| --- | --- | --- |
| `LuaNodeStore` | Server-described immutable definition, node index | Draft/focus/scroll local |
| `LuaScreenStore` | Full-response lifecycle và validated `Ready` snapshot | Local state, network scheduling, Compose state |
| `LuaLocalStateStore` tương lai | Typed local semantic entries của một screen session | `LuaScreen` mutation, Compose controller, persistence, arbitrary server command |
| Compose renderer | `mutableStateOf`, focus/scroll/gesture/animation object platform | Server state hoặc runtime-global local map |

Local UI state is client-owned and never mutates a LuaUI definition.

## Identity, compatibility và lifetime

Một entry chỉ tồn tại trong một screen session đang sống. Identity logic là:

```text
(screenId, nodeId, typed slot, renderer state-compatibility key)
```

`screenId` chỉ guard candidate cùng logical screen; **screen session instance do host sở hữu** mới là navigation boundary. Mở lại cùng `screenId` tạo LocalStateStore mới, trừ khi một continuation API được đặc tả sau này. `nodeId` stable cho phép retain trong cùng session; typed slot và compatibility key ngăn reuse state khi component hoặc semantics đổi. Renderer/component—not arbitrary server input—định nghĩa slot, state schema/version và rule compatible.

Local state bắt đầu memory-only và bị clear khi session đóng, screen/session khác được mở, host explicit reset, user/account boundary đổi hoặc process kết thúc. Host phải báo user/account boundary bằng cách kết thúc session vì runtime core không tự biết auth context. Nó không được tự persist, cache, log, telemetry hay gửi trong mỗi keystroke/action.

## Full-screen reconciliation contract

1. `LuaScreenStore` tạo candidate `LuaNodeStore` đã validate cho response cùng `screenId`.
2. Runtime tạo candidate LocalStateStore detached và reconcile từng entry với node snapshot mới.
3. Host publish definition successor và LocalStateStore successor trong **một** state assignment/session snapshot.

Invalid, partial hoặc wrong-screen payload không reconcile hay publish local state. `Loading`, `Failure` và `Incompatible` không render local state; screen session **giữ** snapshot private bất biến cho retry trong cùng session, nhưng không vượt qua lifetime boundary.

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
- Keystroke chỉ cập nhật local state. Giá trị chỉ đi lên server qua future action payload contract được version/capability-gate ở boundary như debounce, blur hoặc submit; Foundation 0.1 chưa có payload này.
- Clear sau acknowledgement đã được đặc tả cần được app gọi rõ ràng; protocol hiện không coi full response là acknowledgement hoặc reset.

### Expanded/selection

Expanded/collapsed và temporary selection là override local. Definition chỉ seed default khi entry local chưa tồn tại. Component tự định nghĩa khi selection không còn valid và phải prune thay vì giữ reference mơ hồ.

### Focus và scroll

Runtime không giữ `FocusRequester`, `ScrollState` hay `LazyListState`. LocalStateStore chỉ có thể giữ typed focus intent hoặc scroll anchor theo node/container semantic; renderer/Compose adapter sở hữu controller thực tế. Restore là best-effort sau composition, có clamp/fallback khi layout/anchor thay đổi và không được cướp interaction mới của user.

Gesture và animation thuộc Compose renderer, không phải durable StateStore và không có continuity guarantee qua replacement.

## Safety, privacy và API constraints

- Không dùng generic `Map<NodeId, Any?>`, Compose object hay shared mutable global state. API implementation phải typed, immutable-at-publication và KMP-safe.
- Stateful component có dữ liệu nhạy cảm phải có explicit local retention policy; raw value không được persist/log/telemetry và phải clear đúng boundary.
- Server-authoritative reset, remote focus, `UpdateState`, retention metadata hoặc forced overwrite cần wire contract versioned, capability negotiation và security review riêng.
- Patch/revision/realtime conflict, stale response ordering, action cancellation/acknowledgement, offline persistence/encryption và multi-device sync không được suy đoán từ Phase 3.

## Acceptance criteria cho implementation sau này

- Draft dirty và expanded state được retain qua same-session replacement hợp lệ tương thích; draft pristine nhận server baseline mới.
- Node remove, ID change hoặc compatibility change prune state và không revive state đã prune.
- Payload invalid/cross-screen không mutate/reconcile local store; retry cùng session có policy lifetime rõ ràng.
- Coordinator publish NodeStore + reconciled LocalStateStore atomically, không để renderer thấy definition mới với state cũ.
- Runtime không leak Compose types/mutable maps, không persist/log local value mặc định và clear state ở session/user boundary.

## Evolution boundary

Phase này không tạo implementation, control schema hay wire field. Khi thêm `TextField`, patch hoặc action acknowledgement, tạo spec/ADR cho component state schema, reset semantics, conflict resolution, ordering và compatibility. Xem [ADR-0010](../adr/0010-local-ui-state-ownership-and-reconciliation.md) và [câu hỏi mở](../architecture/open-questions.md#offline--local-edits).
