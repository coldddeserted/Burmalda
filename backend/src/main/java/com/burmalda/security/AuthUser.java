package com.burmalda.security;

import com.burmalda.domain.Role;

/** То, что лежит в SecurityContext после проверки JWT. В контроллере: @AuthenticationPrincipal AuthUser me */
public record AuthUser(Long id, String email, Role role, String tokenId) {
}
