# Glossary

| Thuật ngữ | Nghĩa trong LuaUI |
| --- | --- |
| **SDUI** | Server-Driven UI: server mô tả UI có khai báo, client render native trong capability đã cài đặt. |
| **LuaUI Protocol** | Contract client–server độc lập transport cho screen, action, patch, event và compatibility metadata. |
| **LuaScreen** | Definition của một screen: ID, schema version, root node, required capabilities và metadata. |
| **LuaNode** | Node UI typed có stable ID và một loại content/component. |
| **UI definition** | Tree UI bất biến do server mô tả; không đồng nghĩa với local UI state. |
| **Server state** | Data/configuration từ server như price, permission, product, title. |
| **Local UI state** | State tương tác cục bộ như input, focus, scroll, gesture, animation, expanded state. |
| **Stable ID** | ID ổn định của node, dùng làm key xuyên state/patch/test/analytics; Foundation 0.1 yêu cầu unique trong screen, còn scope/generator rộng hơn sẽ được đặc tả trước public API. |
| **LuaTree Builder** | Thành phần Server SDK tạo typed tree từ DSL và business data. |
| **Lua Runtime** | Client runtime xử lý definition, validation, state boundary, action, patch, navigation và render dispatch. |
| **NodeStore** | Index `NodeId → LuaNode` cùng quan hệ tree để patch/lookup hiệu quả. |
| **LuaAction** | Mô tả hành động declarative do user interaction hoặc runtime dispatch. |
| **Expression** | AST typed/allowlisted cho logic UI động, không phải executable code. |
| **Patch** | Thay đổi tăng dần vào definition thay vì tải lại toàn bộ screen. |
| **Reconciliation** | Đồng bộ tree/state sau patch và invalidation vùng render liên quan. |
| **Capability negotiation** | Client–server chọn phiên bản component/action/protocol chung có thể vận hành an toàn. |
| **Renderer** | Hàm/adaptor ánh xạ `LuaNode` semantic sang `@Composable`. |
| **Generated dispatcher** | Mã KSP sinh điều phối typed node đến renderer, tránh reflection hot path. |
| **Business component** | Component semantic cấp domain như `OrderSummary`, thay vì cây primitive lặp lại. |
| **Design token** | Semantic reference cho color/type/spacing/shape/elevation/icon/motion do client resolve. |
| **Golden path** | Stack được ưu tiên trong docs/sample, không phải stack bắt buộc. |
| **Error boundary** | Lớp cô lập payload/lifecycle lỗi và render fallback an toàn. |
| **Inspector** | Tooling quan sát tree, state, network, patch, capability, performance và error. |
