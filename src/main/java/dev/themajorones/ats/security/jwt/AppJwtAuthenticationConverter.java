package dev.themajorones.ats.security.jwt;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AppJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppPrincipal principal = new AppPrincipal(
            claimAsInteger(jwt, "userId"),
            claimAsLong(jwt, "githubId"),
            claimAsString(jwt, "login"),
            claimAsString(jwt, "displayName"),
            claimAsStringList(jwt, "orgs")
        );

        Collection<GrantedAuthority> authorities = List.<GrantedAuthority>of();
        return new UsernamePasswordAuthenticationToken(principal, jwt.getTokenValue(), authorities);
    }

    private Integer claimAsInteger(Jwt jwt, String name) {
        Number number = jwt.getClaim(name);
        if (number == null) {
            return null;
        }
        return number.intValue();
    }

    private Long claimAsLong(Jwt jwt, String name) {
        Number number = jwt.getClaim(name);
        if (number == null) {
            return null;
        }
        return number.longValue();
    }

    private String claimAsString(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<String> claimAsStringList(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toUnmodifiableList());
        }
        return List.of(String.valueOf(value));
    }
}
