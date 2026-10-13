package iuh.fit.reportservice.infrastructure.report;

/** File báo cáo đã dựng: dữ liệu, số dòng và tiền tố tên file (worker gắn thời điểm + đuôi). */
record RenderedExport(byte[] data, int rows, String filePrefix) {
}
