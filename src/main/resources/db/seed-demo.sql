-- Demo catalog data for presentations. Contains NO user accounts.
-- Loaded once, right after the schema is created on an empty database (db.seedDemoData=true).

INSERT INTO loai_mon (ten_loai) VALUES ('Cà phê'), ('Trà'), ('Đá xay'), ('Nước ép'), ('Bánh');

INSERT INTO mon (ten_mon, ma_loai, trang_thai) VALUES
 ('Cà phê đen', 1, TRUE),
 ('Cà phê sữa', 1, TRUE),
 ('Bạc xỉu', 1, TRUE),
 ('Cappuccino', 1, TRUE),
 ('Latte', 1, TRUE),
 ('Trà đào cam sả', 2, TRUE),
 ('Trà sữa trân châu', 2, TRUE),
 ('Trà vải', 2, TRUE),
 ('Cookie đá xay', 3, TRUE),
 ('Matcha đá xay', 3, TRUE),
 ('Nước ép cam', 4, TRUE),
 ('Bánh croissant', 5, TRUE),
 ('Bánh tiramisu', 5, TRUE),
 ('Cà phê muối', 1, FALSE);

INSERT INTO size_mon (ten_size, he_so_dinh_luong, thu_tu) VALUES ('S', 0.80, 1), ('M', 1.00, 2), ('L', 1.30, 3);

INSERT INTO topping (ten_topping, gia_them, trang_thai) VALUES
 ('Trân châu đen', 5000, TRUE),
 ('Thạch đào', 6000, TRUE),
 ('Kem cheese', 10000, TRUE),
 ('Shot espresso', 8000, TRUE),
 ('Pudding trứng', 7000, TRUE);

INSERT INTO bang_gia (mo_ta, ngay_bat_dau, ngay_ket_thuc) VALUES ('Bảng giá năm 2026', '2026-01-01', NULL);

INSERT INTO chi_tiet_bang_gia (ma_bang_gia, ma_mon, ma_size, gia_ban) VALUES
 (1, 1, 1, 20000), (1, 1, 2, 25000), (1, 1, 3, 30000),
 (1, 2, 1, 25000), (1, 2, 2, 29000), (1, 2, 3, 35000),
 (1, 3, 1, 27000), (1, 3, 2, 32000), (1, 3, 3, 37000),
 (1, 4, 2, 45000), (1, 4, 3, 52000),
 (1, 5, 2, 45000), (1, 5, 3, 52000),
 (1, 6, 2, 39000), (1, 6, 3, 45000),
 (1, 7, 2, 35000), (1, 7, 3, 42000),
 (1, 8, 2, 39000), (1, 8, 3, 45000),
 (1, 9, 2, 49000), (1, 9, 3, 55000),
 (1, 10, 2, 49000), (1, 10, 3, 55000),
 (1, 11, 2, 40000),
 (1, 12, 2, 30000),
 (1, 13, 2, 38000),
 (1, 14, 2, 35000);

INSERT INTO khu_vuc (ten_khu_vuc) VALUES ('Tầng trệt'), ('Tầng 1'), ('Sân vườn');

INSERT INTO ban (ten_ban, ma_khu_vuc, so_cho, trang_thai) VALUES
 ('Bàn 01', 1, 2, TRUE), ('Bàn 02', 1, 2, TRUE), ('Bàn 03', 1, 4, TRUE), ('Bàn 04', 1, 4, TRUE), ('Bàn 05', 1, 6, TRUE),
 ('Bàn 11', 2, 4, TRUE), ('Bàn 12', 2, 4, TRUE), ('Bàn 13', 2, 6, TRUE), ('Bàn 14', 2, 8, TRUE),
 ('Sân 01', 3, 4, TRUE), ('Sân 02', 3, 4, TRUE), ('Sân 03', 3, 6, FALSE);

