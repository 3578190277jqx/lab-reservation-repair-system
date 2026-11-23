package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Pattern;

/** 扩展预约状态。保留V1历史，存量库如存在未知状态约束则停止以便人工核对。 */
public class V4__Reservation_pending_expiry extends BaseJavaMigration {
    private static final Pattern STATUS = Pattern.compile("(?i)(?<![a-z0-9_])status(?![a-z0-9_])");

    @Override public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        String product = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        try (Statement s = c.createStatement()) {
            if (product.contains("mysql")) {
                boolean known = false;
                String sql = "SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE "
                    + "FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc "
                    + "ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME "
                    + "WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='reservation' AND tc.CONSTRAINT_TYPE='CHECK'";
                try (PreparedStatement q = c.prepareStatement(sql); ResultSet rs = q.executeQuery()) {
                    while (rs.next()) {
                        String name=rs.getString(1), clause=rs.getString(2);
                        if ("chk_reservation_status".equalsIgnoreCase(name)) {
                            if (!isKnownStatusCheck(clause))
                                throw new SQLException("预约状态CHECK定义与已知结构不一致，请备份并人工核对后再迁移");
                            known=true;
                        }
                        else if (clause != null && STATUS.matcher(clause).find())
                            throw new SQLException("预约状态存在未识别的CHECK约束 " + name + "，请备份并人工核对后再迁移");
                    }
                }
                // DROP和ADD放进同一个MySQL ALTER，避免两次DDL之间留下无状态约束的窗口。
                String prefix = known ? "ALTER TABLE reservation DROP CHECK chk_reservation_status, " : "ALTER TABLE reservation ";
                s.execute(prefix + "ADD CONSTRAINT chk_reservation_status CHECK (status IN (0,1,2,3,4,5))");
            } else if (product.contains("h2")) {
                // 仅用于隔离的H2测试；线上数据库使用MySQL分支。
                s.execute("ALTER TABLE reservation DROP CONSTRAINT IF EXISTS chk_reservation_status");
                s.execute("ALTER TABLE reservation ADD CONSTRAINT chk_reservation_status CHECK (status IN (0,1,2,3,4,5))");
            } else throw new SQLException("不支持的数据库，无法迁移预约状态约束: " + product);
        }
    }

    static boolean isKnownStatusCheck(String clause) {
        if (clause == null) return false;
        String normalized = clause.replaceAll("[`\\s()]", "").toLowerCase(Locale.ROOT);
        return "statusin0,1,2,3,4".equals(normalized) || "statusin0,1,2,3,4,5".equals(normalized);
    }
}
