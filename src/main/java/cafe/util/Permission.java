package cafe.util;

import cafe.model.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * What each role may do. Services check these on every sensitive call (Session.require),
 * so hiding a button is never the only protection.
 */
public enum Permission {
    // daily operations - both roles
    SELL(Role.QUAN_LY, Role.NHAN_VIEN),
    VIEW_OWN_INVOICES(Role.QUAN_LY, Role.NHAN_VIEN),
    MANAGE_CUSTOMERS(Role.QUAN_LY, Role.NHAN_VIEN),
    MANAGE_RESERVATIONS(Role.QUAN_LY, Role.NHAN_VIEN),
    // management only
    VIEW_ALL_INVOICES(Role.QUAN_LY),
    REFUND_INVOICE(Role.QUAN_LY),
    DELETE_CUSTOMER(Role.QUAN_LY),
    VIEW_CUSTOMER_ID(Role.QUAN_LY),
    MANAGE_EMPLOYEES(Role.QUAN_LY),
    MANAGE_CATALOG(Role.QUAN_LY),
    MANAGE_TABLES(Role.QUAN_LY),
    MANAGE_PROMOTIONS(Role.QUAN_LY),
    MANAGE_TAXES(Role.QUAN_LY),
    MANAGE_INVENTORY(Role.QUAN_LY),
    ADJUST_STOCK(Role.QUAN_LY),
    VIEW_REPORTS(Role.QUAN_LY),
    VIEW_AUDIT_LOG(Role.QUAN_LY),
    CASHIER_SHIFT(Role.QUAN_LY, Role.NHAN_VIEN),
    MANAGE_OWNER_ACCOUNTS(Role.CHU_CUA_HANG);

    private final Set<Role> roles;

    Permission(Role first, Role... rest) {
        this.roles = EnumSet.of(first, rest);
    }

    public boolean allows(Role role) {
        return role != null && (roles.contains(role)
                || role == Role.CHU_CUA_HANG && roles.contains(Role.QUAN_LY));
    }
}
