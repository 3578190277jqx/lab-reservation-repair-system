package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationExpiryMigrationTest {
    @Test void knownConstraintDescriptionsAreStrictlyValidated() {
        assertTrue(V4__Reservation_pending_expiry.isKnownStatusCheck("((`status` in (0,1,2,3,4)))"));
        assertTrue(V4__Reservation_pending_expiry.isKnownStatusCheck("(`status` IN (0, 1, 2, 3, 4, 5))"));
        assertFalse(V4__Reservation_pending_expiry.isKnownStatusCheck("(`status` in (0,1,2,3,4) AND `student_id` > 0)"));
        assertFalse(V4__Reservation_pending_expiry.isKnownStatusCheck(null));
    }
    @Test void h2ReplacesKnownCheckAndRetryPreservesRowsAndRejectsInvalidStatuses() throws Exception {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        try(Connection c=ds.getConnection()) {
            c.createStatement().execute("CREATE TABLE reservation(id BIGINT PRIMARY KEY,status INT, CONSTRAINT chk_reservation_status CHECK (status IN (0,1,2,3,4)))");
            c.createStatement().execute("INSERT INTO reservation VALUES(1,0)");
            Context context=mock(Context.class);when(context.getConnection()).thenReturn(c);
            V4__Reservation_pending_expiry migration=new V4__Reservation_pending_expiry();
            migration.migrate(context);c.createStatement().execute("UPDATE reservation SET status=5 WHERE id=1");
            migration.migrate(context);
            try(ResultSet r=c.createStatement().executeQuery("SELECT status FROM reservation WHERE id=1")){assertTrue(r.next());assertEquals(5,r.getInt(1));}
            assertThrows(SQLException.class,()->c.createStatement().execute("UPDATE reservation SET status=6 WHERE id=1"));
        }
    }
    @Test void mysqlUnknownNamedDefinitionOrOtherStatusCheckDoesNotIssueAlter() throws Exception {
        verifyMySqlSafety("chk_reservation_status", "(`status` IN (0,1,2,3,4) AND student_id > 0)");
        verifyMySqlSafety("custom_check", "(`status` < 5)");
    }
    private void verifyMySqlSafety(String constraint,String clause) throws Exception {
        Connection c=mock(Connection.class);DatabaseMetaData md=mock(DatabaseMetaData.class);when(c.getMetaData()).thenReturn(md);when(md.getDatabaseProductName()).thenReturn("MySQL");
        Statement ddl=mock(Statement.class);when(c.createStatement()).thenReturn(ddl);
        PreparedStatement query=mock(PreparedStatement.class);when(c.prepareStatement(anyString())).thenReturn(query);
        ResultSet rs=mock(ResultSet.class);when(query.executeQuery()).thenReturn(rs);when(rs.next()).thenReturn(true,false);when(rs.getString(1)).thenReturn(constraint);when(rs.getString(2)).thenReturn(clause);
        Context context=mock(Context.class);when(context.getConnection()).thenReturn(c);
        assertThrows(SQLException.class,()->new V4__Reservation_pending_expiry().migrate(context));
        verify(ddl,never()).execute(anyString());
    }
}
