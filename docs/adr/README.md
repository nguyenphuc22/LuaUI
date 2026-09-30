# Architecture Decision Records

ADR ghi lại những trade-off kiến trúc có tuổi thọ dài. Chúng bổ sung cho tài liệu architecture: architecture nói hệ thống hoạt động thế nào, ADR nói **vì sao** một hướng được chọn.

## Quy ước

- Mỗi ADR có số tăng dần, title ổn định và trạng thái.
- Không sửa ADR Accepted để viết lại lịch sử. Khi cần thay đổi, tạo ADR mới, liên kết đến ADR cũ và đánh dấu nó **Superseded**.
- `Accepted` ở đây nghĩa là đã được Blueprint v1 chốt về mặt kiến trúc; không có nghĩa implementation đã phát hành.
- Mọi thay đổi public contract phải nêu compatibility impact và cập nhật các tài liệu liên quan.

## Chỉ mục

| ADR | Quyết định | Trạng thái |
| --- | --- | --- |
| [0001](0001-client-native-server-driven-ui.md) | Server mô tả WHAT, client quyết định HOW | Accepted |
| [0002](0002-declarative-safe-protocol.md) | Protocol chỉ mang contract declarative, không mang code thực thi | Accepted |
| [0003](0003-immutable-definitions-and-stable-ids.md) | UI definition bất biến và stable ID là bắt buộc | Accepted |
| [0004](0004-generated-registries-no-hot-path-reflection.md) | Registration/dispatch được generate, không reflection hot path | Accepted |
| [0005](0005-transport-and-design-system-agnostic.md) | Contract độc lập transport và design system | Accepted |
| [0006](0006-versioning-and-capability-negotiation.md) | Evolution đa lớp qua version riêng và capability negotiation | Accepted |
| [0007](0007-mvp-vertical-slice-before-module-expansion.md) | Chứng minh vertical slice trước khi mở rộng module landscape | Accepted |

Xem [hướng dẫn đóng góp](../../CONTRIBUTING.md) để biết khi nào một đề xuất cần ADR.
