# ADR-0004: Registration và dispatch được generate, không reflection trên hot path

- **Status:** Accepted
- **Date:** 2026-09-30
- **Decision area:** Extensibility, performance, type safety

## Bối cảnh

LuaUI cần mở rộng component, renderer, serializer, action handler và capability mà vẫn an toàn kiểu và phù hợp Kotlin Multiplatform. Runtime reflection, annotation scanning hoặc dynamic invocation làm startup/dispatch khó đoán và không phù hợp đường render nóng.

## Quyết định

KSP/KotlinPoet là hướng code generation. Annotation trên typed component/renderer/action handler sinh component registry, renderer registry, serializer registry, action registry, capability registry và dispatcher typed. Render path dùng `LuaNode → generated dispatcher → typed renderer → @Composable`.

Không dùng `Class.forName()`, runtime annotation scan, reflection hay dynamic invoke trong hot path.

## Hệ quả

- Dispatch và registration có thể kiểm tra ở compile time và hoạt động ổn định trên target KMP.
- Custom component không yêu cầu manual registration trong expected developer experience.
- Build complexity/codegen diagnostics trở thành phần quan trọng của DX.
- Cần thiết kế versioning, cross-module discovery và error diagnostics tốt cho generated artifacts.

## Các lựa chọn đã không chọn

- **Reflection registry:** dễ prototype hơn nhưng rủi ro performance/portable behavior.
- **Manual registration:** minh bạch nhưng dễ quên, lặp và tạo runtime missing handler.
- **String-key dynamic renderer map trên hot path:** bỏ qua type checking và tạo lỗi muộn.

## Liên quan

- [Runtime & rendering](../architecture/runtime.md)
- [Platform & integration](../architecture/platform.md#custom-components-và-generated-integration)
- [Quality & operations](../architecture/quality.md#hiệu-năng-đo-end-to-end)
