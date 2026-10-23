-- Append-only, auditable corrections to closed cashier-shift reconciliation.
CREATE TABLE IF NOT EXISTS dieu_chinh_ca (
    ma_dc INT AUTO_INCREMENT PRIMARY KEY,
    ma_ca INT NOT NULL,
    ma_nv INT NOT NULL,
    thoi_diem DATETIME NOT NULL,
    tac_dong_ky_vong DECIMAL(15,2) NOT NULL,
    ly_do VARCHAR(255) NOT NULL,
    ma_yeu_cau VARCHAR(64) NOT NULL,
    CONSTRAINT uq_dc_ca_yeu_cau UNIQUE (ma_yeu_cau),
    CONSTRAINT fk_dc_ca FOREIGN KEY (ma_ca) REFERENCES ca_thu_ngan (ma_ca),
    CONSTRAINT fk_dc_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_dc_ca_nonzero CHECK (tac_dong_ky_vong <> 0)
);
