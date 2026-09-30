# ADR-0005: Contract độc lập transport và design system

- **Status:** Accepted
- **Date:** 2026-09-30
- **Decision area:** Modularity, integration, portability

## Bối cảnh

LuaUI cần có golden path rõ ràng nhưng phải phục vụ ứng dụng dùng HTTP/JSON, gRPC/Protobuf, WebSocket, Material 3, Cupertino hoặc company design system. Khóa core vào một adapter sẽ giảm khả năng dùng lại và buộc fork không cần thiết.

## Quyết định

LuaUI Protocol giữ semantic contract độc lập transport. `LuaTransport` là ranh giới cho screen/action/observe; HTTP, gRPC, WebSocket và fake/local là adapter. Protobuf + gRPC là golden path khuyến nghị, không bắt buộc.

Server gửi component semantics và design tokens, client resolve qua design-system adapter. Material 3 là default theo blueprint, không required. SQLDelight, OpenTelemetry, analytics vendor, Ktor/Spring cũng là adapters, không phải core dependency.

## Hệ quả

- Cùng screen/action/patch giữ semantics qua các transport khác nhau.
- App có thể triển khai company design system không cần fork LuaUI.
- Core API phải nhỏ và tránh leakage của framework/vendor type.
- Cần duy trì golden path, reference sample và contract test để modularity không biến thành docs mơ hồ.

## Các lựa chọn đã không chọn

- **gRPC-only:** tối ưu một stack nhưng loại bỏ HTTP/JSON và migration path thực tế.
- **Material-only wire contract:** đơn giản ngắn hạn nhưng phá brand/native portability.
- **Core phụ thuộc trực tiếp database/analytics/navigation vendor:** làm dependency graph nặng và khó evolve.

## Liên quan

- [Platform & integration](../architecture/platform.md)
- [Protocol & compatibility](../architecture/protocol.md#transport-độc-lập)
- [ADR-0007](0007-mvp-vertical-slice-before-module-expansion.md)
