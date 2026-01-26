package io.helidon.examples.jaxrs.apt.test.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;

/**
 * Test resource for verifying @Context SecurityContext functionality.
 */
@Path("/security")
@Produces(MediaType.TEXT_PLAIN)
public class TestSecurityContextResource {

    /**
     * Get user principal name from SecurityContext.
     */
    @GET
    @Path("/principal")
    public String getPrincipal(@Context SecurityContext securityContext) {
        Principal principal = securityContext.getUserPrincipal();
        return "principal:" + (principal != null ? principal.getName() : "null");
    }

    /**
     * Check if user has a specific role.
     */
    @GET
    @Path("/role/{role}")
    public String checkRole(@PathParam("role") String role,
                            @Context SecurityContext securityContext) {
        boolean hasRole = securityContext.isUserInRole(role);
        return "role:" + role + "=" + hasRole;
    }

    /**
     * Check if connection is secure (HTTPS).
     */
    @GET
    @Path("/secure")
    public String isSecure(@Context SecurityContext securityContext) {
        return "secure:" + securityContext.isSecure();
    }

    /**
     * Get authentication scheme.
     */
    @GET
    @Path("/scheme")
    public String getAuthScheme(@Context SecurityContext securityContext) {
        String scheme = securityContext.getAuthenticationScheme();
        return "scheme:" + (scheme != null ? scheme : "null");
    }

    /**
     * Combined security info endpoint.
     */
    @GET
    @Path("/info")
    public String getSecurityInfo(@Context SecurityContext securityContext) {
        Principal principal = securityContext.getUserPrincipal();
        String principalName = principal != null ? principal.getName() : "anonymous";
        String scheme = securityContext.getAuthenticationScheme();
        boolean secure = securityContext.isSecure();

        return "user:" + principalName + ",scheme:" + (scheme != null ? scheme : "none") + ",secure:" + secure;
    }

    /**
     * Protected endpoint that requires admin role.
     */
    @GET
    @Path("/admin")
    public String adminOnly(@Context SecurityContext securityContext) {
        if (!securityContext.isUserInRole("admin")) {
            throw new jakarta.ws.rs.ForbiddenException("Admin role required");
        }
        Principal principal = securityContext.getUserPrincipal();
        return "admin-access-granted:" + (principal != null ? principal.getName() : "unknown");
    }

    /**
     * Endpoint that checks multiple roles.
     */
    @GET
    @Path("/roles-check")
    public String checkMultipleRoles(@Context SecurityContext securityContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("admin:").append(securityContext.isUserInRole("admin"));
        sb.append(",user:").append(securityContext.isUserInRole("user"));
        sb.append(",guest:").append(securityContext.isUserInRole("guest"));
        return sb.toString();
    }
}
