# ADR-0006: Evolution đa lớp qua version riêng và capability negotiation

- **Status:** Accepted
- **Date:** 2026-09-30
- **Decision area:** Backward compatibility, rollout safety

## Bối cảnh

SDUI production có client ở nhiều phiên bản. Một screen có thể dùng component/action/expression/patch version khác nhau. Một version tổng duy nhất không diễn đạt được phần client thực sự hiểu và làm rollout server có nguy cơ gửi payload unknown.

## Quyết định

LuaUI version độc lập cho SDK, protocol, schema, component, action, patch và expression. Client công bố capability/version đã cài; server resolve phần tương thích, chọn fallback hoặc trả typed incompatibility khi không có fallback an toàn. Client vẫn validate capability trước runtime như defence in depth.

## Hệ quả

- Server rollout có thể target population capability đa dạng mà không crash client cũ.
- Component author phải duy trì compatibility/fallback story và test fixture.
- Contract/phân loại capability phức tạp hơn nhưng minh bạch hơn một global version.
- Cần chốt exact negotiation envelope, unknown-field policy, deprecation và migration policy trước 1.0.

## Các lựa chọn đã không chọn

- **Một global version duy nhất:** không thể biểu đạt component/action independently evolved.
- **Server assume client latest:** không an toàn với staged rollout, offline client và long-tail devices.
- **Client silently ignore everything unknown:** có thể tạo UI sai/nguy hiểm thay vì fallback có chủ đích.

## Liên quan

- [Protocol & compatibility](../architecture/protocol.md)
- [Testing architecture](../architecture/quality.md#testing-architecture)
- [Schema & wire format open questions](../architecture/open-questions.md#schema--wire-format)
