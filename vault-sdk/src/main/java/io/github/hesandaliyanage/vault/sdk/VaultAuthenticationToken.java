package io.github.hesandaliyanage.vault.sdk;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Spring Security {@link org.springframework.security.core.Authentication}
 * populated from a {@link VaultPrincipal}. The principal's role is exposed
 * as a {@code ROLE_<role>} authority and each scope as a plain authority,
 * so {@code @PreAuthorize("hasRole('ADMIN')")} and
 * {@code @PreAuthorize("hasAuthority('orders:write')")} both work.
 */
public class VaultAuthenticationToken extends AbstractAuthenticationToken {

    private final VaultPrincipal principal;

    public VaultAuthenticationToken(VaultPrincipal principal) {
        super(buildAuthorities(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public VaultPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.userId();
    }

    private static Collection<? extends GrantedAuthority> buildAuthorities(VaultPrincipal principal) {
        Stream<SimpleGrantedAuthority> role = principal.role() == null
                ? Stream.empty()
                : Stream.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));
        Stream<SimpleGrantedAuthority> scopes = principal.scopes().stream()
                .map(SimpleGrantedAuthority::new);
        return Stream.concat(role, scopes).toList();
    }
}
