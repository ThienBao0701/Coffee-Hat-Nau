-- =====================================================================
-- Cafe Management System - database schema (MySQL 8 / H2 in MySQL mode)
-- Runs only on an EMPTY database. It never drops or alters tables.
-- Money: DECIMAL(15,2) (VND, rounded to whole dong by the application)
-- Ingredient quantities: DECIMAL(14,3)
-- =====================================================================

-- ---------- Employees & accounts ----------
CREATE TABLE nhan_vien (
    ma_nv              INT AUTO_INCREMENT PRIMARY KEY,
    ten_nv             VARCHAR(100) NOT NULL,
    chuc_vu            VARCHAR(50),
    so_dien_thoai      VARCHAR(15),
    ngay_sinh          DATE,
    dia_chi            VARCHAR(255),
    bang_cap           VARCHAR(100),
    gio_lam            VARCHAR(50),
    he_so_luong        DECIMAL(5,2)  NOT NULL DEFAULT 1.00,
    luong_co_ban       DECIMAL(15,2) NOT NULL DEFAULT 0,
    ten_dang_nhap      VARCHAR(50)   NOT NULL,
    mat_khau           VARCHAR(100)  NOT NULL,
    vai_tro            VARCHAR(20)   NOT NULL,
    chu_so_huu         BOOLEAN       NOT NULL DEFAULT FALSE,
    phai_doi_mat_khau  BOOLEAN       NOT NULL DEFAULT FALSE,
    trang_thai         BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_nv_ten_dang_nhap UNIQUE (ten_dang_nhap),
    CONSTRAINT ck_nv_vai_tro CHECK (vai_tro IN ('QUAN_LY', 'NHAN_VIEN')),
    CONSTRAINT ck_nv_luong CHECK (he_so_luong >= 0 AND luong_co_ban >= 0)
);

-- ---------- Catalog ----------
CREATE TABLE loai_mon (
    ma_loai   INT AUTO_INCREMENT PRIMARY KEY,
    ten_loai  VARCHAR(100) NOT NULL,
    CONSTRAINT uq_loai_mon_ten UNIQUE (ten_loai)
);

CREATE TABLE mon (
    ma_mon      INT AUTO_INCREMENT PRIMARY KEY,
    ten_mon     VARCHAR(100) NOT NULL,
    ma_loai     INT NOT NULL,
    trang_thai  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_mon_ten UNIQUE (ten_mon),
    CONSTRAINT fk_mon_loai FOREIGN KEY (ma_loai) REFERENCES loai_mon (ma_loai)
);

-- he_so_dinh_luong: recipe multiplier for this size (M = 1.00)
CREATE TABLE size_mon (
    ma_size           INT AUTO_INCREMENT PRIMARY KEY,
    ten_size          VARCHAR(20) NOT NULL,
    he_so_dinh_luong  DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    thu_tu            INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_size_ten UNIQUE (ten_size),
    CONSTRAINT ck_size_he_so CHECK (he_so_dinh_luong > 0)
);

CREATE TABLE topping (
    ma_topping   INT AUTO_INCREMENT PRIMARY KEY,
    ten_topping  VARCHAR(100) NOT NULL,
    gia_them     DECIMAL(15,2) NOT NULL,
    trang_thai   BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_topping_ten UNIQUE (ten_topping),
    CONSTRAINT ck_topping_gia CHECK (gia_them >= 0)
);

-- Price lists never overlap in time (enforced by PriceService), so a product/size has one current price.
CREATE TABLE bang_gia (
    ma_bang_gia    INT AUTO_INCREMENT PRIMARY KEY,
    mo_ta          VARCHAR(200) NOT NULL,
    ngay_bat_dau   DATE NOT NULL,
    ngay_ket_thuc  DATE,
    CONSTRAINT ck_bang_gia_ngay CHECK (ngay_ket_thuc IS NULL OR ngay_ket_thuc >= ngay_bat_dau)
);

CREATE TABLE chi_tiet_bang_gia (
    ma_bang_gia  INT NOT NULL,
    ma_mon       INT NOT NULL,
    ma_size      INT NOT NULL,
    gia_ban      DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (ma_bang_gia, ma_mon, ma_size),
    CONSTRAINT fk_ctbg_bang_gia FOREIGN KEY (ma_bang_gia) REFERENCES bang_gia (ma_bang_gia) ON DELETE CASCADE,
    CONSTRAINT fk_ctbg_mon FOREIGN KEY (ma_mon) REFERENCES mon (ma_mon),
    CONSTRAINT fk_ctbg_size FOREIGN KEY (ma_size) REFERENCES size_mon (ma_size),
    CONSTRAINT ck_ctbg_gia CHECK (gia_ban > 0)
);

