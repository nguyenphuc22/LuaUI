# Chạy Foundation 0.1

> **Status:** In progress
>
> **Mục đích:** Chạy và kiểm chứng vertical slice đầu tiên; không dùng đây như hướng dẫn production integration.

## Điều kiện

- JDK 21 trở lên;
- kết nối mạng trong lần chạy Gradle đầu tiên để tải Gradle Wrapper và dependency;
- macOS, Windows hoặc Linux có thể chạy Compose Desktop theo runtime phù hợp của Gradle.

## Lệnh

```bash
./gradlew check
./gradlew :sample:run
```

Lệnh thứ hai tự khởi động Ktor server cục bộ tại `127.0.0.1:8080`, sau đó mở cửa sổ Compose Desktop. Đóng cửa sổ sẽ dừng server.

## Kịch bản xác nhận

1. Sample tải screen `dashboard` qua HTTP.
2. Client decode và validate typed JSON + capability `@1` trước render.
3. KSP-generated dispatcher chọn Material 3 renderer cho `Column`, `Text` và `Button`.
4. Bấm **Refresh** để gửi một `Submit` action với `screenId`, `sourceNodeId` và `actionId`.
5. Server re-authorize action, trả về full replacement screen cùng stable IDs; giá hiển thị thay đổi.

## Những gì chưa có

Đây không phải bản demo của toàn bộ Blueprint. Nó chưa bao gồm patch/NodeStore, custom component, input/local state, expression, navigation, gRPC, WebSocket, offline/cache, Android/iOS sample, persistence, observability hay production security policy.

Contract cụ thể nằm tại [Protocol 0.1](../specs/protocol-0.1.md). Mọi mở rộng public phải có compatibility story, tests và ADR khi phù hợp.

## Proto3 compatibility spike

Schema Proto3 canonical và adapter JVM được kiểm tra riêng, nhưng chưa thay thế HTTP JSON của sample:

```bash
./gradlew :luaui-proto:generateProto :luaui-proto-jvm:test
```

Xem [Proto3 0.1](../specs/protobuf-0.1.md) để biết nguồn schema, ranh giới platform và policy evolution.
