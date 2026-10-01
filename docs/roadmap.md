# Lộ trình LuaUI

> **Status:** Foundation 0.1 in progress; các milestone sau vẫn Planned
>
> Đây là thứ tự hiện thực theo Architecture Blueprint v1, không phải cam kết ngày phát hành hoặc danh sách tính năng đã sẵn sàng.

## Nguyên tắc ưu tiên

Không tạo hàng chục module trước khi có giá trị chạy được. LuaUI bắt đầu bằng một vertical slice chứng minh contract end-to-end:

```text
Server DSL → LuaNode → serialize → transport → decode → KSP dispatcher
           → Compose UI → user action → server
```

Chỉ sau khi đường này hoạt động, các khả năng dynamic/offline/full-stack mới được mở rộng.

## 0.1 — Foundation (MVP)

- schema, `LuaNode` và stable IDs;
- annotations + KSP generated renderer;
- Compose + Material 3 default adapter;
- HTTP transport, basic action, basic Server DSL;
- một reference sample end-to-end.

Contract thực thi đầu tiên được chốt tại [Protocol 0.1](specs/protocol-0.1.md). Nó cố ý hẹp hơn architecture đích để chứng minh vertical slice trước.

**Tiến độ hiện tại:** Gradle multi-module scaffold, core typed contract + validation, Server DSL, HTTP JSON/Ktor reference transport, KSP-generated Material 3 dispatcher, Compose Desktop sample và unit/integration tests đã có trên nhánh `dev`. Chúng vẫn là `0.1.0-SNAPSHOT`, không phải release ổn định.

**Definition of success:** Một server DSL tạo screen typed, client decode/validate/render bằng generated dispatcher, user dispatch một action và server phản hồi qua contract đã kiểm thử.

## 0.2 — Runtime

- NodeStore và StateStore;
- navigation, expressions và design tokens;
- cache + error boundary;
- Inspector cơ bản;
- custom components.

## 0.3 — Dynamic

- WebSocket;
- patch engine và reconciliation;
- capability negotiation + version negotiation;
- offline persistence;
- SQLDelight adapter tùy chọn.

## 0.4 — Full stack

- gRPC và optimized Protobuf path;
- Ktor Server SDK;
- Spring adapter;
- OpenTelemetry, analytics và Inspector nâng cao.

## 0.5+

- tuning hiệu năng;
- tối ưu iOS/Desktop;
- animation protocol và advanced forms;
- accessibility, internationalization;
- Web/Wasm experimental.

## 1.0 readiness

LuaUI chỉ nên cân nhắc 1.0 khi tất cả điều kiện sau có bằng chứng rõ ràng:

- protocol, public API và schema-evolution rules ổn định;
- backward compatibility và migration policy được định nghĩa/test;
- Android, iOS và Desktop production-ready;
- security review hoàn tất;
- benchmark reproducible được công bố;
- Server SDK ổn định và Inspector đủ dùng;
- tài liệu phát hành, support và deprecation policy rõ ràng.

## Những việc không làm trước MVP

- Không dựng toàn bộ landscape module đích.
- Không khóa core vào gRPC, Material 3, SQLDelight, Ktor, Spring hay một analytics vendor.
- Không hứa mức performance mà chưa có benchmark.
- Không biến feature 0.3–1.0 thành requirement của 0.1.
