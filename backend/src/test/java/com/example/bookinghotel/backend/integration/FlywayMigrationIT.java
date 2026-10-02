package com.example.bookinghotel.backend.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class FlywayMigrationIT {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    Flyway flyway(String target) {
        return Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target(target).cleanDisabled(false).load();
    }
    @Test void historicalV3ToV4ToV5AndReplay_preservesDataAndConvertsOnlyOnce() throws Exception {
        flyway("3").clean(); flyway("3").migrate();
        try (Connection c=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
             Statement sql=c.createStatement()) {
            sql.executeUpdate("INSERT INTO users(id,email,password_hash,display_name,role,enabled,created_at) VALUES(1,'migration@test','hash','Existing user','USER',TRUE,NOW(6))");
            sql.executeUpdate("UPDATE rooms SET available_rooms=7 WHERE id=1");
            sql.executeUpdate("INSERT INTO bookings(id,room_id,user_id,quantity,check_in_date,check_out_date,guests,nights,total_price,status,created_at) VALUES(1,1,1,2,'2026-12-01','2026-12-02',2,1,100,'FAILED',NOW(6)),(2,1,1,1,'2026-12-01','2026-12-02',1,1,50,'PROCESSING',NOW(6))");
            sql.executeUpdate("INSERT INTO payments(booking_id,method,status,idempotency_key,provider_reference,amount_vnd,created_at) VALUES(1,'VNPAY','FAILED','old','old-ref',2500000,NOW(6)),(2,'VNPAY','PENDING','pending','pending-ref',NULL,NOW(6))");
            flyway("4").migrate();
            assertEquals(9,number(sql,"SELECT available_rooms FROM rooms WHERE id=1"));
            assertEquals(1,number(sql,"SELECT inventory_released FROM bookings WHERE id=1"));
            assertEquals(1,number(sql,"SELECT COUNT(*) FROM bookings WHERE id=2 AND reservation_expires_at IS NOT NULL AND inventory_released=FALSE"));
            assertEquals(1,number(sql,"SELECT COUNT(*) FROM users WHERE email='migration@test'"));
            assertEquals(2,number(sql,"SELECT COUNT(*) FROM payments"));
            flyway("5").migrate();
            assertEquals(1250000,number(sql,"SELECT price_per_night FROM rooms WHERE id=1"));
            assertEquals(2000000,number(sql,"SELECT price_per_night FROM rooms WHERE id=2"));
            assertEquals(3000000,number(sql,"SELECT price_per_night FROM rooms WHERE id=3"));
            assertEquals(2500000,number(sql,"SELECT total_price FROM bookings WHERE id=1"));
            assertEquals(2500000,number(sql,"SELECT amount_vnd FROM payments WHERE booking_id=1"));
            assertEquals(1250000,number(sql,"SELECT amount_vnd FROM payments WHERE booking_id=2"));
            assertEquals(0,flyway("5").migrate().migrationsExecuted);
            assertEquals(1250000,number(sql,"SELECT price_per_night FROM rooms WHERE id=1"));
            // V6 retires old duplicates and orphan active attempts; generated index then rejects new overlap.
            sql.executeUpdate("INSERT INTO payments(booking_id,method,status,idempotency_key,provider_reference,amount_vnd,created_at) VALUES(2,'VNPAY','PENDING','duplicate','duplicate-ref',1250000,NOW(6)),(1,'VNPAY','PENDING','orphan','orphan-ref',2500000,NOW(6))");
            flyway("6").migrate();
            assertEquals(1,number(sql,"SELECT COUNT(*) FROM payments WHERE status='PENDING'"));
            assertEquals(0,number(sql,"SELECT COUNT(*) FROM payments WHERE booking_id=1 AND status='PENDING'"));
            assertThrows(SQLException.class,()->sql.executeUpdate("INSERT INTO payments(booking_id,method,status,idempotency_key,created_at) VALUES(2,'VNPAY','PENDING','blocked',NOW(6))"));
            assertEquals(0,flyway("6").migrate().migrationsExecuted);
        }
    }
    long number(Statement sql,String query) throws SQLException {
        try(ResultSet r=sql.executeQuery(query)){assertTrue(r.next());return r.getLong(1);}
    }
}
