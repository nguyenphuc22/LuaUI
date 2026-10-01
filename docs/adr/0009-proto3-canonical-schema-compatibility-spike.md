# ADR-0009: Dùng Proto3 làm canonical schema cho compatibility spike

- **Status:** Accepted
- **Date:** 2026-10-01
- **Decision area:** Protocol schema, serialization boundary, compatibility

## Bối cảnh

Foundation 0.1 đã chứng minh HTTP + typed JSON end-to-end, nhưng JSON không được định hướng là canonical wire contract lâu dài. Blueprint yêu cầu Protobuf và JSON cùng biểu đạt một semantic contract; nếu chỉ giữ Kotlin data class/JSON, field tag, `oneof`, generated binding và compatibility với hệ sinh thái Protobuf sẽ chưa được kiểm chứng.

Đưa Protobuf plugin hay generated JVM type trực tiếp vào `luaui-core` sẽ làm common KMP model phụ thuộc vào toolchain JVM. Xây luôn gRPC, HTTP Protobuf và native bindings sẽ mở rộng phạm vi vượt nhu cầu kiểm chứng schema ban đầu.

## Quyết định

- `luaui-proto/src/main/proto/luaui/v1/luaui.proto` là canonical Proto3 source cho Foundation 0.1 compatibility spike.
- `:luaui-proto` chỉ chứa schema và Java bindings sinh bởi `protoc`; `:luaui-proto-jvm` là adapter explicit giữa bindings và core KMP.
- Node, action và response dùng closed `oneof`; error dùng enum đóng. Adapter reject variant unset/unknown, enum unknown, payload quá giới hạn và screen không qua structural validation.
- Repeated capability được encode theo thứ tự `(name, version)` để byte golden ổn định; tag/name không được đổi hoặc tái sử dụng.
- HTTP/Ktor sample vẫn dùng typed JSON. Protobuf schema/adapter chứng minh compatibility nhưng chưa là gRPC service, HTTP Protobuf transport hoặc public/stable API.

## Hệ quả

- Schema có thể được review, sinh bindings và bảo vệ bởi descriptor/golden tests mà core vẫn KMP-safe.
- Java/JVM là boundary hiện thực đầu tiên vì generated Kotlin APIs của Protobuf dựa trên Java; iOS/Web binding là quyết định sau.
- Mọi thay đổi Proto3 từ đây phải cập nhật spec, compatibility test và ADR khi thay đổi semantic/evolution policy.
- Có thêm hai module chuyên biệt, nhưng không thêm transport/runtime feature ngoài contract compatibility.

## Các phương án không chọn

- **Để JSON/Kotlin class là source duy nhất:** không bảo vệ tag/`oneof`/generated-binding compatibility.
- **Đưa plugin Protobuf vào KMP core:** buộc core vào JVM toolchain và làm ranh giới platform mờ đi.
- **Dùng reflection hoặc `DynamicMessage` cho adapter:** giảm type-safety và tạo đường payload mở không cần thiết.
- **Thêm gRPC ngay:** trộn kiểm chứng schema với thay đổi transport lớn hơn.

## Liên kết

- [Proto3 0.1 spec](../specs/protobuf-0.1.md)
- [Proto3 source](../../luaui-proto/src/main/proto/luaui/v1/luaui.proto)
- [Protocol 0.1 JSON reference](../specs/protocol-0.1.md)
- [ADR-0005: Contract độc lập transport](0005-transport-and-design-system-agnostic.md)
- [ADR-0008: Foundation reference contract](0008-foundation-0-1-reference-contract.md)
