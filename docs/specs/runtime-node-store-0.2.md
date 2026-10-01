# LuaUI Runtime 0.2 — NodeStore Phase 1

> **Status:** In progress
>
> **Scope:** Một snapshot/index bất biến cho `LuaScreen` đã validate. Đây chưa phải patch engine, StateStore cho local UI, cache, navigation hay public/stable API.

## Mục tiêu

`NodeId` đã là stable và unique trong phạm vi một screen. Phase 1 hiện thực `:luaui-runtime` để biến một definition hợp lệ thành một snapshot screen-scoped có lookup O(1), parent relation và thứ tự children không đổi.

```text
LuaScreen response → validate → LuaNodeStore.create → immutable snapshot/index
                                                      → Compose dispatcher
```

`luaui-runtime` chỉ phụ thuộc `luaui-core`. Nó không phụ thuộc Compose, Ktor, Protobuf, coroutines, cache hay persistence.

## Contract Phase 1

- `LuaNodeStore.create(...)` bắt buộc client capabilities và gọi `LuaProtocolValidator.validateScreen(...)` trước khi index. Payload invalid trả `LuaNodeStoreCreation.Invalid`; không ném exception và không tạo partial store.
- Store snapshot đệ quy `LuaColumnNode.children` và capability set bằng collection chỉ-đọc. Thay đổi hoặc cast một collection mà caller từng dùng để tạo screen không làm thay đổi store đã publish.
- Mỗi entry giữ node snapshot, `parentId` và ordered `childIds`; `find(NodeId)` là O(1).
- `NodeId` chỉ có nghĩa trong `screenId` của store hiện tại. Không có global cache/index theo NodeId.
- Sample tạo candidate store trước, sau đó gán một `DashboardUiState.Ready` mới trong một lần state assignment. Một full-screen response không mutate store cũ; payload invalid trở thành failure state thay vì render partial tree.

## Những điều cố ý chưa làm

Không có patch/revision, reconciliation mutating store, local input/focus/scroll state, action retry/cancellation, cache, Inspector, WebSocket, gRPC hay HTTP Protobuf. Revision ordering, batch atomicity và conflict với local edit vẫn là [câu hỏi mở](../architecture/open-questions.md#stable-id--patch).

## Evidence

`LuaNodeStoreTest` kiểm tra tree lồng nhau, lookup/parent/child order, source collection mutation, validation/capability/limit rejection và full-screen snapshot replacement. HTTP sample test tạo store từ screen ban đầu và từ action response, xác nhận refresh vẫn giữ stable action node ID.

```bash
./gradlew :luaui-runtime:allTests :sample:test
```

## Evolution boundary

Khi thêm patch hoặc local `StateStore`, cần một spec/ADR riêng cho revision, ownership, atomicity và conflict policy. Không thêm metadata patch vào `LuaScreen` hay Proto3 schema trong Phase 1.
