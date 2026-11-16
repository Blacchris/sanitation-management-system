package com.sanitation.management.auth.dto;

import com.sanitation.management.user.entity.User;
import com.sanitation.management.user.enums.AccountStatus;
import com.sanitation.management.user.enums.Role;

public record UserSummary(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role,
        AccountStatus accountStatus) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getRole(), user.getAccountStatus());
    }
}
