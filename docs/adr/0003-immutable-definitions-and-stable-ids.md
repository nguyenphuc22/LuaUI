# ADR-0003: UI definition bất biến và stable ID là bắt buộc

- **Status:** Accepted
- **Date:** 2026-09-30
- **Decision area:** State, patch, reconciliation

## Bối cảnh

SDUI phải xử lý full screen, patch, local input và rendering incremental mà không làm mất interaction state hoặc traverse toàn bộ tree mỗi lần có thay đổi. Một mutable tree trộn server/local state làm ownership mơ hồ và tăng nguy cơ race/conflict.

## Quyết định

LuaUI screen/tree là immutable UI definition. Server state và local UI state có lifecycle riêng. Mỗi node có stable ID; identity policy phải cho phép NodeStore resolve node không mơ hồ trong screen hiện tại. ID được dùng xuyên patch, state reconciliation, analytics, animation, lazy key, Inspector và test. Foundation 0.1 chốt uniqueness trong screen; generator, cross-screen scope và migration cần spec trước public API.

NodeStore giữ index `NodeId → LuaNode` cùng quan hệ cấu trúc tree để patch lookup hiệu quả. Local input/focus/scroll/gesture/animation không round-trip theo từng thay đổi.

## Hệ quả

- Patch và invalidation có thể granular thay vì reload toàn screen.
- Renderer có input rõ ràng, dễ test và ít phụ thuộc mutable global state.
- Server author phải quản lý ID cẩn thận; đổi ID sai có thể mất state, animation continuity hoặc analytics attribution.
- Revision, ordering, atomicity và conflict policy của patch cần spec riêng trước khi realtime/offline implementation sâu.

## Các lựa chọn đã không chọn

- **ID theo vị trí tree:** vỡ khi insert/move và không ổn định cho lazy list/patch.
- **Một mutable state graph chung:** không rõ ownership và dễ đồng bộ nhầm local input lên server.
- **Reload full screen cho mọi thay đổi:** tốn network/CPU và làm UX yếu khi update nhỏ.

## Liên quan

- [Runtime & rendering](../architecture/runtime.md)
- [Protocol & compatibility](../architecture/protocol.md)
- [Stable ID & patch open questions](../architecture/open-questions.md#stable-id--patch)
