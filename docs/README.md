# Tài liệu LuaUI

Đây là chỉ mục cho Architecture Blueprint v1 của LuaUI. Bộ tài liệu gồm kiến trúc đã chốt, delivery planned và các câu hỏi Open cần đặc tả; không mục nào là API đã phát hành hay inventory implementation hiện có.

## Điểm vào theo vai trò

| Vai trò | Lộ trình đọc |
| --- | --- |
| Product/tech lead | [Architecture overview](architecture/README.md) → [Roadmap](roadmap.md) → [Open questions](architecture/open-questions.md) |
| Client engineer | [Runtime & rendering](architecture/runtime.md) → [Protocol](architecture/protocol.md) → [Quality & operations](architecture/quality.md) |
| Server engineer | [Platform & integration](architecture/platform.md) → [Protocol](architecture/protocol.md) → [Runtime & rendering](architecture/runtime.md) |
| Framework maintainer | [ADR index](adr/README.md) → toàn bộ `architecture/` → [Contributing](../CONTRIBUTING.md) |

## Bản đồ tài liệu

```text
docs/
├── architecture/
│   ├── README.md                 # North star, boundaries, component map
│   ├── protocol.md               # Contract, capability, versioning
│   ├── runtime.md                # Render, state, action, patch, offline
│   ├── platform.md               # Server SDK, transport, design-system
│   ├── quality.md                # Security, errors, observability, tests
│   └── open-questions.md         # Các spec/ADR cần chốt trước khi code sâu
├── adr/                          # Quyết định kiến trúc bền vững
├── reference/
│   └── glossary.md               # Thuật ngữ chuẩn hoá
└── roadmap.md                    # Thứ tự hiện thực và tiêu chí 1.0
```

## Quy ước trạng thái

| Nhãn | Ý nghĩa |
| --- | --- |
| **Accepted architecture** | Quyết định được Blueprint v1 chốt; implementation có thể chưa tồn tại. |
| **MVP** | Phạm vi cần chứng minh ở vertical slice đầu tiên. |
| **Planned** | Hướng mở rộng sau MVP; không được mô tả như tính năng sẵn có. |
| **Open** | Cần đặc tả hoặc ADR trước khi trở thành contract. |

## Nguyên tắc bảo trì tài liệu

`README.md` giải thích **LuaUI là gì**. Tài liệu kiến trúc giải thích **vì sao và cách hệ thống vận hành**. ADR giải thích **vì sao một trade-off được chọn**. Khi nội dung giữa chúng mâu thuẫn, ADR mới nhất có trạng thái Accepted là nguồn quyết định; sau đó các tài liệu còn lại phải được cập nhật trong cùng thay đổi.
