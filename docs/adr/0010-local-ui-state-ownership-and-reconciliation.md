# ADR-0010: Local UI state thuộc client và được reconcile theo stable identity

- **Status:** Accepted
- **Date:** 2026-10-02
- **Decision area:** Local state, full-screen replacement, reconciliation

## Bối cảnh

ADR-0003 đã chốt rằng UI definition bất biến và stable ID là bắt buộc, nhưng chưa xác định lifecycle cụ thể của draft input, focus, scroll và expanded state khi server trả một full-screen replacement. `LuaNodeStore` hiện là snapshot definition do server mô tả; `LuaScreenStore` xử lý lifecycle `Loading`/`Ready`/`Incompatible`/`Failure`. Không lớp nào được phép âm thầm trộn local interaction vào `LuaScreen`.

Blueprint yêu cầu local state không round-trip ở mỗi keystroke, nhưng protocol Foundation 0.1 chưa có control stateful như `TextField`, action acknowledgement hay patch revision. Vì vậy quyết định này chốt ownership và reconciliation trước, không suy đoán API/wire model chưa tồn tại.

## Quyết định

### Ownership và ranh giới

- `LuaNodeStore` và `LuaScreenStore` tiếp tục là source of truth cho immutable definition và server state. Local UI state không được thêm vào `LuaNode`, `LuaScreen`, Proto3 schema hay ScreenStore Phase 2.
- `LuaLocalStateStore` là khái niệm runtime độc lập, KMP-safe và memory-only cho **một screen session đang sống**. Nó không phụ thuộc Compose, network, coroutine, `StateFlow`, persistence hay telemetry.
- Compose/renderer sở hữu object nền tảng thực tế như `FocusRequester`, `ScrollState`, `LazyListState`, gesture và animation controller. Runtime chỉ có thể giữ typed semantic state hoặc intent; không lưu object Compose trong core.
- Không có generic `Map<NodeId, Any?>`, key tự do do server gửi, hay shared mutable global state. Mỗi slot phải typed và do component/renderer đã đăng ký sở hữu.

### Identity và lifetime

Một entry local state chỉ hợp lệ trong một screen session và được định danh khái niệm bởi:

```text
(screenId, nodeId, typed local-state slot, renderer state-compatibility key)
```

`NodeId` ổn định là điều kiện cần nhưng chưa đủ để giữ state. Successor chỉ tương thích khi node vẫn tồn tại, cùng component semantic và cùng state schema/slot contract do renderer công bố. Node di chuyển trong tree vẫn giữ state nếu identity và compatibility không đổi.

Local state không được vượt qua navigation, một screen session mới, explicit reset, user/account boundary hay process lifetime. Host phải kết thúc screen session khi user/account boundary đổi vì runtime core không tự biết auth context. State không tự persist vào cache/disk, log, analytics hoặc tự serialize vào payload action.

### Full-screen reconciliation

Với một full-screen response hợp lệ cùng screen session, pipeline đích là:

```text
typed response → LuaScreenStore tạo LuaNodeStore candidate
               → LocalStateStore tạo/reconcile candidate
               → host publish remote + local successor trong một assignment
```

Payload invalid, partial hoặc sai `screenId` không được publish hay reconcile local state. `Loading`, `Failure` và `Incompatible` không reconcile hoặc render local state; screen session **giữ** snapshot private bất biến để retry cùng session. State chỉ bị release ở các boundary lifetime nêu trên.

Khi node bị xóa, đổi ID, đổi component/state contract hoặc opt out retention, entry bị prune ngay và không được hồi sinh nếu ID đó xuất hiện lại ở response sau. Full-screen response không tự là lệnh reset local state; server-authoritative reset cần protocol/capability/trust contract riêng.

### Policy theo loại state

| Loại | Owner | Qua replacement hợp lệ, tương thích |
| --- | --- | --- |
| Draft input | `LuaLocalStateStore` | Draft dirty giữ giá trị local; full response cập nhật baseline nhưng không ghi đè hoặc tự clear `dirty`. Draft pristine được reseed bằng default server mới. |
| Expanded/collapsed, temporary selection | `LuaLocalStateStore` | Giữ override local; definition chỉ seed default khi chưa có entry local. |
| Focus | Compose/platform renderer | LocalStateStore chỉ có thể giữ typed focus intent theo node; renderer sở hữu controller và restore best-effort sau composition, không được cướp focus mới của user. |
| Scroll | Compose/platform renderer | LocalStateStore chỉ có thể giữ typed anchor/container hint; renderer sở hữu controller và restore best-effort, clamp/fallback khi anchor hoặc layout thay đổi. |
| Gesture/animation | Compose renderer | Không là durable StateStore và không có bảo đảm restore qua replacement. |

Keystroke chỉ thay đổi draft local. Một action payload contract tương lai, versioned và được khai báo rõ mới có thể gửi giá trị ở boundary như debounce, blur hoặc submit; Foundation 0.1 `Submit` hiện chỉ mang ID và không serialize draft. Full response chỉ cập nhật baseline, không tự chuyển draft dirty thành pristine. Sau acknowledgement đã được đặc tả, app có thể gọi reset draft rõ ràng.

### Privacy và safety

- Default là memory-only; không persist qua process restart hoặc sang screen/session khác.
- Component có dữ liệu nhạy cảm phải có retention policy local rõ ràng, không log/telemetry raw value và phải clear khi session bị dispose hoặc boundary component yêu cầu.
- Server không thể gửi arbitrary local-state mutation, forced focus hay authoritative overwrite trong policy này. Những khả năng đó cần contract versioned, capability negotiation và review trust riêng.

## Hệ quả

- Full replacement không làm mất interaction state tương thích, nhưng definition vẫn giữ immutable và server-owned.
- Host cần một coordinator/session snapshot để tránh publish definition mới với local state cũ chưa reconcile; `LuaScreenStore` Phase 2 không bị mở rộng ngầm thành state container Compose.
- Component stateful tương lai phải công bố typed slots, state schema/compatibility và retention policy; stable ID bị tái sử dụng sai sẽ tự prune thay vì leak state sang ngữ nghĩa mới.
- Một số hành vi vẫn intentionally delayed: patch conflict, revision/ordering, action acknowledgement, persistence/encryption, multi-device sync, scroll anchor algorithm, accessibility focus semantics và public API stability.

## Các lựa chọn đã không chọn

- **Nhét local state vào `LuaScreen` hoặc server payload:** phá ownership, serialization boundary và có thể làm server ghi đè draft bất ngờ.
- **Giữ chỉ theo `NodeId`:** không đủ an toàn khi component/slot semantics đổi nhưng ID bị reuse.
- **Một mutable global map với `Any`:** không type-safe, khó KMP, dễ leak Compose object và không có lifecycle rõ ràng.
- **Xem mọi full response là reset:** làm mất input/focus/scroll hợp lệ và biến UI update thành UX regressions.
- **Tự persist draft mặc định:** tạo rủi ro privacy/session isolation trước khi có encryption và retention contract.

## Compatibility impact

Quyết định này chưa thay đổi wire schema hoặc public implementation API. Bất kỳ field reset, authoritative overwrite, stateful control, action acknowledgement hoặc persistence nào về sau phải có compatibility/version/capability story và spec/ADR bổ sung.

## Liên quan

- [ADR-0003: Immutable definitions and stable IDs](0003-immutable-definitions-and-stable-ids.md)
- [LocalStateStore Runtime 0.2](../specs/runtime-local-state-store-0.2.md)
- [Runtime & rendering](../architecture/runtime.md)
- [Stable ID & patch open questions](../architecture/open-questions.md#stable-id--patch)
