# LuaUI

[![Verify](https://github.com/nguyenphuc22/LuaUI/actions/workflows/verify.yml/badge.svg?branch=main)](https://github.com/nguyenphuc22/LuaUI/actions/workflows/verify.yml)

> **A type-safe, full-stack Server-Driven UI framework for Kotlin Multiplatform and Compose Multiplatform.**
> *Weave dynamic interfaces across platforms.*

LuaUI giúp backend mô tả **cái gì** cần hiển thị, còn ứng dụng khách quyết định **hiển thị như thế nào** bằng UI native của từng nền tảng. Mục tiêu là thay đổi cấu trúc, nội dung và hành vi khai báo của màn hình mà không phải phát hành lại ứng dụng, nhưng chỉ trong giới hạn capability mà client đã cài đặt.

> **Trạng thái hiện tại — Foundation 0.1 đã có reference baseline.** `main` có vertical slice Compose Desktop, canonical Proto3 compatibility contract và CI đã kiểm thử. Runtime 0.2 NodeStore Phase 1 đang được hiện thực trên nhánh feature. Đây vẫn là `0.1.0-SNAPSHOT`: chưa có API/wire contract ổn định, artefact phát hành hay cam kết production support.

## Tại sao LuaUI?

LuaUI không phải là bộ chuyển đổi `JSON → Compose`. Đây là một nền tảng SDUI gồm protocol, Server SDK, client runtime, hệ thống state/action và hạ tầng render để xây dựng giao diện dynamic mà vẫn native, type-safe và đa nền tảng.

```mermaid
flowchart TB
    S[Application / Server SDK] --> P[LuaUI Protocol]
    P --> T{Transport}
    T --> H[HTTP]
    T --> G[gRPC]
    T --> W[WebSocket]
    H --> R[LuaUI Client Runtime]
    G --> R
    W --> R
    R --> D[Generated dispatcher]
    D --> C[Compose Multiplatform]
    C --> A[Android]
    C --> I[iOS]
    C --> X[Desktop]
```

## Nguyên tắc cốt lõi

- **Server mô tả WHAT, client quyết định HOW.** Server gửi cấu trúc, dữ liệu, action và expression có khai báo; client giữ quyền render, design system và hành vi nền tảng.
- **Protocol là contract trung tâm.** Transport độc lập; Protobuf là hướng khuyến nghị, JSON là serialization thay thế, còn HTTP, gRPC và WebSocket là các lựa chọn transport.
- **Type-safe, generated và không reflection ở hot path.** KSP sinh registry, serializer, capability và dispatcher.
- **UI definition bất biến, state tách biệt.** Server state không lẫn với input, focus, scroll, gesture hay animation cục bộ.
- **Stable node ID là bắt buộc.** ID là nền tảng cho patch, reconciliation, state, analytics, Inspector và test; policy sinh/phạm vi identity được đặc tả trước public API.
- **Không tải hoặc thực thi mã từ server.** Action và expression chỉ có dạng declarative, allowlisted và được validate.
- **Thiết kế theo semantic.** Server gửi `Primary Button` hoặc token `title_medium`, không gửi pixel, màu hay lệnh render cụ thể.
- **Khả năng tương thích là first-class.** Capability negotiation và version độc lập cho protocol, schema, component, action, patch và expression.

## Phạm vi và ranh giới

LuaUI hướng tới Android, iOS và Desktop qua Kotlin Multiplatform + Compose Multiplatform. Web/Wasm là hướng tương lai.

LuaUI **không** là WebView, HTML renderer, JavaScript/Kotlin scripting runtime, remote-code-execution platform, backend tổng quát, database framework, networking framework thay thế hay Compose replacement.

## Golden path

| Phần | Lựa chọn ưu tiên |
| --- | --- |
| Server | Kotlin, Ktor, LuaUI Server SDK |
| Contract | Proto3 schema + JVM adapter là canonical compatibility spike đã kiểm thử; typed JSON vẫn là HTTP reference có kiểm thử |
| Kết nối | HTTP/Ktor trong sample Desktop; gRPC và WebSocket là các bước sau |
| Client | Kotlin Multiplatform, LuaUI Runtime, KSP, Compose Multiplatform |
| Giao diện | Material 3 là mặc định, design-system adapter là first-class |
| Lưu trữ | Cache memory/Okio; SQLDelight là tùy chọn |
| Quan sát | Correlation IDs; OpenTelemetry/Kermit adapter là tùy chọn |

Đây là đường mặc định, không phải khóa chặt framework vào Ktor, gRPC, Material 3 hay một backend cụ thể.

## Foundation 0.1 hiện có

Vertical slice đầu tiên hiện chứng minh:

```text
Server DSL → validated LuaScreen → HTTP JSON → decode + validate
           → KSP-generated Material 3 dispatcher → Compose Desktop
           → Submit → full replacement screen
```

Phạm vi cố ý hẹp: `Column`, `Text`, `Button`, `Submit`, exact capability `@1`, stable node ID, error response typed và full-screen replacement. Proto3 schema + adapter JVM chứng minh JSON/Protobuf cùng semantic model; HTTP sample vẫn dùng JSON. Runtime 0.2 bắt đầu bằng NodeStore snapshot/index; patch, custom component, TextField, expression, navigation, WebSocket/gRPC và offline chưa được hiện thực.

## Chạy Foundation 0.1

Yêu cầu JDK 21 trở lên. Gradle Wrapper tự quản lý phiên bản Gradle phù hợp.

```bash
./gradlew check
./gradlew :sample:run
```

Sample tự chạy một Ktor server cục bộ và mở Compose Desktop. Bấm **Refresh** để kiểm chứng action round-trip và full-screen replacement. Xem [hướng dẫn Foundation 0.1](docs/guides/foundation-0.1.md) để biết phạm vi và tiêu chí kiểm thử.

## Lộ trình triển khai

Việc hiện thực bắt đầu bằng một vertical slice nhỏ thay vì dựng toàn bộ kiến trúc module ngay lập tức:

```text
Server DSL → LuaNode → Serialize → Transport → Decode
          → KSP dispatcher → Compose → LuaAction → Server
```

Foundation reference gồm `luaui-core`, `luaui-compose`, `luaui-material3`, `luaui-annotations`, `luaui-ksp`, `luaui-transport`, `luaui-server` và `sample`. Compatibility spike thêm `luaui-proto` (schema/bindings) cùng `luaui-proto-jvm` (adapter), không mở rộng transport. Runtime 0.2 Phase 1 thêm `luaui-runtime` cho NodeStore snapshot/index; patch, WebSocket, gRPC và offline persistence vẫn là các bước sau.

Xem chi tiết tại [lộ trình](docs/roadmap.md).

## Tài liệu

| Nếu bạn muốn… | Đọc |
| --- | --- |
| Nắm kiến trúc và ranh giới hệ thống | [Tổng quan kiến trúc](docs/architecture/README.md) |
| Hiểu protocol, capability và evolution | [Protocol & compatibility](docs/architecture/protocol.md) |
| Hiểu runtime, render, state, action, patch và offline | [Runtime & rendering](docs/architecture/runtime.md) |
| Hiểu design system, server SDK và transport | [Nền tảng & tích hợp](docs/architecture/platform.md) |
| Hiểu bảo mật, độ tin cậy, observability, hiệu năng và test | [Chất lượng & vận hành](docs/architecture/quality.md) |
| Chạy hoặc review vertical slice hiện tại | [Foundation 0.1](docs/guides/foundation-0.1.md) |
| Xem contract thực thi hiện tại | [Protocol 0.1](docs/specs/protocol-0.1.md) |
| Review schema canonical compatibility spike | [Proto3 0.1](docs/specs/protobuf-0.1.md) |
| Review NodeStore Runtime Phase 1 | [Runtime NodeStore 0.2](docs/specs/runtime-node-store-0.2.md) |
| Xem các quyết định kiến trúc bền vững | [Architecture Decision Records](docs/adr/README.md) |
| Xem thuật ngữ chuẩn | [Glossary](docs/reference/glossary.md) |
| Bắt đầu đóng góp | [CONTRIBUTING.md](CONTRIBUTING.md) |

Chỉ mục đầy đủ nằm tại [docs/README.md](docs/README.md).

## Đóng góp

LuaUI ưu tiên tính nhất quán của protocol và tính an toàn của runtime hơn việc bổ sung nhanh nhiều component. Mọi thay đổi làm ảnh hưởng contract, ranh giới server/client, khả năng tương thích hoặc mô hình state cần có ADR và cập nhật tài liệu liên quan. Xem [hướng dẫn đóng góp](CONTRIBUTING.md) trước khi mở thay đổi.

## Giấy phép

Giấy phép chưa được công bố. Không coi repository hiện tại là một dependency có thể sử dụng trong production.
