package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.*;
import java.util.*;

/** 历史8表库可能尚无邮箱验证和会话版本列；仅补列，不改联系方式、密码或业务数据。 */
public class V2__Account_security_columns extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Set<String> columns = new HashSet<>();
        try (ResultSet result = connection.getMetaData().getColumns(connection.getCatalog(), null, "user", "%")) {
            while (result.next()) columns.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        for (String name : Arrays.asList("email_verified", "phone_verified", "auth_version")) {
            if (!columns.contains(name)) {
                try (Statement ddl = connection.createStatement()) {
                    ddl.execute("ALTER TABLE `user` ADD COLUMN " + name + (name.equals("auth_version") ? " INT" : " TINYINT") + " NOT NULL DEFAULT 0");
                }
            }
        }
    }
}