-- ---------- Areas & tables ----------
CREATE TABLE khu_vuc (
    ma_khu_vuc   INT AUTO_INCREMENT PRIMARY KEY,
    ten_khu_vuc  VARCHAR(100) NOT NULL,
    CONSTRAINT uq_khu_vuc_ten UNIQUE (ten_khu_vuc)
);

-- trang_thai = table in service (TRUE) or out of service (FALSE).
-- The operational status (empty / occupied / reserved) is DERIVED from open invoices and reservations.
CREATE TABLE ban (
    ma_ban      INT AUTO_INCREMENT PRIMARY KEY,
    ten_ban     VARCHAR(50) NOT NULL,
    ma_khu_vuc  INT NOT NULL,
    so_cho      INT NOT NULL DEFAULT 4,
    trang_thai  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_ban_ten UNIQUE (ten_ban),
    CONSTRAINT fk_ban_khu_vuc FOREIGN KEY (ma_khu_vuc) REFERENCES khu_vuc (ma_khu_vuc),
    CONSTRAINT ck_ban_so_cho CHECK (so_cho > 0)
);

-- ---------- Customers & reservations ----------
CREATE TABLE khach_hang (
    ma_kh          INT AUTO_INCREMENT PRIMARY KEY,
    ten_kh         VARCHAR(100) NOT NULL,
    ngay_sinh      DATE,
    so_cmnd        VARCHAR(20),
    so_dien_thoai  VARCHAR(15),
    dia_chi        VARCHAR(255),
    CONSTRAINT uq_kh_cmnd UNIQUE (so_cmnd),
    CONSTRAINT uq_kh_sdt UNIQUE (so_dien_thoai)
);

CREATE TABLE don_dat_hang (
    ma_ddh         INT AUTO_INCREMENT PRIMARY KEY,
    ma_kh          INT NOT NULL,
    ma_ban         INT NOT NULL,
    ma_nv          INT NOT NULL,
    thoi_diem_dat  DATETIME NOT NULL,
    so_nguoi       INT NOT NULL DEFAULT 1,
    ghi_chu        VARCHAR(255),
    trang_thai     VARCHAR(20) NOT NULL,
    ngay_tao       DATETIME NOT NULL,
    CONSTRAINT fk_ddh_kh FOREIGN KEY (ma_kh) REFERENCES khach_hang (ma_kh),
    CONSTRAINT fk_ddh_ban FOREIGN KEY (ma_ban) REFERENCES ban (ma_ban),
    CONSTRAINT fk_ddh_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_ddh_trang_thai CHECK (trang_thai IN ('CHO_XAC_NHAN', 'DA_XAC_NHAN', 'DA_HUY', 'HOAN_THANH')),
    CONSTRAINT ck_ddh_so_nguoi CHECK (so_nguoi > 0)
);
CREATE INDEX idx_ddh_ban_thoi_diem ON don_dat_hang (ma_ban, thoi_diem_dat);

-- ---------- Promotions & taxes ----------
-- A promotion type is EITHER a percentage OR a fixed amount.
CREATE TABLE loai_khuyen_mai (
    ma_loai_km      INT AUTO_INCREMENT PRIMARY KEY,
    ten_loai_km     VARCHAR(100) NOT NULL,
    phan_tram_giam  DECIMAL(5,2),
    phan_tien_giam  DECIMAL(15,2),
    CONSTRAINT ck_lkm_mot_kieu CHECK (
        (phan_tram_giam IS NOT NULL AND phan_tien_giam IS NULL AND phan_tram_giam > 0 AND phan_tram_giam <= 100)
     OR (phan_tien_giam IS NOT NULL AND phan_tram_giam IS NULL AND phan_tien_giam > 0))
);

CREATE TABLE khuyen_mai (
    ma_km                  INT AUTO_INCREMENT PRIMARY KEY,
    ten_km                 VARCHAR(100) NOT NULL,
    ma_loai_km             INT NOT NULL,
    ngay_bat_dau           DATE NOT NULL,
    ngay_ket_thuc          DATE NOT NULL,
    gia_tri_don_toi_thieu  DECIMAL(15,2) NOT NULL DEFAULT 0,
    giam_toi_da            DECIMAL(15,2),
    trang_thai             BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_km_loai FOREIGN KEY (ma_loai_km) REFERENCES loai_khuyen_mai (ma_loai_km),
    CONSTRAINT ck_km_ngay CHECK (ngay_ket_thuc >= ngay_bat_dau),
    CONSTRAINT ck_km_gia_tri CHECK (gia_tri_don_toi_thieu >= 0 AND (giam_toi_da IS NULL OR giam_toi_da > 0))
);

