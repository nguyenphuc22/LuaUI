# ADR-0001: Server mô tả WHAT, client quyết định HOW

- **Status:** Accepted
- **Date:** 2026-09-30
- **Decision area:** Product boundary, rendering, platform behavior

## Bối cảnh

LuaUI cần cho phép server thay đổi cấu trúc, nội dung và behavior có khai báo của giao diện mà không phát hành app mới. Đồng thời, ứng dụng client vẫn phải giữ native UX, accessibility, security boundary và quyền chọn design system theo nền tảng.

## Quyết định

Server chỉ mô tả **WHAT**: semantic component tree, data, declarative action, expression, metadata và capability requirements. Client quyết định **HOW**: renderer, design token resolution, Compose/platform behavior, local interaction state và security enforcement.

LuaUI là protocol + Server SDK + client runtime + state/action/rendering infrastructure cho Server-Driven Compose UI. Nó không là WebView, HTML renderer, JavaScript runtime, remote-code-execution platform hay Compose replacement.

## Hệ quả

- UI tiếp tục render qua Compose native trên Android, iOS và Desktop.
- Server không gửi pixel/color/font/raw layout metric cụ thể hay executable code; semantic layout structure vẫn thuộc component contract.
- Design system và platform behavior có thể khác nhau mà semantic contract vẫn giữ nguyên.
- Một số thay đổi UI vẫn cần app release khi component/capability chưa tồn tại trên client.

## Các lựa chọn đã không chọn

- **Remote browser/WebView:** linh hoạt về layout nhưng phá native integration và ranh giới platform.
- **Server điều khiển pixel/layout chi tiết:** làm protocol gắn với một design system và khó bảo trì.
- **Downloaded script:** tăng surface tấn công, giảm predictability và không phù hợp multiplatform.

## Liên quan

- [Architecture overview](../architecture/README.md)
- [Platform & integration](../architecture/platform.md)
- [ADR-0002](0002-declarative-safe-protocol.md)
