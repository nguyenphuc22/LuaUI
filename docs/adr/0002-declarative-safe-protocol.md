# ADR-0002: Protocol chỉ mang contract declarative, không mang code thực thi

- **Status:** Accepted
- **Date:** 2026-09-30
- **Decision area:** Protocol security, actions, expressions

## Bối cảnh

Server cần biểu đạt hành vi UI động: navigate, submit, show feedback, update state và điều kiện hiển thị. Đưa code tùy ý xuống client sẽ tạo rủi ro security, làm hành vi không thể dự đoán và phá tính portable của Kotlin Multiplatform.

## Quyết định

LuaUI Protocol chỉ mang data, typed node, declarative action và expression AST allowlisted. Server có thể chạy business logic bất kỳ ở phía server, nhưng không được truyền Kotlin code, JavaScript, script, class/method name để invoke tùy ý hoặc payload thực thi khác xuống client.

Custom action được biểu diễn bằng type đã đăng ký; client handler được app đóng gói và KSP register từ trước. Expression chỉ dùng operation typed được allowlist, không dùng `eval()` hay arbitrary function invocation.

## Hệ quả

- Client có thể validate action/expression về type, capability, version và limit trước khi thực thi.
- Runtime giữ được behavior nhất quán trên Android, iOS và Desktop.
- Một loại hành vi mới cần cập nhật client và capability, thay vì server tự gửi code.
- Cần đặc tả thêm expression semantics, action idempotency/retry và deep-link policy trước public API ổn định.

## Các lựa chọn đã không chọn

- **JavaScript/Kotlin scripting trong payload:** remote execution và cross-platform semantics khó kiểm soát.
- **Reflection-based arbitrary client call:** phá allowlist và tăng attack surface.
- **Chỉ cho static UI:** an toàn hơn nhưng không đáp ứng mục tiêu SDUI dynamic.

## Liên quan

- [Protocol & compatibility](../architecture/protocol.md)
- [Runtime & rendering](../architecture/runtime.md)
- [Security model](../architecture/quality.md#security-model)
