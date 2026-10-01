# ADR-0008: Dựng một reference contract đóng cho Foundation 0.1

- **Status:** Accepted
- **Date:** 2026-09-30
- **Decision area:** MVP protocol scope, delivery

## Bối cảnh

Blueprint đích bao gồm protocol đa transport, custom component, patch, realtime, capability evolution và offline. Dựng toàn bộ trước khi một screen chạy end-to-end sẽ làm API dựa trên giả định thay vì feedback thực tế.

## Quyết định

Foundation 0.1 chỉ có `Column`, `Text`, `Button` và `Submit`. Reference sample chạy trên Compose Desktop, dùng HTTP + typed JSON, trả full screen replacement sau action và dùng exact capability `@1`. Node ID là explicit và unique trong screen. Client decode/validate trước render; server re-authorize action ID.

Protobuf vẫn là hướng canonical/recommended của architecture đích. JSON 0.1 chỉ là reference transport có thể kiểm thử, không phải quyết định public wire format lâu dài.

## Hệ quả

- Có thể kiểm chứng Server DSL, contract, validation, HTTP, generated dispatcher, Compose và action round-trip sớm.
- Patch, NodeStore, custom component, navigation, expression, WebSocket/gRPC, cache/offline và broad capability negotiation được hoãn.
- Source/public API cần được giới hạn rõ là `0.1.0-SNAPSHOT` cho tới khi Protobuf/runtime strategy và evolution rules được chốt.

## Các lựa chọn đã không chọn

- **Scaffold mọi feature Blueprint:** quá rộng, không có executable feedback loop sớm.
- **Manual JSON hay arbitrary custom nodes:** phá type-safe/validated contract.
- **Chờ Protobuf/gRPC hoàn chỉnh rồi mới có sample:** trì hoãn kiểm chứng UI/runtime không cần thiết.

## Liên quan

- [Protocol 0.1 spec](../specs/protocol-0.1.md)
- [Roadmap](../roadmap.md)
- [ADR-0007](0007-mvp-vertical-slice-before-module-expansion.md)
