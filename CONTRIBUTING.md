# Đóng góp cho LuaUI

Cảm ơn bạn đã muốn tham gia xây dựng LuaUI. Repository hiện ở giai đoạn **architecture-first**: chưa có mã nguồn hoặc build system. Đóng góp có giá trị nhất lúc này là làm rõ contract, thu hẹp MVP và biến các quyết định đã chốt thành đặc tả có thể kiểm thử.

## Trước khi bắt đầu

1. Đọc [tổng quan kiến trúc](docs/architecture/README.md), [protocol](docs/architecture/protocol.md) và [các ADR](docs/adr/README.md).
2. Kiểm tra [câu hỏi mở](docs/architecture/open-questions.md). Nếu đề xuất của bạn giải quyết một mục ở đó, hãy nêu rõ phạm vi và trade-off.
3. Giữ MVP là một vertical slice. Không thêm trước các module, transport hay adapter chỉ vì chúng xuất hiện trong kiến trúc đích.

## Các ràng buộc không được phá vỡ

- Server mô tả **WHAT**; client quyết định **HOW**.
- Payload không được mang executable code hoặc yêu cầu reflection/dynamic invocation trên hot path.
- UI definition bất biến và tách khỏi local UI state.
- Mọi node có stable ID; các thay đổi ID phải đánh giá tác động đến patch, state và analytics.
- Contract phải transport-agnostic và design-system-agnostic.
- Client phải validate payload trước runtime và có fallback an toàn khi payload không tương thích.
- Capability/versioning không được bị thay bằng một version tổng duy nhất.

## Khi nào cần ADR?

Tạo hoặc cập nhật một ADR trong `docs/adr/` nếu thay đổi:

- protocol, schema hoặc quy tắc evolution;
- ranh giới server/client hoặc model bảo mật;
- state, patch, stable ID hoặc reconciliation;
- cơ chế registration, code generation hoặc reflection;
- lựa chọn bắt buộc giữa transport, persistence, design system hay public API.

Một ADR cần nêu bối cảnh, quyết định, hệ quả và liên kết đến tài liệu bị ảnh hưởng. Không thay đổi ADR đã được chấp nhận để viết lại lịch sử; tạo ADR thay thế và đánh dấu ADR cũ là superseded khi cần.

## Chuẩn pull request khi mã nguồn xuất hiện

- Giới hạn thay đổi vào một mục tiêu có thể review.
- Cập nhật docs khi public API, wire contract, metric, feature status hoặc cách sử dụng thay đổi.
- Thêm test tương ứng: unit cho logic, integration cho ranh giới client–server, compatibility cho evolution, và fault test cho payload lỗi.
- Không tuyên bố benchmark hoặc production support nếu chưa có dữ liệu và tiêu chí được công bố.
- Không đưa secret, dữ liệu người dùng, token hoặc payload production vào repository hay fixture test.

## Cách tổ chức tài liệu

- `README.md`: định vị, trạng thái và điểm vào cho người mới.
- `docs/architecture/`: contract, boundaries và các yêu cầu kỹ thuật có tuổi thọ dài.
- `docs/adr/`: lý do đằng sau những quyết định bền vững.
- `docs/reference/`: thuật ngữ và thông tin tra cứu.
- `docs/roadmap.md`: thứ tự thực hiện, không phải danh sách tính năng đã phát hành.

Viết rõ trạng thái của từng khả năng: **đã chốt về kiến trúc**, **MVP**, **planned**, **open**, **experimental** hoặc **đã phát hành**. Repository hiện tại chỉ có tài liệu: nó ghi nhận kiến trúc Accepted, delivery Planned và câu hỏi Open; chưa có implementation đã phát hành.
