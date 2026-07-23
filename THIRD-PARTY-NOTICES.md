# Third-party notices

弦序自身从 `v0.1.0-beta.2` 起采用 GNU GPLv3 发布。本文件仅说明随源码或二进制使用的第三方组件；这些组件仍由各自许可证管理。

The Windows application package includes unmodified runtime libraries and their embedded license metadata, including:

- Eclipse Temurin / OpenJDK 21 — GPLv2 with the Classpath Exception. The packaged runtime retains its `runtime/legal` notices.
- Kotlin — Apache License 2.0.
- Compose Multiplatform and related JetBrains/AndroidX runtime libraries — Apache License 2.0.
- Java Native Access (JNA) — distributed under its available Apache License 2.0 terms; its JAR retains `META-INF/AL2.0`, `META-INF/LGPL2.1`, and `META-INF/LICENSE`.
- Skiko and kotlinx libraries — their upstream open-source licenses and embedded metadata apply.

The Full package bundles the following audio components. The Lite package does not bundle them; its optional setup script downloads them from their upstream distribution channels:

- Python 3.10 embedded runtime — Python Software Foundation License.
- Basic Pitch — Apache License 2.0.
- Demucs — MIT License.
- PyTorch and related Python dependencies — their respective upstream licenses.
- FFmpeg GPL shared build — GNU GPL and the notices included by the downloaded build.

This file is informational and does not replace the complete license texts shipped with each component. Upstream projects and trademarks belong to their respective owners.
