# LuaUI Runtime 0.2 — ScreenStore Phase 2

> **Status:** Implemented, experimental Runtime 0.2 API
>
> **Scope:** Lifecycle snapshot bất biến cho một full-screen response, kể cả `AcknowledgedScreen` có replacement screen. Đây không phải local UI StateStore implementation, patch engine, correlation/action engine hay public/stable API.

## Mục tiêu

`LuaScreenStore` đặt ranh giới rõ giữa typed response và state mà host render. Nó bind vào một `screenId`, giữ cấu hình validation của client và tạo một object successor hoàn chỉnh cho mỗi transition.

```text
typed response → LuaScreenStore.accept
                     ├─ screen-scope guard
                     ├─ LuaNodeStore candidate (validate + snapshot + index)
                     └─ immutable successor state → host state assignment → Compose
```

`luaui-runtime` vẫn chỉ phụ thuộc `luaui-core`. Store không biết HTTP/Ktor, coroutine, `StateFlow`, Compose hay persistence; transport và app host tiếp tục sở hữu I/O, scheduling và observable UI state.

## Contract Phase 2

- `LuaScreenStore.loading(screenId, clientCapabilities, limits)` tạo state `Loading` và snapshot client capabilities. Thay đổi một `MutableSet` của caller sau đó không làm yếu validation ở response kế tiếp.
- Store là screen-scoped. `LuaScreenResponse.Screen`, `LuaActionResponse.Screen` hoặc `LuaActionResponse.AcknowledgedScreen` có replacement `screenId` khác sẽ thành `Failure(INVALID_SCREEN)`; navigation/cross-screen replacement chưa thuộc Phase 2.
- Mỗi screen response có `screenId` phù hợp đi qua `LuaNodeStore.create(...)` trước. Chỉ khi candidate hoàn chỉnh và hợp lệ thì successor có state `Ready(nodeStore)`; không có raw tree hay partial index được publish.
- `LuaScreenResponse.Incompatible` trở thành `Incompatible(missingCapabilities)` với set bất biến. Typed `Failure` từ screen/action được giữ nguyên error. Candidate invalid trở thành safe `Failure(INVALID_SCREEN)` và giữ diagnostics validation chỉ-đọc cho logging/debug UI; production host tự quyết định redaction.
- `accept(...)` và `beginLoading()` không mutate store hiện tại. Host giữ state của mình và thực hiện một assignment, ví dụ `screenStore = screenStore.accept(response)`. Vì vậy full-screen replacement hoặc failure là nguyên tử ở ranh giới state của host, còn snapshot cũ vẫn có thể được kiểm tra an toàn.
- Sample giữ `Ready` trong lúc action request đang bay; khi response về, nó nhận full replacement qua cùng pipeline. `AcknowledgedScreen` chỉ được ScreenStore xem là replacement screen hợp lệ; settlement draft/correlation nằm ở `LuaScreenSession.complete(...)`. Không có progress overlay, retry, cancellation, optimistic update hoặc error overlay trên tree hiện có trong phase này.

## Những điều cố ý chưa làm

`LuaScreenStore` không tự giữ local input/focus/scroll state, patch/revision/reconciliation, response ordering hoặc stale-response discard, navigation, cache/offline, action retry/cancellation/progress/idempotency, `StateFlow`/Compose-state ownership hay renderer lỗi Compose. [LocalStateStore 0.2](runtime-local-state-store-0.2.md) và [TextField submission 0.2](runtime-text-field-submission-0.2.md) dùng `LuaScreenSession`, nhưng ScreenStore vẫn giữ ranh giới riêng: nó chỉ tạo candidate `Ready(nodeStore)`. `LuaScreenSession.complete(...)` có correlation hẹp cho acknowledgement TextField đã chuẩn bị; ScreenStore không giữ prepared action, submission ID hay revision và không quyết định response nào thắng ở mức global.

Patch ordering, local-edit conflict và action lifecycle tiếp tục là [câu hỏi mở](../architecture/open-questions.md#stable-id--patch) và [câu hỏi về action](../architecture/open-questions.md#expressions--actions).

## Evidence

`LuaScreenStoreTest` kiểm tra loading, full-screen/action replacement, failure/incompatible, scope mismatch, limits/capability validation và defensive collection snapshots. HTTP sample test đưa initial response, legacy Refresh và `AcknowledgedScreen` của Product filter qua cùng replacement pipeline, xác nhận snapshot cũ không đổi và action ID stable vẫn resolve ở snapshot mới.

```bash
./gradlew :luaui-runtime:allTests :sample:test
```

## Evolution boundary

Khi thêm slot local khác, patch hoặc action lifecycle ngoài acknowledgement TextField hẹp, cần spec/ADR riêng cho typed slot API, cancellation, global ordering, revision, atomicity và conflict policy. Không dùng ScreenStore Phase 2 để ngầm định một trong các policy đó.
