# Câu hỏi mở trước khi mở rộng implementation

> **Status:** Open
>
> **Mục đích:** Những mục này không phủ nhận Blueprint v1. Chúng là chi tiết contract cần được chốt bằng spec hoặc ADR trước khi trở thành public API.

## Stable ID & patch

- Foundation 0.1 chốt ID explicit và unique trong một screen. Sau 0.1, ai sinh ID (Server DSL, domain hay client helper), scope cross-screen/session và lifecycle khi node di chuyển/đổi tên/xóa là gì?
- Runtime 0.2 Phase 1 đã có NodeStore snapshot/index theo scope một screen, Phase 2 có ScreenStore full-response lifecycle, và Phase 3 có TextField draft reconciliation experimental. Revision, action response ordering, stale-response discard và mutation policy cho patch vẫn chưa được chốt.
- Patch có `revision`/`baseRevision` không? Xử lý duplicate, out-of-order, replay và retry thế nào?
- `Batch` có atomic không? Partial failure báo thế nào và tree có rollback không?
- [ADR-0010](../adr/0010-local-ui-state-ownership-and-reconciliation.md) chốt retain/prune local state cho full-screen replacement; [ADR-0011](../adr/0011-textfield-local-draft-vertical-slice.md) hiện thực policy đó cho draft TextField. Patch conflict với dirty draft/focus/scroll, revision và batch rollback vẫn cần policy riêng.

## Schema & wire format

- Foundation 0.1 đã dùng Proto3 làm canonical compatibility source. JSON mapping public, unknown-field policy cross-version và tiêu chí nâng schema thành stable là gì?
- Quy tắc add/remove/rename/changing semantics của field public là gì?
- Required capability được encode/validate ở level screen hay từng node/action?
- Error model có type/code/correlation ID nào và phần nào an toàn để hiển thị cho client?

## Component & capability

- Baseline component set 0.1 gồm chính xác node nào? Nó khác gì với component catalogue đích?
- Version/fallback của custom business component liên module hoạt động ra sao?
- Khi không có fallback compatible, server trả typed error hay client render placeholder nào?
- Ai sở hữu fallback: Server SDK, component author hay app host?

## Expressions & actions

- Type system, null semantics, locale/date behavior, overflow/divide-by-zero và dependency tracking của expression là gì?
- Có giới hạn complexity/depth/evaluation budget nào?
- Action network retry, cancellation, optimistic state, progress và idempotency được mô hình hoá thế nào?
- Action acknowledgement/correlation nào cho phép clear hoặc merge local draft một cách rõ ràng, thay vì coi full response là reset?
- `OpenUrl`/deep link sử dụng route grammar và trust policy nào?

## Offline & local edits

- Cache key, TTL, invalidation, stale-data UX và user/session isolation là gì?
- Cache cần encryption ở layer nào; payload nào được phép persist?
- Offline action queue/retry/conflict có thuộc phạm vi LuaUI hay app adapter?
- Full-screen retain/prune policy đã được chốt tại [ADR-0010](../adr/0010-local-ui-state-ownership-and-reconciliation.md); persistence, encryption, TTL và local draft conflict với patch/offline queue vẫn cần spec.

## Security & privacy

- Auth/authz context, payload integrity, replay prevention và rate limit do transport/application triển khai cụ thể ra sao?
- Telemetry có consent, PII redaction, sampling và retention policy nào?
- Inspector có bị tắt trong production không; dữ liệu nào được redacted hoặc export?
- Các size/depth/node/string limits có default, override và telemetry như thế nào?

## Design system & accessibility

- Token fallback, dark mode, dynamic type, RTL, accessibility semantics và reduced motion được biểu diễn bằng contract nào?
- Custom renderer cần đảm bảo accessibility/semantics tối thiểu nào?
- Khi token không được design system cài đặt resolve thì fallback/failure ra sao?

## Public API & delivery

- Spring, Cupertino, Web/Wasm và Inspector được xếp planned hay experimental ở từng milestone nào?
- API stability, deprecation, migration và release support policy là gì?
- Documentation/sample nào là reference implementation cho golden path?

Khi giải quyết một câu hỏi, tạo ADR hoặc spec liên kết từ đây; không âm thầm biến một giả định implementation thành protocol contract.
