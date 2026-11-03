package com.burmalda.security;

import com.burmalda.domain.Role;

/** What is stored in the SecurityContext after the JWT check. In a controller: @AuthenticationPrincipal AuthUser me */
public record AuthUser(Long id, String email, Role role, String tokenId) {
}
