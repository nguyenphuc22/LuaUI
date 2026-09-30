# Chất lượng & vận hành

> **Status:** Accepted architecture (Blueprint v1)
>
> **Scope:** Guardrail và chiến lược quality cho implementation tương lai; repository chưa công bố benchmark, SLO hay production support.

## Security model

Nguyên tắc gốc: **không tin payload server một cách mù quáng**. Client luôn là security boundary đối với dữ liệu đến từ network, kể cả khi server thuộc cùng tổ chức.

| Bề mặt | Guardrail bắt buộc theo kiến trúc |
| --- | --- |
| Code execution | Không downloaded/executable code, `eval`, script hay arbitrary method invocation |
| Actions/expressions | Typed allowlist; handler phải được client đăng ký trước |
| Components | Capability và schema validation trước render |
| URLs/deep links | Validate route/URL và allowlist theo policy ứng dụng |
| Payload | Giới hạn kích thước, độ sâu tree, số node và độ dài string |
| Patch | Validate operation, target ID, version và capability trước apply |
| Platform APIs | Không expose unrestricted file/platform API; file access phải sandboxed |

Authentication, authorization, session binding, integrity/signing, rate limit và data classification thuộc application/transport boundary. LuaUI phải cho phép tích hợp chúng nhưng không được giả định một cơ chế duy nhất. Chính sách chính xác cho Inspector production, telemetry privacy và cache encryption là [Open](open-questions.md#security--privacy).

## Lỗi và degraded experience

Mọi lỗi inbound phải có đường đi có kiểm soát:

```text
Payload → Decode → Validate → Runtime
                       │
                       └── invalid / unsupported → Error boundary → safe fallback
```

Nguyên tắc UX:

- Không crash toàn app vì một screen, node hay patch không hợp lệ.
- Debug build hiển thị diagnostic đủ hành động: loại lỗi, component/version, request/screen/patch ID.
- Production render fallback an toàn và không lộ data nhạy cảm, wire payload hay stack trace.
- Lỗi transport, schema, unknown/unsupported component được phân biệt để observability và retry policy đúng.

## Observability và analytics

LuaUI cần nối được một interaction xuyên client–server bằng correlation ID:

```text
requestId · sessionId · screenId · actionId · patchId
```

Ví dụ trace: `actionId` từ thao tác click → transport → business logic → `patchId` → client validation → render. OpenTelemetry là adapter định hướng, không thuộc core bắt buộc.

Event analytics định hướng bao gồm:

```text
screen_loaded · screen_rendered · component_impression · component_clicked
action_dispatched · action_completed · patch_received · patch_applied · render_failed
```

`LuaAnalytics` tách core khỏi Firebase, Amplitude, Mixpanel và vendor khác. Event schema, sampling, consent, PII redaction và retention phải được ứng dụng quyết định trước production use.

## LuaUI Inspector

Inspector là tooling first-class, không phải debug print tạm thời. Mục tiêu là cho phép inspect:

- Tree và stable IDs;
- server/local state;
- actions, expressions và capability;
- network, patch, cache và error;
- decode/build/render timing và performance data.

Inspector cần hiển thị **trạng thái thực** thay vì số liệu marketing. Truy cập Inspector, redaction và khả năng xuất payload phải có policy trước khi nó được bật ngoài môi trường phát triển.

## Hiệu năng: đo end-to-end

Hiệu năng không được suy ra chỉ từ việc dùng Protobuf. Chuỗi cần đo là:

```text
Network + Decode + Validate + Normalize + Tree/State reconciliation
+ Composition + Layout + Draw
```

Chiến lược kiến trúc bao gồm generated dispatch, zero reflection hot path, serializer generated, indexed node lookup, stable lazy keys, patch/granular state, background decode, cache-first và Compose native UI. Chúng là hypothesis để kiểm chứng, không phải kết quả benchmark.

Benchmark plan tối thiểu:

| Hạng mục | Quy mô/case |
| --- | --- |
| Decode và initial tree | 100, 1.000, 10.000 nodes |
| Patch | 1, 10, 100 nodes thay đổi |
| Lists | 100, 1.000, 10.000 items |
| Actions | local và network |
| Cache | cold và warm |
| Serialization | JSON và Protobuf |
| Nền tảng | Android, iOS, Desktop JVM |

Không công bố ngưỡng “fast”, latency hay throughput trước khi benchmark reproducible được xuất bản.

## Testing architecture

```text
Testing
├── Unit          schema, validator, expression, state, action, patch
├── Integration   server→client, transport→runtime, runtime→renderer, cache→runtime
├── Compatibility old→new schema/component/action/patch
├── UI            screenshot / golden
├── Fault         invalid payload, network failure, malformed patch, unsupported component
└── Performance   benchmark theo methodology công bố
```

Compatibility test là bắt buộc đối với public contract evolution. Mỗi bug liên quan payload/patch nên thêm fixture tối thiểu tái hiện lỗi và một test bảo vệ regression. Test data không được chứa token, PII hoặc payload production chưa redacted.
