-- Additive schema for owner roles and accountable cashier shifts.
CREATE TABLE IF NOT EXISTS ca_thu_ngan (
    ma_ca INT AUTO_INCREMENT PRIMARY KEY,
    ma_nv INT NOT NULL,
    mo_luc DATETIME NOT NULL,
    tien_dau_ca DECIMAL(15,2) NOT NULL,
    trang_thai VARCHAR(10) NOT NULL,
    dong_luc DATETIME,
    tien_dem DECIMAL(15,2),
    tien_ky_vong DECIMAL(15,2),
    chenh_lech DECIMAL(15,2),
    ghi_chu_dong VARCHAR(255),
    ma_nv_dong INT,
    CONSTRAINT fk_ca_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT fk_ca_nv_dong FOREIGN KEY (ma_nv_dong) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_ca_trang_thai CHECK (trang_thai IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_ca_tien CHECK (tien_dau_ca >= 0 AND (tien_dem IS NULL OR tien_dem >= 0))
);
CREATE TABLE IF NOT EXISTS ngan_ke_toan (
    ma_ngan INT PRIMARY KEY,
    ma_ca_dang_mo INT,
    CONSTRAINT ck_ngan_ke_duy_nhat CHECK (ma_ngan = 1),
    CONSTRAINT fk_ngan_ca FOREIGN KEY (ma_ca_dang_mo) REFERENCES ca_thu_ngan (ma_ca)
);
CREATE TABLE IF NOT EXISTS bien_dong_tien_mat (
    ma_bd INT AUTO_INCREMENT PRIMARY KEY,
    ma_ca INT NOT NULL,
    ma_nv INT NOT NULL,
    thoi_diem DATETIME NOT NULL,
    loai VARCHAR(10) NOT NULL,
    so_tien DECIMAL(15,2) NOT NULL,
    ly_do VARCHAR(255) NOT NULL,
    ma_yeu_cau VARCHAR(64),
    CONSTRAINT uq_bd_yeu_cau UNIQUE (ma_yeu_cau),
    CONSTRAINT fk_bd_ca FOREIGN KEY (ma_ca) REFERENCES ca_thu_ngan (ma_ca),
    CONSTRAINT fk_btm_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_bd_tien_loai CHECK (loai IN ('THEM', 'CHI', 'RUT')),
    CONSTRAINT ck_bd_tien CHECK (so_tien > 0)
);
CREATE TABLE IF NOT EXISTS hoan_tien (
    ma_ht INT AUTO_INCREMENT PRIMARY KEY,
    ma_hd INT NOT NULL,
    ma_ca INT,
    ma_nv INT NOT NULL,
    phuong_thuc VARCHAR(20) NOT NULL,
    so_tien DECIMAL(15,2) NOT NULL,
    thoi_diem DATETIME NOT NULL,
    ly_do VARCHAR(255) NOT NULL,
    CONSTRAINT uq_ht_hd UNIQUE (ma_hd),
    CONSTRAINT fk_ht_hd FOREIGN KEY (ma_hd) REFERENCES hoa_don (ma_hd),
    CONSTRAINT fk_ht_ca FOREIGN KEY (ma_ca) REFERENCES ca_thu_ngan (ma_ca),
    CONSTRAINT fk_ht_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_ht_pt CHECK (phuong_thuc IN ('TIEN_MAT', 'CHUYEN_KHOAN', 'THE')),
    CONSTRAINT ck_ht_tien CHECK (so_tien >= 0)
);
