package com.lab.transfer;

import com.lab.common.BusinessException;
import com.lab.entity.Equipment;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentWorkbookDateSystemTest {
    private final EquipmentWorkbook codec = new EquipmentWorkbook();
    private static final String[] HEADERS = {"设备编号", "设备名称", "实验室ID", "型号", "制造商", "采购日期", "设备说明"};

    /** Create real XLSX bytes with either a date-formatted numeric cell or a literal string. */
    private byte[] workbook(boolean date1904, Double serial, String dateText, boolean dateFormatted) throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            book.getCTWorkbook().getWorkbookPr().setDate1904(date1904);
            Sheet sheet = book.createSheet("设备");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) header.createCell(i).setCellValue(HEADERS[i]);
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("EQ-DATE");
            row.createCell(1).setCellValue("设备");
            row.createCell(2).setCellValue("1");
            Cell cell = row.createCell(5);
            if (serial != null) {
                cell.setCellValue(serial);
                if (dateFormatted) {
                    CellStyle style = book.createCellStyle();
                    style.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
                    cell.setCellStyle(style);
                }
            } else if (dateText != null) {
                cell.setCellValue(dateText);
            }
            book.write(out);
            byte[] bytes = out.toByteArray();
            try (XSSFWorkbook reopened = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertEquals(date1904, reopened.isDate1904(), "the date system must survive serialization");
            }
            return bytes;
        }
    }

    private LocalDate readNumeric(boolean date1904, double serial) throws Exception {
        return codec.read(workbook(date1904, serial, null, true)).get(0).getPurchaseDate();
    }

    @Test void importsSameCalendarDateFromReal1900And1904Workbooks() throws Exception {
        assertEquals(LocalDate.of(2026, 10, 31), readNumeric(false, 46326));
        assertEquals(LocalDate.of(2026, 10, 31), readNumeric(true, 44864));
        assertEquals(LocalDate.of(2026, 10, 31), readNumeric(true, 44864.5));
    }

    @Test void acceptsTextAndBlankRegardlessOfWorkbookDateSystem() throws Exception {
        for (boolean date1904 : new boolean[]{false, true}) {
            assertEquals(LocalDate.of(2026, 10, 31), codec.read(workbook(date1904, null, "2026-10-31", false)).get(0).getPurchaseDate());
            assertNull(codec.read(workbook(date1904, null, null, false)).get(0).getPurchaseDate());
            assertThrows(BusinessException.class, () -> codec.read(workbook(date1904, null, "2026-02-30", false)));
            assertThrows(BusinessException.class, () -> codec.read(workbook(date1904, null, "1899-12-31", false)));
            assertThrows(BusinessException.class, () -> codec.read(workbook(date1904, 44864d, null, false)));
        }
    }

    @Test void checksBothEpochsAndCalendarYearBounds() throws Exception {
        assertEquals(LocalDate.of(1900, 1, 1), readNumeric(false, 1));
        assertEquals(LocalDate.of(1900, 2, 28), readNumeric(false, 59));
        assertEquals(LocalDate.of(1900, 3, 1), readNumeric(false, 61));
        assertEquals(LocalDate.of(1904, 1, 1), readNumeric(true, 0));
        assertEquals(LocalDate.of(9999, 12, 31), readNumeric(false, 2958465));
        assertEquals(LocalDate.of(9999, 12, 31), readNumeric(true, 2957003));
        assertThrows(BusinessException.class, () -> readNumeric(false, -1));
        assertThrows(BusinessException.class, () -> readNumeric(true, -1));
        assertThrows(BusinessException.class, () -> readNumeric(false, 0));
        assertThrows(BusinessException.class, () -> readNumeric(false, 60)); // Excel's fictitious 1900-02-29
        assertThrows(BusinessException.class, () -> readNumeric(false, 2958466));
        assertThrows(BusinessException.class, () -> readNumeric(true, 2957004));
    }

    @Test void exportedTextDatesRoundTrip() {
        Equipment equipment = new Equipment();
        equipment.setEquipmentCode("EQ-1"); equipment.setName("设备"); equipment.setLabId(1L);
        equipment.setPurchaseDate(LocalDate.of(2026, 10, 31));
        assertEquals(LocalDate.of(2026, 10, 31), codec.read(codec.write(Collections.singletonList(equipment))).get(0).getPurchaseDate());
    }
}
