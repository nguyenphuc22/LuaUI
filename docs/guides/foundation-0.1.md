# Chạy Foundation 0.1

> **Status:** Implemented, experimental
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
3. KSP-generated dispatcher chọn Material 3 renderer cho `Column`, `Text`, capability-gated `TextField` và `Button`.
4. Nhập ` rice ` vào **Product filter**. Giá trị này chỉ đổi local draft, không gửi network request ở mỗi keystroke.
5. Bấm **Apply filter**. Client gọi `LuaScreenSession.prepareAction`, snapshot đúng `TextField` được action allowlist, rồi gửi `SubmitTextFields` cùng typed submission sau capability `action.submit_text_fields@1`.
6. Server re-authorize action, chuẩn hoá filter thành `rice`, rồi trả `AcknowledgedScreen` gồm full replacement screen và acknowledgement theo `submissionId`/node/revision. Client gọi `complete`; acknowledgement phải khớp toàn bộ submission, rồi draft Product filter chỉ trở thành pristine nếu snapshot local của chính nó vẫn còn khớp baseline chuẩn hoá.
7. Gõ một giá trị mới rồi bấm **Refresh**. Đây là `Submit@1` legacy: action request vẫn có identifiers và client capabilities, nhưng không mang text-field submission; giá hiển thị thay đổi còn draft dirty vẫn giữ nguyên qua full-screen replacement.

## Những gì chưa có

Đây không phải bản demo của toàn bộ Blueprint. Runtime 0.2 hiện có NodeStore snapshot/index, ScreenStore lifecycle, LocalStateStore/TextField vertical slice và một submission/acknowledgement hẹp cho `TextField`; tất cả vẫn experimental. Đây không phải generic form engine: payload cho field/action khác, partial acknowledgement hoặc field-error model không có trong slice này. Foundation 0.1 vẫn chưa bao gồm patch, custom component, expression, navigation, gRPC, WebSocket, offline/cache, Android/iOS sample, persistence, observability hay production security policy.

Contract cụ thể nằm tại [Protocol 0.1](../specs/protocol-0.1.md). Mọi mở rộng public phải có compatibility story, tests và ADR khi phù hợp.

## Proto3 compatibility spike

Schema Proto3 canonical và adapter JVM được kiểm tra riêng, nhưng chưa thay thế HTTP JSON của sample:

```bash
./gradlew :luaui-proto:generateProto :luaui-proto-jvm:test
```

Xem [Proto3 0.1](../specs/protobuf-0.1.md) để biết nguồn schema, ranh giới platform và policy evolution.

## Runtime NodeStore Phase 1, ScreenStore Phase 2, LocalStateStore Phase 3 và Submission Phase 4

Runtime 0.2 có NodeStore KMP-safe để tạo snapshot/index sau validation và ScreenStore bất biến để biến typed full-screen/action response thành state `Loading`, `Ready`, `Incompatible` hoặc `Failure`. `LuaScreenSession` phối hợp chúng với `LuaLocalStateStore` typed draft cho `TextField`; renderer Material 3 không giữ raw text riêng. Sample quảng cáo `component.text_field@1` và `action.submit_text_fields@1` ngoài Foundation. `Submit@1` vẫn không mang draft; riêng `SubmitTextFields` dùng `prepareAction`/`complete` để settle acknowledgement khớp, không tạo policy ordering/retry toàn cục:

```bash
./gradlew :luaui-runtime:allTests
```

Xem [Runtime NodeStore 0.2](../specs/runtime-node-store-0.2.md), [Runtime ScreenStore 0.2](../specs/runtime-screen-store-0.2.md), [Runtime LocalStateStore 0.2](../specs/runtime-local-state-store-0.2.md) và [TextField Submission 0.2](../specs/runtime-text-field-submission-0.2.md) để biết scope và ranh giới evolution.
