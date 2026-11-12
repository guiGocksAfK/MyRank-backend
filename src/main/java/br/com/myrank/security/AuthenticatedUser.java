package br.com.myrank.security;

import br.com.myrank.domain.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * {@link UserDetails} que já carrega a entidade {@link User} resolvida no filtro de
 * autenticação, evitando novas idas ao banco em {@link AuthUtils#getUser(UserDetails)}.
 */
public class AuthenticatedUser implements UserDetails {

    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedUser(User user) {
        this(user, Collections.emptyList());
    }

    public AuthenticatedUser(User user, Collection<? extends GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = authorities == null ? Collections.emptyList() : authorities;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash() != null ? user.getPasswordHash() : "";
    }

    @Override
    public String getUsername() {
        return user.getEmail() != null ? user.getEmail() : user.getUsername();
    }
}
