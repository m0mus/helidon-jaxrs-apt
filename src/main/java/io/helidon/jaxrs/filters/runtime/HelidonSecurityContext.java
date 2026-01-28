package io.helidon.jaxrs.filters.runtime;

import io.helidon.webserver.http.ServerRequest;
import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Wrapper that adapts Helidon's ServerRequest to JAX-RS SecurityContext.
 *
 * <p>This implementation extracts security information from HTTP headers,
 * providing:
 * <ul>
 *   <li>User principal from Basic/Bearer authentication headers</li>
 *   <li>Role checking via custom X-User-Roles header</li>
 *   <li>Secure transport detection (HTTPS)</li>
 *   <li>Authentication scheme information</li>
 * </ul>
 *
 * <p>For production use, integrate with Helidon Security module for
 * full authentication/authorization support.
 */
public class HelidonSecurityContext implements SecurityContext {

    private final ServerRequest request;
    private final Principal userPrincipal;
    private final String authenticationScheme;
    private final Set<String> roles;

    public HelidonSecurityContext(ServerRequest request) {
        this.request = request;

        Principal principal = null;
        String scheme = null;
        Set<String> userRoles = new HashSet<>();

        // Check for authentication header
        var authHeader = request.headers().first(io.helidon.http.HeaderNames.AUTHORIZATION);
        if (authHeader.isPresent()) {
            String auth = authHeader.get();
            if (auth.toLowerCase().startsWith("basic ")) {
                scheme = BASIC_AUTH;
                // Extract username from Basic auth (username:password base64 encoded)
                try {
                    String credentials = new String(java.util.Base64.getDecoder().decode(auth.substring(6)));
                    int colonIndex = credentials.indexOf(':');
                    if (colonIndex > 0) {
                        String username = credentials.substring(0, colonIndex);
                        principal = () -> username;
                    }
                } catch (Exception e) {
                    // Invalid base64 or format, leave principal as null
                }
            } else if (auth.toLowerCase().startsWith("bearer ")) {
                scheme = "BEARER";
                // For bearer tokens, we'd need JWT decoding which is application-specific
                // Check for X-User-Name header as fallback for testing
                var userName = request.headers().first(io.helidon.http.HeaderNames.create("X-User-Name"));
                if (userName.isPresent()) {
                    String name = userName.get();
                    principal = () -> name;
                }
            } else if (auth.toLowerCase().startsWith("digest ")) {
                scheme = DIGEST_AUTH;
            }
        }

        // Check for custom roles header (useful for testing)
        var rolesHeader = request.headers().first(io.helidon.http.HeaderNames.create("X-User-Roles"));
        if (rolesHeader.isPresent()) {
            String rolesStr = rolesHeader.get();
            for (String role : rolesStr.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    userRoles.add(trimmed);
                }
            }
        }

        this.userPrincipal = principal;
        this.authenticationScheme = scheme;
        this.roles = Collections.unmodifiableSet(userRoles);
    }

    @Override
    public Principal getUserPrincipal() {
        return userPrincipal;
    }

    @Override
    public boolean isUserInRole(String role) {
        return roles.contains(role);
    }

    @Override
    public boolean isSecure() {
        // Check if the request was made over HTTPS
        return "https".equalsIgnoreCase(request.requestedUri().scheme());
    }

    @Override
    public String getAuthenticationScheme() {
        return authenticationScheme;
    }
}

