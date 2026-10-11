# ADR-0012: TextField submission có acknowledgement tường minh

- **Status:** Accepted
- **Date:** 2026-10-04
- **Decision area:** Action payload, local draft settlement, protocol evolution, compatibility

## Bối cảnh

[ADR-0011](0011-textfield-local-draft-vertical-slice.md) đã chứng minh `TextField` giữ draft ở client qua full-screen replacement, nhưng cố ý không gửi giá trị vào `Submit@1`. Điều đó bảo vệ ownership local state, nhưng chưa chứng minh một form stateful có thể gửi dữ liệu hữu ích cho server rồi settle draft an toàn.

Chỉ thêm value vào mọi `LuaActionRequest`, hoặc coi một full response là acknowledgement, đều không đủ an toàn. User có thể gõ tiếp sau lúc gửi; một response cũ, replay hoặc response của session khác không được xóa text mới. JSON của Foundation cũng strict, nên nullable field mới được encode mặc định sẽ làm server cũ không hiểu request `Submit@1` thất bại.

## Quyết định

- Giữ nguyên `LuaAction.Submit@1`, `LuaActionRequest` identifiers-only cho `Submit`, và `LuaCapabilities.foundation`.
- Thêm `LuaAction.SubmitTextFields(actionId, textFieldNodeIds)` sau capability opt-in `action.submit_text_fields@1`. Action declaration chỉ là allowlist có thứ tự của các `LuaTextFieldNode` trong cùng screen; nó không mang value hoặc server mutation command.
- Thêm nullable `LuaActionRequest.textFieldSubmission`. Nó là `LuaTextFieldSubmission(submissionId, fields)` typed, trong đó mỗi field có `nodeId`, raw value và `draftRevision`. Field này bị omit khỏi JSON khi `null`, nên JSON legacy `Submit` giữ nguyên shape.
- Client tạo `submissionId` opaque, unique theo invocation. Nó chỉ correlation acknowledgement; không phải authentication credential, replay nonce hay idempotency key.
- Thành công của `SubmitTextFields` là `LuaActionResponse.AcknowledgedScreen(screen, acknowledgement)`. Response luôn chứa full screen hợp lệ cùng `screenId`; acknowledgement echo đúng `submissionId`, thứ tự node ID và revision đã gửi. Value chuẩn hóa không bị echo lần hai: server đặt nó làm `LuaTextFieldNode.initialValue` trong replacement screen.
- Runtime chỉ settle draft qua `LuaScreenSession.complete(preparedAction, response)`. `prepareAction` snapshot đúng allowlist trong validated `LuaNodeStore`, draft revision/value và opaque session-lifetime token. `complete` chỉ reset một draft thành pristine khi session token, submission ID, thứ tự/field/revision acknowledgement, slot generation, revision và value hiện tại đều khớp; một acknowledgement hợp lệ consume prepared invocation, nên replay không áp replacement lần hai. Nếu user đã gõ tiếp, draft mới giữ nguyên.
- `LuaScreenSession.accept(response)` vẫn nhận full screen như trước nhưng **không** suy diễn acknowledgement/reset. `Failure`, plain `Screen`, acknowledgement sai hoặc response của session lifetime cũ không settle local draft.
- Server phải re-authorize `screenId`, source button, action ID/type, client capability và exact allowlist trên state authoritative. Payload lạ, thiếu, thừa hoặc không đúng kiểu bị reject. Slice dùng acknowledgement all-or-nothing ở protocol (không có partial server acceptance); sau đó local settlement vẫn kiểm tra freshness theo từng draft để không ghi đè field user đã sửa tiếp. Inline field errors, retry/idempotency và partial acceptance không thuộc phạm vi này.

## Hệ quả

- Sample có thể gửi Product filter thật sự: server trim value, trả baseline chuẩn hóa và acknowledgement; draft trở thành pristine chỉ sau khi response khớp.
- Legacy client/server vẫn dùng `Submit@1` và Proto3/JSON payload cũ. Client không quảng cáo `action.submit_text_fields@1` không được nhận screen dùng action này.
- Proto3 mở rộng append-only: `Action.submit_text_fields = 3`, `ActionRequest.text_field_submission = 5`, `ActionResponse.acknowledged_screen = 3`. Tag/name cũ không đổi.
- Raw value tồn tại trong action payload đúng một lần tại boundary submit. Nó không được log, telemetry, persist hoặc gắn vào error message bởi runtime/reference sample.
- Correlation chỉ bảo vệ settlement cục bộ cho snapshot/action này. Global ordering giữa full responses, retry delivery, server idempotency, optimistic state, cancellation và patch conflict vẫn Open.

## Các phương án không chọn

- **Mở rộng `Submit@1`:** thay đổi ngữ nghĩa action legacy và không cho capability story rõ ràng.
- **Generic `Map<NodeId, Any?>` hoặc payload tự do:** không type-safe, khó validate/authorize/redact và làm scope form mở vô hạn.
- **Gửi draft ở mỗi keystroke:** phá local-state boundary, tăng privacy/network cost và cần debounce/retry policy khác.
- **Plain `Screen` tự clear draft:** không có correlation/revision, nên có thể xóa text mới từ response chậm.
- **Ack chỉ mang value, không có full screen:** cho phép server điều khiển local state ngoài immutable definition/baseline và thiếu replacement để reconcile tree.

## Compatibility impact

- `component.text_field@1` và `action.submit_text_fields@1` đều opt-in; `foundation` vẫn chính xác là Column/Text/Button/Submit.
- JSON thêm polymorphic tags `submit_text_fields` và `acknowledged_screen`, cùng `textFieldSubmission` chỉ xuất hiện khi dùng extension. Unknown subtype/field vẫn fail safely ở client/server strict cũ.
- Proto3 field numbers và `oneof` membership mới được guard bằng descriptor/round-trip tests; Foundation golden payload không đổi.
- API vẫn experimental, chưa phải stable public contract. Bất kỳ field type mới, partial acknowledgement, validation error model, idempotency hoặc retry behavior nào cần ADR/spec và capability/version riêng.

## Liên kết

- [ADR-0010: Local UI state ownership](0010-local-ui-state-ownership-and-reconciliation.md)
- [ADR-0011: TextField local draft](0011-textfield-local-draft-vertical-slice.md)
- [TextField submission Runtime 0.2](../specs/runtime-text-field-submission-0.2.md)
- [Protocol 0.1](../specs/protocol-0.1.md)
- [Proto3 0.1](../specs/protobuf-0.1.md)