-- Tax periods never overlap (enforced by PromotionService), so at most one tax rate is active on a date.
CREATE TABLE thue (
    ma_thue        INT AUTO_INCREMENT PRIMARY KEY,
    ten_thue       VARCHAR(100) NOT NULL,
    phan_tram      DECIMAL(5,2) NOT NULL,
    ngay_bat_dau   DATE NOT NULL,
    ngay_ket_thuc  DATE,
    CONSTRAINT ck_thue_phan_tram CHECK (phan_tram >= 0 AND phan_tram <= 100),
    CONSTRAINT ck_thue_ngay CHECK (ngay_ket_thuc IS NULL OR ngay_ket_thuc >= ngay_bat_dau)
);

-- ---------- Invoices ----------
-- Snapshot columns (ten_km, thue_suat, tam_tinh, tien_giam, tien_thue, tong_tien) keep issued invoices
-- unchanged when promotions / taxes are edited later. They are filled when the invoice is paid.
CREATE TABLE hoa_don (
    ma_hd          INT AUTO_INCREMENT PRIMARY KEY,
    ngay_lap       DATETIME NOT NULL,
    loai_don       VARCHAR(20) NOT NULL,
    trang_thai     VARCHAR(20) NOT NULL,
    ma_ban         INT,
    ma_kh          INT,
    ma_nv          INT NOT NULL,
    ma_km          INT,
    ten_km         VARCHAR(100),
    ma_thue        INT,
    thue_suat      DECIMAL(5,2)  NOT NULL DEFAULT 0,
    tam_tinh       DECIMAL(15,2) NOT NULL DEFAULT 0,
    tien_giam      DECIMAL(15,2) NOT NULL DEFAULT 0,
    tien_thue      DECIMAL(15,2) NOT NULL DEFAULT 0,
    tong_tien      DECIMAL(15,2) NOT NULL DEFAULT 0,
    dia_chi_giao   VARCHAR(255),
    ghi_chu        VARCHAR(255),
    ly_do_huy      VARCHAR(255),
    thoi_diem_huy  DATETIME,
    ma_nv_huy      INT,
    CONSTRAINT fk_hd_ban FOREIGN KEY (ma_ban) REFERENCES ban (ma_ban),
    CONSTRAINT fk_hd_kh FOREIGN KEY (ma_kh) REFERENCES khach_hang (ma_kh),
    CONSTRAINT fk_hd_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT fk_hd_km FOREIGN KEY (ma_km) REFERENCES khuyen_mai (ma_km),
    CONSTRAINT fk_hd_thue FOREIGN KEY (ma_thue) REFERENCES thue (ma_thue),
    CONSTRAINT fk_hd_nv_huy FOREIGN KEY (ma_nv_huy) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_hd_loai CHECK (loai_don IN ('TAI_BAN', 'MANG_DI', 'GIAO_HANG')),
    CONSTRAINT ck_hd_trang_thai CHECK (trang_thai IN ('OPEN', 'PAID', 'CANCELLED', 'REFUNDED'))
);
CREATE INDEX idx_hd_trang_thai ON hoa_don (trang_thai);
CREATE INDEX idx_hd_ngay_lap ON hoa_don (ngay_lap);