INSERT INTO khach_hang (ten_kh, ngay_sinh, so_cmnd, so_dien_thoai, dia_chi) VALUES
 ('Nguyễn Văn An', '1995-03-12', NULL, '0901234567', 'Quận 1, TP.HCM'),
 ('Trần Thị Bình', '1998-07-25', NULL, '0912345678', 'Quận 3, TP.HCM'),
 ('Lê Hoàng Cường', NULL, NULL, '0987654321', NULL),
 ('Phạm Minh Duyên', '2001-11-02', NULL, '0938111222', 'Thủ Đức, TP.HCM');

INSERT INTO loai_khuyen_mai (ten_loai_km, phan_tram_giam, phan_tien_giam) VALUES
 ('Giảm 10%', 10, NULL),
 ('Giảm 20.000đ', NULL, 20000),
 ('Giảm 15%', 15, NULL);

INSERT INTO khuyen_mai (ten_km, ma_loai_km, ngay_bat_dau, ngay_ket_thuc, gia_tri_don_toi_thieu, giam_toi_da, trang_thai) VALUES
 ('Khách thân thiết - giảm 10%', 1, '2026-01-01', '2026-12-31', 0, 50000, TRUE),
 ('Đơn từ 150.000đ giảm 20.000đ', 2, '2026-01-01', '2026-12-31', 150000, NULL, TRUE),
 ('Khai trương (đã hết hạn)', 3, '2025-12-01', '2025-12-31', 0, NULL, TRUE);

INSERT INTO thue (ten_thue, phan_tram, ngay_bat_dau, ngay_ket_thuc) VALUES ('VAT 8%', 8, '2026-01-01', NULL);

INSERT INTO nguyen_lieu (ten_nl, don_vi_tinh, so_luong_ton, muc_canh_bao, trang_thai) VALUES
 ('Cà phê hạt rang', 'gram', 5000, 1000, TRUE),
 ('Sữa đặc', 'ml', 3000, 800, TRUE),
 ('Sữa tươi', 'ml', 8000, 2000, TRUE),
 ('Đường', 'gram', 6000, 1000, TRUE),
 ('Trà đen', 'gram', 1500, 300, TRUE),
 ('Đào ngâm', 'gram', 400, 500, TRUE),
 ('Vải ngâm', 'gram', 2000, 500, TRUE),
 ('Bột matcha', 'gram', 800, 200, TRUE),
 ('Cam tươi', 'quả', 60, 20, TRUE),
 ('Ly nhựa', 'cái', 500, 100, TRUE);

-- Opening stock movements: keeps "stock = sum of movements" true from day one
INSERT INTO bien_dong_kho (ma_nl, thoi_diem, loai, so_luong, ton_sau, tham_chieu, ma_nv, ghi_chu)
SELECT ma_nl, '2026-01-01 00:00:00', 'TON_DAU', so_luong_ton, so_luong_ton, NULL, NULL, 'Tồn đầu kỳ (dữ liệu mẫu)' FROM nguyen_lieu;

INSERT INTO cong_thuc (ma_mon, ma_nl, dinh_luong) VALUES
 (1, 1, 20), (1, 4, 10),
 (2, 1, 20), (2, 2, 30),
 (3, 1, 10), (3, 2, 20), (3, 3, 80),
 (4, 1, 18), (4, 3, 150),
 (5, 1, 18), (5, 3, 200),
 (6, 5, 5), (6, 6, 50), (6, 4, 20),
 (7, 5, 5), (7, 3, 100), (7, 4, 25),
 (8, 5, 5), (8, 7, 50), (8, 4, 20),
 (10, 8, 10), (10, 3, 150), (10, 4, 20),
 (11, 9, 3);

INSERT INTO nha_cung_cap (ten_ncc, so_dien_thoai, dia_chi, email, ghi_chu, trang_thai) VALUES
 ('Công ty Cà phê Cao Nguyên', '0263 3822 111', 'Bảo Lộc, Lâm Đồng', 'sales@caonguyen.example', 'Cà phê hạt', TRUE),
 ('Đại lý Sữa Miền Nam', '028 3999 2222', 'Quận 7, TP.HCM', 'order@suamiennam.example', 'Sữa tươi, sữa đặc', TRUE),
 ('Chợ đầu mối Thủ Đức', '028 3896 3333', 'Thủ Đức, TP.HCM', NULL, 'Trái cây', TRUE);
