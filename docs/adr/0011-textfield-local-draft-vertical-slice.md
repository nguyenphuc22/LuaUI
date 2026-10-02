# ADR-0011: TextField vertical slice giữ draft ở client và capability-gate schema

- **Status:** Accepted
- **Date:** 2026-10-02
- **Decision area:** Stateful control, local draft, protocol evolution, rendering

## Bối cảnh

[ADR-0010](0010-local-ui-state-ownership-and-reconciliation.md) đã chốt ownership/lifetime cho local state, nhưng khi đó chưa có control stateful để kiểm chứng policy bằng code. Bước hiện thực đầu tiên cần đi xuyên Server DSL, typed core model, JSON/Proto3 mapping, validation, immutable runtime, generated renderer và sample HTTP mà không mở generic StateStore, action payload hay patch engine.

`LuaCapabilities.foundation` là baseline đã được client Foundation 0.1 sử dụng để tự quảng cáo renderer. Mở rộng set này ngầm sẽ khiến client cũ báo hỗ trợ `TextField` dù không có renderer. Ngược lại, gửi draft trong mỗi keystroke hoặc xem full response là acknowledgement sẽ phá local ownership và có thể làm mất text người dùng đang nhập.

## Quyết định

- Thêm `LuaTextFieldNode(id, label, initialValue)` làm definition additive, capability-gated bởi `component.text_field@1`. `initialValue` chỉ là server baseline, không phải live draft.
- Giữ `LuaCapabilities.foundation` nguyên vẹn. Server phải declare capability TextField qua tree; client không quảng cáo `textField` nhận typed `Incompatible` trước render.
- Thêm `TextFieldNode text_field = 5` theo kiểu append-only vào Proto3 `Node.oneof`, và mapping typed tương đương trong strict JSON. `ActionRequest`/`Submit` không thêm field hay payload.
- Hiện thực `LuaLocalStateStore` KMP-safe, memory-only với đúng một typed slot `LuaTextFieldDraft(value, serverBaseline, isDirty)`. Không expose generic key/value map, Compose controller, persistence hay server mutation API.
- Hiện thực `LuaScreenSession` là publication unit gồm `LuaScreenStore` và `LuaLocalStateStore`. Nó chỉ reconcile draft sau khi ScreenStore tạo `Ready(LuaNodeStore)` hợp lệ, rồi host gán một successor session duy nhất.
- Cùng `LuaTextFieldNode` và slot `TextFieldDraft/v1` trong một session là tương thích. Dirty draft giữ `value` và cập nhật `serverBaseline`; pristine draft nhận baseline mới. Remove, ID change hoặc đổi sang node type khác prune state; ID xuất hiện lại không revive draft cũ.
- Material 3 renderer lấy giá trị/callback qua `LuaRenderContext`, không tự `remember` raw text. Sample dùng TextField + Refresh để chứng minh draft local còn giữ khi server trả full replacement.

## Hệ quả

- Foundation client cũ vẫn hoạt động với Foundation screen cũ; nếu server trả screen cần TextField, nó nhận `Incompatible` thay vì crash hoặc render thiếu.
- Definition server vẫn bất biến và transport-agnostic, còn value đang gõ không đi vào JSON/Proto3, log/network hay `Submit` hiện tại.
- TextField có thể được kiểm thử theo boundary: validator/capability, codec/tag, runtime reconciliation, KSP dispatcher, Material renderer và HTTP sample.
- Bề mặt này vẫn experimental. Slice không truncate local draft, nên không dành cho input có kích thước không kiểm soát; API cap/validation per-field, payload/acknowledgement, max input UX, validation business-domain, focus/scroll state, debounce/blur, stale-response order, patch/offline conflict và persistence không được suy đoán từ implementation này.

## Các phương án không chọn

- **Thêm TextField vào `foundation`:** client legacy sẽ tự quảng cáo sai khả năng render.
- **Đưa draft vào `LuaTextFieldNode` hoặc Proto3:** biến local interaction thành server-owned definition và tạo nguy cơ overwrite/persistence ngoài ý muốn.
- **Gửi draft trong `LuaActionRequest` hoặc mỗi keystroke:** cần contract versioned cho payload, acknowledgement, authorization, privacy và conflict resolution; chưa có trong scope.
- **Generic `Map<NodeId, Any?>`:** không type-safe, không có ownership/compatibility rule rõ và dễ leak object Compose.
- **Để renderer tự `remember` text:** không thể reconcile nhất quán với immutable full-screen replacement hoặc expose policy cho runtime test.

## Compatibility impact

- `Node.text_field = 5` là tag Proto3 mới append-only; tag/name cũ không đổi và Foundation golden payload vẫn được giữ.
- JSON có polymorphic tag mới `text_field`; server phải capability-gate screen trước khi gửi cho client không hỗ trợ.
- Capability `component.text_field@1` là opt-in. `LuaCapabilities.foundation`, `LuaActionRequest` và `LuaAction.Submit` không đổi.
- `LuaNodeDispatcher.Render(node, onAction)` Foundation được giữ lại; stateful overload là additive. Raw host Foundation cũ fail rõ ràng nếu nhận TextField thay vì render control không có local-state owner.
- Đây chưa phải public/stable wire API. Mọi thay đổi semantic của draft slot hoặc thêm payload/reset authoritative phải có capability/version story và ADR/spec tiếp theo.

## Liên kết

- [ADR-0010: Local UI state ownership](0010-local-ui-state-ownership-and-reconciliation.md)
- [LocalStateStore Runtime 0.2](../specs/runtime-local-state-store-0.2.md)
- [Protocol 0.1](../specs/protocol-0.1.md)
- [Proto3 0.1](../specs/protobuf-0.1.md)
