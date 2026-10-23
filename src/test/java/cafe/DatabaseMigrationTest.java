package cafe;

import cafe.config.AppConfig;
import cafe.config.Database;
import cafe.dao.Jdbc;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DatabaseMigrationTest {
    @Test
    void additiveMigrationPreservesExistingAccountsInvoicesAndPaymentsAndCanRerun() {
        Properties props = new Properties();
        props.setProperty("db.url", "jdbc:h2:mem:m" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        Database db = Database.fromConfig(new AppConfig(props));
        db.tx(c -> {
            try (var st = c.createStatement()) {
                st.execute("CREATE TABLE nhan_vien (ma_nv INT PRIMARY KEY, vai_tro VARCHAR(20) NOT NULL, trang_thai BOOLEAN NOT NULL DEFAULT TRUE)");
                st.execute("CREATE TABLE nhat_ky (ma_nk INT PRIMARY KEY)");
                st.execute("CREATE TABLE hoa_don (ma_hd INT PRIMARY KEY)");
                st.execute("CREATE TABLE thanh_toan (ma_tt INT PRIMARY KEY, ma_hd INT NOT NULL)");
            }
            Jdbc.update(c, "INSERT INTO nhan_vien (ma_nv,vai_tro) VALUES (1,'QUAN_LY')");
            Jdbc.update(c, "INSERT INTO hoa_don (ma_hd) VALUES (17)");
            Jdbc.update(c, "INSERT INTO thanh_toan (ma_tt,ma_hd) VALUES (9,17)");
            return null;
        });

        db.migrate();
        db.migrate();

        assertEquals(1, db.read(c -> Jdbc.count(c, "SELECT COUNT(*) FROM nhan_vien WHERE ma_nv=1")).intValue());
        assertEquals(1, db.read(c -> Jdbc.count(c, "SELECT COUNT(*) FROM hoa_don WHERE ma_hd=17")).intValue());
        assertEquals(1, db.read(c -> Jdbc.count(c, "SELECT COUNT(*) FROM thanh_toan WHERE ma_tt=9 AND ma_hd=17")).intValue());
        assertNotNull(db.read(c -> Jdbc.one(c, "SELECT chu_so_huu FROM nhan_vien WHERE ma_nv=1", rs -> rs.getObject(1))));
        assertEquals(1, db.read(c -> Jdbc.count(c, "SELECT COUNT(*) FROM db_schema_version WHERE version=1")).intValue());
        assertEquals(1, db.read(c -> Jdbc.count(c, "SELECT COUNT(*) FROM db_schema_version WHERE version=2")).intValue());
        assertEquals(0, db.read(c -> Jdbc.count(c, "SELECT COUNT(*) FROM dieu_chinh_ca")).intValue());
        assertEquals(1, db.read(c -> Jdbc.count(c, "SELECT COUNT(*) FROM ngan_ke_toan WHERE ma_ngan=1")).intValue());
    }
}