-- ---------- Cashier drawer / shifts ----------
-- One physical drawer is shared by this workstation; the singleton row serializes opening, payment and closeout.
CREATE TABLE ca_thu_ngan (
    ma_ca             INT AUTO_INCREMENT PRIMARY KEY,
    ma_nv             INT NOT NULL,
    mo_luc            DATETIME NOT NULL,
    tien_dau_ca       DECIMAL(15,2) NOT NULL,
    trang_thai        VARCHAR(10) NOT NULL,
    dong_luc          DATETIME,
    tien_dem          DECIMAL(15,2),
    tien_ky_vong      DECIMAL(15,2),
    chenh_lech         DECIMAL(15,2),
    ghi_chu_dong      VARCHAR(255),
    ma_nv_dong        INT,
    CONSTRAINT fk_ca_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT fk_ca_nv_dong FOREIGN KEY (ma_nv_dong) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_ca_trang_thai CHECK (trang_thai IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_ca_tien CHECK (tien_dau_ca >= 0 AND (tien_dem IS NULL OR tien_dem >= 0))
);
CREATE INDEX idx_ca_nv_mo ON ca_thu_ngan (ma_nv, mo_luc);

CREATE TABLE ngan_ke_toan (
    ma_ngan INT PRIMARY KEY,
    ma_ca_dang_mo INT,
    CONSTRAINT ck_ngan_ke_duy_nhat CHECK (ma_ngan = 1),
    CONSTRAINT fk_ngan_ca FOREIGN KEY (ma_ca_dang_mo) REFERENCES ca_thu_ngan (ma_ca)
);
INSERT INTO ngan_ke_toan (ma_ngan, ma_ca_dang_mo) VALUES (1, NULL);

CREATE TABLE bien_dong_tien_mat (
    ma_bd          INT AUTO_INCREMENT PRIMARY KEY,
    ma_ca          INT NOT NULL,
    ma_nv          INT NOT NULL,
    thoi_diem      DATETIME NOT NULL,
    loai           VARCHAR(10) NOT NULL,
    so_tien        DECIMAL(15,2) NOT NULL,
    ly_do          VARCHAR(255) NOT NULL,
    ma_yeu_cau     VARCHAR(64),
    CONSTRAINT uq_bd_yeu_cau UNIQUE (ma_yeu_cau),
    CONSTRAINT fk_bd_ca FOREIGN KEY (ma_ca) REFERENCES ca_thu_ngan (ma_ca),
    CONSTRAINT fk_btm_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_bd_tien_loai CHECK (loai IN ('THEM', 'CHI', 'RUT')),
    CONSTRAINT ck_bd_tien CHECK (so_tien > 0)
);
CREATE INDEX idx_bd_ca_time ON bien_dong_tien_mat (ma_ca, thoi_diem);

CREATE TABLE hoan_tien (
    ma_ht          INT AUTO_INCREMENT PRIMARY KEY,
    ma_hd          INT NOT NULL,
    ma_ca          INT,
    ma_nv          INT NOT NULL,
    phuong_thuc    VARCHAR(20) NOT NULL,
    so_tien        DECIMAL(15,2) NOT NULL,
    thoi_diem      DATETIME NOT NULL,
    ly_do          VARCHAR(255) NOT NULL,
    CONSTRAINT uq_ht_hd UNIQUE (ma_hd),
    CONSTRAINT fk_ht_hd FOREIGN KEY (ma_hd) REFERENCES hoa_don (ma_hd),
    CONSTRAINT fk_ht_ca FOREIGN KEY (ma_ca) REFERENCES ca_thu_ngan (ma_ca),
    CONSTRAINT fk_ht_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_ht_pt CHECK (phuong_thuc IN ('TIEN_MAT', 'CHUYEN_KHOAN', 'THE')),
    CONSTRAINT ck_ht_tien CHECK (so_tien >= 0)
);

CREATE TABLE dieu_chinh_ca (
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
CREATE INDEX idx_dc_ca_time ON dieu_chinh_ca (ma_ca, thoi_diem);

-- One invoice line = one product + size + its own toppings and note (surrogate key, see README).
CREATE TABLE chi_tiet_hoa_don (
    ma_cthd   INT AUTO_INCREMENT PRIMARY KEY,
    ma_hd     INT NOT NULL,
    ma_mon    INT NOT NULL,
    ma_size   INT NOT NULL,
    ten_mon   VARCHAR(100) NOT NULL,
    ten_size  VARCHAR(20)  NOT NULL,
    so_luong  INT NOT NULL,
    don_gia   DECIMAL(15,2) NOT NULL,
    ghi_chu   VARCHAR(255),
    CONSTRAINT fk_cthd_hd FOREIGN KEY (ma_hd) REFERENCES hoa_don (ma_hd),
    CONSTRAINT fk_cthd_mon FOREIGN KEY (ma_mon) REFERENCES mon (ma_mon),
    CONSTRAINT fk_cthd_size FOREIGN KEY (ma_size) REFERENCES size_mon (ma_size),
    CONSTRAINT ck_cthd_so_luong CHECK (so_luong > 0),
    CONSTRAINT ck_cthd_don_gia CHECK (don_gia >= 0)
);

-- so_luong = toppings per ONE drink of the line; line topping cost = gia_them * so_luong * line quantity
CREATE TABLE chi_tiet_topping (
    ma_cthd      INT NOT NULL,
    ma_topping   INT NOT NULL,
    ten_topping  VARCHAR(100) NOT NULL,
    so_luong     INT NOT NULL,
    gia_them     DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (ma_cthd, ma_topping),
    CONSTRAINT fk_ctt_cthd FOREIGN KEY (ma_cthd) REFERENCES chi_tiet_hoa_don (ma_cthd) ON DELETE CASCADE,
    CONSTRAINT fk_ctt_topping FOREIGN KEY (ma_topping) REFERENCES topping (ma_topping),
    CONSTRAINT ck_ctt_so_luong CHECK (so_luong > 0)
);

-- UNIQUE(ma_hd): an invoice can be paid only once, even if the button is clicked twice.
CREATE TABLE thanh_toan (
    ma_tt           INT AUTO_INCREMENT PRIMARY KEY,
    ma_hd           INT NOT NULL,
    phuong_thuc     VARCHAR(20) NOT NULL,
    so_tien         DECIMAL(15,2) NOT NULL,
    tien_khach_dua  DECIMAL(15,2) NOT NULL,
    tien_thoi       DECIMAL(15,2) NOT NULL DEFAULT 0,
    thoi_diem       DATETIME NOT NULL,
    ma_nv           INT NOT NULL,
    ma_ca           INT,
    CONSTRAINT uq_tt_hd UNIQUE (ma_hd),
    CONSTRAINT fk_tt_hd FOREIGN KEY (ma_hd) REFERENCES hoa_don (ma_hd),
    CONSTRAINT fk_tt_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT fk_tt_ca FOREIGN KEY (ma_ca) REFERENCES ca_thu_ngan (ma_ca),
    CONSTRAINT ck_tt_phuong_thuc CHECK (phuong_thuc IN ('TIEN_MAT', 'CHUYEN_KHOAN', 'THE')),
    CONSTRAINT ck_tt_so_tien CHECK (so_tien >= 0 AND tien_khach_dua >= so_tien)
);
CREATE INDEX idx_tt_thoi_diem ON thanh_toan (thoi_diem);
CREATE TABLE db_schema_version (version INT PRIMARY KEY);
INSERT INTO db_schema_version (version) VALUES (1);
INSERT INTO db_schema_version (version) VALUES (2);

-- ---------- Inventory ----------
CREATE TABLE nguyen_lieu (
    ma_nl         INT AUTO_INCREMENT PRIMARY KEY,
    ten_nl        VARCHAR(100) NOT NULL,
    don_vi_tinh   VARCHAR(20)  NOT NULL,
    so_luong_ton  DECIMAL(14,3) NOT NULL DEFAULT 0,
    muc_canh_bao  DECIMAL(14,3) NOT NULL DEFAULT 0,
    trang_thai    BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_nl_ten UNIQUE (ten_nl),
    CONSTRAINT ck_nl_canh_bao CHECK (muc_canh_bao >= 0)
);

-- dinh_luong = quantity for one size-M serving; other sizes multiply by size_mon.he_so_dinh_luong
CREATE TABLE cong_thuc (
    ma_ct       INT AUTO_INCREMENT PRIMARY KEY,
    ma_mon      INT NOT NULL,
    ma_nl       INT NOT NULL,
    dinh_luong  DECIMAL(14,3) NOT NULL,
    CONSTRAINT uq_ct_mon_nl UNIQUE (ma_mon, ma_nl),
    CONSTRAINT fk_ct_mon FOREIGN KEY (ma_mon) REFERENCES mon (ma_mon),
    CONSTRAINT fk_ct_nl FOREIGN KEY (ma_nl) REFERENCES nguyen_lieu (ma_nl),
    CONSTRAINT ck_ct_dinh_luong CHECK (dinh_luong > 0)
);

CREATE TABLE nha_cung_cap (
    ma_ncc         INT AUTO_INCREMENT PRIMARY KEY,
    ten_ncc        VARCHAR(150) NOT NULL,
    so_dien_thoai  VARCHAR(15),
    dia_chi        VARCHAR(255),
    email          VARCHAR(100),
    ghi_chu        VARCHAR(255),
    trang_thai     BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_ncc_ten UNIQUE (ten_ncc)
);

CREATE TABLE phieu_nhap (
    ma_pn          INT AUTO_INCREMENT PRIMARY KEY,
    ngay_nhap      DATETIME NOT NULL,
    ma_nv          INT NOT NULL,
    ma_ncc         INT NOT NULL,
    trang_thai     VARCHAR(20) NOT NULL,
    ghi_chu        VARCHAR(255),
    ly_do_huy      VARCHAR(255),
    thoi_diem_huy  DATETIME,
    ma_nv_huy      INT,
    CONSTRAINT fk_pn_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT fk_pn_ncc FOREIGN KEY (ma_ncc) REFERENCES nha_cung_cap (ma_ncc),
    CONSTRAINT fk_pn_nv_huy FOREIGN KEY (ma_nv_huy) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_pn_trang_thai CHECK (trang_thai IN ('DA_XAC_NHAN', 'DA_HUY'))
);

CREATE TABLE chi_tiet_phieu_nhap (
    ma_pn     INT NOT NULL,
    ma_nl     INT NOT NULL,
    so_luong  DECIMAL(14,3) NOT NULL,
    gia_nhap  DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (ma_pn, ma_nl),
    CONSTRAINT fk_ctpn_pn FOREIGN KEY (ma_pn) REFERENCES phieu_nhap (ma_pn) ON DELETE CASCADE,
    CONSTRAINT fk_ctpn_nl FOREIGN KEY (ma_nl) REFERENCES nguyen_lieu (ma_nl),
    CONSTRAINT ck_ctpn CHECK (so_luong > 0 AND gia_nhap >= 0)
);

CREATE TABLE phieu_xuat (
    ma_px          INT AUTO_INCREMENT PRIMARY KEY,
    ngay_xuat      DATETIME NOT NULL,
    ma_nv          INT NOT NULL,
    ly_do          VARCHAR(255) NOT NULL,
    trang_thai     VARCHAR(20) NOT NULL,
    ly_do_huy      VARCHAR(255),
    thoi_diem_huy  DATETIME,
    ma_nv_huy      INT,
    CONSTRAINT fk_px_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT fk_px_nv_huy FOREIGN KEY (ma_nv_huy) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_px_trang_thai CHECK (trang_thai IN ('DA_XAC_NHAN', 'DA_HUY'))
);

CREATE TABLE chi_tiet_phieu_xuat (
    ma_px          INT NOT NULL,
    ma_nl          INT NOT NULL,
    so_luong_xuat  DECIMAL(14,3) NOT NULL,
    don_gia_xuat   DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (ma_px, ma_nl),
    CONSTRAINT fk_ctpx_px FOREIGN KEY (ma_px) REFERENCES phieu_xuat (ma_px) ON DELETE CASCADE,
    CONSTRAINT fk_ctpx_nl FOREIGN KEY (ma_nl) REFERENCES nguyen_lieu (ma_nl),
    CONSTRAINT ck_ctpx CHECK (so_luong_xuat > 0 AND don_gia_xuat >= 0)
);

-- Every stock change is recorded here; nguyen_lieu.so_luong_ton always equals the sum of so_luong.
CREATE TABLE bien_dong_kho (
    ma_bd       INT AUTO_INCREMENT PRIMARY KEY,
    ma_nl       INT NOT NULL,
    thoi_diem   DATETIME NOT NULL,
    loai        VARCHAR(20) NOT NULL,
    so_luong    DECIMAL(14,3) NOT NULL,
    ton_sau     DECIMAL(14,3) NOT NULL,
    tham_chieu  VARCHAR(30),
    ma_nv       INT,
    ghi_chu     VARCHAR(255),
    CONSTRAINT fk_bd_nl FOREIGN KEY (ma_nl) REFERENCES nguyen_lieu (ma_nl),
    CONSTRAINT fk_bd_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv),
    CONSTRAINT ck_bd_loai CHECK (loai IN ('TON_DAU', 'NHAP', 'XUAT', 'BAN_HANG', 'HUY_NHAP', 'HUY_XUAT', 'DIEU_CHINH'))
);
CREATE INDEX idx_bd_nl_thoi_diem ON bien_dong_kho (ma_nl, thoi_diem);

-- ---------- Audit log ----------
CREATE TABLE nhat_ky (
    ma_nk         INT AUTO_INCREMENT PRIMARY KEY,
    thoi_diem     DATETIME NOT NULL,
    ma_nv         INT,
    hanh_dong     VARCHAR(50) NOT NULL,
    doi_tuong     VARCHAR(50) NOT NULL,
    ma_doi_tuong  VARCHAR(30),
    chi_tiet      VARCHAR(500),
    CONSTRAINT fk_nk_nv FOREIGN KEY (ma_nv) REFERENCES nhan_vien (ma_nv)
);
CREATE INDEX idx_nk_thoi_diem ON nhat_ky (thoi_diem);
