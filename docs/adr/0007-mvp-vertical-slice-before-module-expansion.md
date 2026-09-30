# ADR-0007: Chứng minh vertical slice trước khi mở rộng module landscape

- **Status:** Accepted
- **Date:** 2026-09-30
- **Decision area:** Delivery, repository structure, scope control

## Bối cảnh

Kiến trúc đích của LuaUI gồm nhiều vùng: core, compiler, rendering, state, actions, transport, storage, server, observability, tooling và samples. Tạo tất cả trước khi có end-to-end behavior sẽ phân tán effort, tạo API suy đoán và khoá dependency graph sớm.

## Quyết định

MVP chỉ khởi tạo `luaui-core`, `luaui-compose`, `luaui-material3`, `luaui-annotations`, `luaui-ksp`, `luaui-transport`, `luaui-server` và `sample`. Nó phải chứng minh `Server DSL → LuaNode → serialize → transport → decode → generated dispatcher → Compose → LuaAction → Server` trước khi thêm NodeStore, patch, WebSocket, gRPC, offline persistence hoặc adapter rộng hơn.

Danh sách module lớn là kiến trúc đích, không phải scaffold bắt buộc.

## Hệ quả

- Có một executable contract sớm để xác thực DSL, protocol và generated runtime path.
- Tránh chi phí bảo trì module/API không có consumer thực.
- Một số feature như offline/realtime/Inspector phải đợi foundation ổn định; docs phải ghi rõ trạng thái planned.
- Cần review ranh giới module khi vertical slice có bằng chứng thay vì tự động tách theo sơ đồ đích.

## Các lựa chọn đã không chọn

- **Scaffold toàn bộ 30+ module ngay từ đầu:** tạo cảm giác progress nhưng tăng surface API chưa kiểm chứng.
- **Chỉ prototype một app không có protocol boundary:** nhanh hơn nhưng không xác nhận framework contract/reusability.
- **Chờ tất cả adapter trước sample:** trì hoãn feedback loop quan trọng nhất.

## Liên quan

- [Roadmap](../roadmap.md)
- [Architecture overview](../architecture/README.md#cấu-trúc-module-đích-đến-và-mvp)
- [Contributing](../../CONTRIBUTING.md)
