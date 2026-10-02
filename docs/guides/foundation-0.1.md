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

Đây không phải bản demo của toàn bộ Blueprint. Runtime 0.2 hiện có NodeStore snapshot/index và ScreenStore lifecycle cho full-screen response; LocalStateStore đã có policy kiến trúc nhưng chưa có control/input implementation. Foundation 0.1 vẫn chưa bao gồm patch, custom component, expression, navigation, gRPC, WebSocket, offline/cache, Android/iOS sample, persistence, observability hay production security policy.

Contract cụ thể nằm tại [Protocol 0.1](../specs/protocol-0.1.md). Mọi mở rộng public phải có compatibility story, tests và ADR khi phù hợp.

## Proto3 compatibility spike

Schema Proto3 canonical và adapter JVM được kiểm tra riêng, nhưng chưa thay thế HTTP JSON của sample:

```bash
./gradlew :luaui-proto:generateProto :luaui-proto-jvm:test
```

Xem [Proto3 0.1](../specs/protobuf-0.1.md) để biết nguồn schema, ranh giới platform và policy evolution.

## Runtime NodeStore Phase 1, ScreenStore Phase 2 và LocalStateStore Phase 3

Runtime 0.2 có NodeStore KMP-safe để tạo snapshot/index sau validation và ScreenStore bất biến để biến typed full-screen/action response thành state `Loading`, `Ready`, `Incompatible` hoặc `Failure`. LocalStateStore mới chốt ownership/reconciliation policy cho local input/focus/scroll; sample vẫn chưa có control stateful, patch hay local state implementation:

```bash
./gradlew :luaui-runtime:allTests
```

Xem [Runtime NodeStore 0.2](../specs/runtime-node-store-0.2.md), [Runtime ScreenStore 0.2](../specs/runtime-screen-store-0.2.md) và [Runtime LocalStateStore 0.2](../specs/runtime-local-state-store-0.2.md) để biết scope và ranh giới evolution.
