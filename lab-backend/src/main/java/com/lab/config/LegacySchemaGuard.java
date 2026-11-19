package com.lab.config;

import java.sql.*;
import java.util.*;

/** 只读元数据的存量接管预检，不读取或修改任何用户/业务值。 */
public final class LegacySchemaGuard {
    private LegacySchemaGuard() { }
    private static final Map<String, Set<String>> CORE = new LinkedHashMap<>();
    static {
        add("user", "id", "username", "password", "role");
        add("lab", "id", "admin_id");
        add("equipment", "id", "lab_id", "equipment_code", "status");
        add("reservation", "id", "student_id", "equipment_id", "start_time", "end_time", "status");
        add("blacklist", "id", "student_id", "expire_time");
        add("repair_order", "id", "equipment_id", "reporter_id", "status");
        add("repair_record", "id", "repair_order_id", "operator_id", "to_status");
        add("usage_log", "id", "reservation_id", "start_time", "end_time");
    }
    private static void add(String table, String... columns) { CORE.put(table, new HashSet<>(Arrays.asList(columns))); }
    public static Set<String> tables(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String schema = tables.getString("TABLE_SCHEM");
                if ("INFORMATION_SCHEMA".equalsIgnoreCase(schema)) continue;
                names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }
    public static boolean isKnownLegacy(Connection connection) throws SQLException {
        Set<String> tables = tables(connection);
        if (!tables.equals(CORE.keySet())) return false;
        for (Map.Entry<String, Set<String>> table : CORE.entrySet()) {
            Set<String> columns = new HashSet<>();
            try (ResultSet rows = connection.getMetaData().getColumns(connection.getCatalog(), null, table.getKey(), "%")) {
                while (rows.next()) columns.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
            if (!columns.containsAll(table.getValue())) return false;
        }
        return true;
    }
}
