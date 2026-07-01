package com.yy.writingwithai.core.data.export

/**
 * fix-2026-06-24-review-r1-critical · 导入被拒(典型场景:zip 含 Zip Slip 路径穿越 entry)。
 *
 * 抛此异常时 import 整体终止，UI 渲染"import 文件损坏"。
 */
class ImportRejected(reason: String) : RuntimeException(reason)
