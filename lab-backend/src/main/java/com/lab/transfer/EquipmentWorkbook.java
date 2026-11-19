package com.lab.transfer;

import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.entity.Equipment;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 只处理有界、纯数据的XLSX，绝不执行公式或下载外部资源。 */
@Component
public class EquipmentWorkbook {
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_ROWS = 500;
    private static final long MAX_EXPANDED_BYTES = 8L * 1024 * 1024;
    private static final String[] HEADERS = {"设备编号", "设备名称", "实验室ID", "型号", "制造商", "采购日期", "设备说明"};

    public List<EquipmentRow> read(byte[] bytes) {
        validateArchive(bytes);
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (book.getNumberOfSheets() != 1) throw bad("仅支持含一张工作表的设备模板");
            Sheet sheet = book.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS) throw bad("一次最多导入500行，请拆分文件");
            Row header = sheet.getRow(0);
            if (header == null || header.getLastCellNum() != HEADERS.length) throw bad("表头与设备模板不一致，请重新下载模板");
            for (int c = 0; c < HEADERS.length; c++) {
                if (!HEADERS[c].equals(text(header.getCell(c)))) throw bad("表头与设备模板不一致，请重新下载模板");
            }
            List<EquipmentRow> result = new ArrayList<>();
            for (int n = 1; n <= sheet.getLastRowNum(); n++) {
                Row row = sheet.getRow(n);
                if (row == null) continue;
                if (row.getLastCellNum() > HEADERS.length) throw bad("第" + (n + 1) + "行含模板之外的列");
                String[] cells = new String[HEADERS.length];
                boolean empty = true;
                for (int c = 0; c < cells.length; c++) {
                    cells[c] = text(row.getCell(c));
                    if (!cells[c].isEmpty()) empty = false;
                }
                if (empty) continue;
                EquipmentRow equipment = new EquipmentRow();
                equipment.setRowNumber(n + 1);
                equipment.setEquipmentCode(cells[0]); equipment.setName(cells[1]);
                try {
                    long id = new BigDecimal(cells[2]).longValueExact();
                    if (id <= 0) throw new NumberFormatException();
                    equipment.setLabId(id);
                } catch (RuntimeException ex) { throw bad("第" + (n + 1) + "行实验室ID必须为正整数"); }
                equipment.setModel(optional(cells[3])); equipment.setManufacturer(optional(cells[4]));
                equipment.setPurchaseDate(date(row.getCell(5), cells[5], n + 1, book.isDate1904()));
                equipment.setDescription(optional(cells[6])); equipment.setStatus(0);
                result.add(equipment);
            }
            if (result.isEmpty()) throw bad("没有可导入的设备数据");
            return result;
        } catch (BusinessException ex) { throw ex;
        } catch (IOException | RuntimeException ex) { throw bad("无法读取XLSX，请使用设备模板且不要包含公式或加密内容"); }
    }

    private LocalDate date(Cell cell, String value, int row, boolean date1904) {
        if (value.isEmpty()) return null;
        try {
            LocalDate date;
            if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                double serial = cell.getNumericCellValue();
                double lastDay = DateUtil.getExcelDate(LocalDate.of(9999, 12, 31), date1904);
                // Excel 1900系统将不存在的1900-02-29编码为60；不能悄悄映射成真实日期。
                if (!Double.isFinite(serial) || !DateUtil.isValidExcelDate(serial) || serial >= lastDay + 1
                        || (!date1904 && serial >= 60 && serial < 61)) throw new IllegalArgumentException();
                date = DateUtil.getLocalDateTime(serial, date1904).toLocalDate();
            } else {
                date = LocalDate.parse(value);
            }
            if (date.getYear() < 1900 || date.getYear() > 9999) throw new IllegalArgumentException();
            return date;
        } catch (RuntimeException ex) { throw bad("第" + row + "行采购日期须为yyyy-MM-dd或有效Excel日期"); }
    }

    private String text(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return NumberToTextConverter.toText(cell.getNumericCellValue());
        if (cell.getCellType() == CellType.FORMULA) throw bad("设备导入不接受公式，请粘贴为纯文本或数值");
        throw bad("设备导入含不支持的单元格类型");
    }

    /** 在POI分配XML对象之前，实际解压计数，限制ZIP膨胀、重复条目和二进制嵌入。 */
    private void validateArchive(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw bad("请选择XLSX设备文件");
        if (bytes.length > MAX_FILE_BYTES) throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "设备文件最大2 MiB");
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') throw bad("只支持XLSX文件");
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            Set<String> names = new HashSet<>(); long total = 0; int count = 0; boolean workbook = false;
            ZipEntry entry; byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (++count > 200 || !names.add(name) || name.contains("..") || name.contains("\\") || name.startsWith("/")
                        || name.endsWith(".bin") || name.startsWith("xl/embeddings/") || name.startsWith("xl/media/")) {
                    throw bad("文件包含不支持的压缩条目、图片或嵌入内容");
                }
                if (name.equals("xl/workbook.xml")) workbook = true;
                long size = 0; int read;
                while ((read = zip.read(buffer)) != -1) {
                    size += read; total += read;
                    if (size > MAX_EXPANDED_BYTES / 2 || total > MAX_EXPANDED_BYTES) throw bad("设备文件展开后过大，请移除多余内容并拆分");
                }
            }
            if (!workbook) throw bad("文件不是有效的XLSX工作簿");
        } catch (BusinessException ex) { throw ex;
        } catch (IOException ex) { throw bad("XLSX文件已损坏"); }
    }

    public byte[] template() { return write(Collections.emptyList()); }

    public byte[] write(List<Equipment> equipment) {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("设备");
            CellStyle textStyle = book.createCellStyle(); textStyle.setDataFormat(book.createDataFormat().getFormat("@"));
            for (int c = 0; c < HEADERS.length; c++) { sheet.setColumnWidth(c, (c == 6 ? 45 : 22) * 256); sheet.setDefaultColumnStyle(c, textStyle); }
            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) header.createCell(c, CellType.STRING).setCellValue(HEADERS[c]);
            int n = 1;
            for (Equipment e : equipment) {
                Row row = sheet.createRow(n++);
                String[] values = {e.getEquipmentCode(), e.getName(), String.valueOf(e.getLabId()), e.getModel(), e.getManufacturer(),
                        e.getPurchaseDate() == null ? "" : e.getPurchaseDate().toString(), e.getDescription()};
                for (int c = 0; c < values.length; c++) row.createCell(c, CellType.STRING).setCellValue(values[c] == null ? "" : values[c]);
            }
            sheet.createFreezePane(0, 1); book.write(output); return output.toByteArray();
        } catch (IOException ex) { throw new BusinessException(ResultCode.INTERNAL_ERROR, "设备表格生成失败"); }
    }

    private static String optional(String value) { return value.isEmpty() ? null : value; }
    private static BusinessException bad(String message) { return new BusinessException(ResultCode.BAD_REQUEST, message); }
}
