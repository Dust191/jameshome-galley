package io.github.jameshome.galley.modules.export.application;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.modules.material.infrastructure.MaterialListRow;
import io.github.jameshome.galley.modules.material.infrastructure.MaterialMapper;
import io.github.jameshome.galley.modules.stock.infrastructure.MovementListRow;
import io.github.jameshome.galley.modules.stock.infrastructure.StockMovementMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 物料导出。
 *
 * <p>两点必须做对，否则"Excel 打开是乱码"会成为最常见的反馈：
 *
 * <ul>
 *   <li>文件头写 <b>UTF-8 BOM</b>（{@code EF BB BF}）。Excel 不认无 BOM 的 UTF-8，会按本地编码解。
 *   <li>字段按需要加引号：含逗号、引号、换行的值必须包裹，否则列会错位。
 * </ul>
 *
 * <p>行数上限 50000：导出不是分页查询，没有上限的话一次误操作就能把内存打满。
 */
@Service
public class MaterialExportService {

    private static final int MAX_ROWS = 50_000;

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final String[] HEADER = {
        "自编码", "物料名", "分类", "单位", "规格", "安全库存", "保质期(天)", "当前结存", "状态", "备注"
    };

    private static final String[] MOVEMENT_HEADER = {
        "发生时间", "物料", "单位", "类型", "数量", "操作人", "备注", "是否已冲正"
    };

    /** 导出给人看的时间，用上海时区的 {@code yyyy-MM-dd HH:mm:ss}，Excel 可直接识别。 */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final MaterialMapper materialMapper;
    private final StockMovementMapper movementMapper;

    public MaterialExportService(
            MaterialMapper materialMapper, StockMovementMapper movementMapper) {
        this.materialMapper = materialMapper;
        this.movementMapper = movementMapper;
    }

    /** 受当前筛选约束，不受分页限制（DES-002 §4.6）。 */
    public byte[] exportMaterials(
            String keyword, String categoryId, String stockState, String status) {
        List<MaterialListRow> rows =
                materialMapper.search(
                        blankToNull(keyword),
                        parseUuid(categoryId),
                        blankToNull(stockState),
                        blankToNull(status),
                        "m.name ASC, m.id ASC",
                        MAX_ROWS + 1,
                        0);

        if (rows.size() > MAX_ROWS) {
            throw new BusinessException(
                    ErrorCode.EXPORT_TOO_LARGE, "导出结果超过 " + MAX_ROWS + " 行，请缩小筛选范围");
        }

        StringBuilder sb = new StringBuilder(rows.size() * 96);
        appendRow(sb, HEADER);
        for (MaterialListRow row : rows) {
            appendRow(
                    sb,
                    new String[] {
                        nullToEmpty(row.getCode()),
                        nullToEmpty(row.getName()),
                        nullToEmpty(row.getCategoryName()),
                        nullToEmpty(row.getUnitName()),
                        nullToEmpty(row.getSpec()),
                        decimal(row.getSafetyStock()),
                        row.getShelfLifeDays() == null ? "" : row.getShelfLifeDays().toString(),
                        decimal(row.getCurrentStock()),
                        nullToEmpty(row.getStatus()),
                        nullToEmpty(row.getNote())
                    });
        }

        return withBom(sb.toString());
    }

    private static void appendRow(StringBuilder sb, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(cells[i]));
        }
        // Excel 对 CRLF 更友好
        sb.append("\r\n");
    }

    /** 含逗号、双引号、换行时用双引号包裹，内部双引号翻倍——RFC 4180 的最小实现。 */
    static String quote(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needQuote =
                value.indexOf(',') >= 0
                        || value.indexOf('"') >= 0
                        || value.indexOf('\n') >= 0
                        || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static java.util.UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.util.UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "categoryId 格式不正确", "categoryId");
        }
    }

    /** 流水导出；同样受筛选约束、同样带 BOM。 */
    public byte[] exportMovements(String materialId, String type, String from, String to) {
        List<MovementListRow> rows =
                movementMapper.search(
                        parseUuid(materialId),
                        blankToNull(type),
                        parseInstant(from),
                        parseInstant(to),
                        MAX_ROWS + 1,
                        0);

        if (rows.size() > MAX_ROWS) {
            throw new BusinessException(
                    ErrorCode.EXPORT_TOO_LARGE, "导出结果超过 " + MAX_ROWS + " 行，请缩小时间范围");
        }

        StringBuilder sb = new StringBuilder(rows.size() * 80);
        appendRow(sb, MOVEMENT_HEADER);
        for (MovementListRow row : rows) {
            appendRow(
                    sb,
                    new String[] {
                        row.getOccurredAt() == null ? "" : TIME_FORMAT.format(row.getOccurredAt()),
                        nullToEmpty(row.getMaterialName()),
                        nullToEmpty(row.getUnitName()),
                        nullToEmpty(row.getType()),
                        decimal(row.getQuantity()),
                        nullToEmpty(row.getOperatorName()),
                        nullToEmpty(row.getNote()),
                        Boolean.TRUE.equals(row.getReversed()) ? "是" : "否"
                    });
        }
        return withBom(sb.toString());
    }

    private static byte[] withBom(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, result, UTF8_BOM.length, body.length);
        return result;
    }

    private static java.time.Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "时间格式应为 ISO 8601", "time");
        }
    }
}
