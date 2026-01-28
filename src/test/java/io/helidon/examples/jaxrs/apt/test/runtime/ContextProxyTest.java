package io.helidon.examples.jaxrs.apt.test.runtime;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.examples.jaxrs.apt.runtime.HttpHeadersProxy;
import io.helidon.examples.jaxrs.apt.runtime.ResourceInfoProxy;
import io.helidon.examples.jaxrs.apt.runtime.SecurityContextProxy;
import io.helidon.examples.jaxrs.apt.runtime.SimpleRuntimeDelegate;
import io.helidon.examples.jaxrs.apt.runtime.UriInfoProxy;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Method;
import java.net.URI;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContextProxyTest {

    @BeforeEach
    void initRuntimeDelegate() {
        SimpleRuntimeDelegate.init();
    }

    @Test
    void testUriInfoProxyDelegates() {
        Context ctx = Context.create();
        TestUriInfo uriInfo = new TestUriInfo();
        ctx.register(UriInfo.class, uriInfo);

        Contexts.runInContext(ctx, () -> {
            assertThat(UriInfoProxy.INSTANCE.getPath(), is("path"));
            assertThat(UriInfoProxy.INSTANCE.getPath(false), is("raw"));
            assertThat(UriInfoProxy.INSTANCE.getPathSegments().size(), is(1));
            assertThat(UriInfoProxy.INSTANCE.getPathSegments(false).size(), is(1));
            assertThat(UriInfoProxy.INSTANCE.getRequestUri(), is(URI.create("http://localhost/request")));
            assertThat(UriInfoProxy.INSTANCE.getAbsolutePath(), is(URI.create("http://localhost/absolute")));
            assertThat(UriInfoProxy.INSTANCE.getBaseUri(), is(URI.create("http://localhost/base/")));
            assertThat(UriInfoProxy.INSTANCE.getRequestUriBuilder().build(), is(URI.create("http://localhost/request")));
            assertThat(UriInfoProxy.INSTANCE.getAbsolutePathBuilder().build(), is(URI.create("http://localhost/absolute")));
            assertThat(UriInfoProxy.INSTANCE.getBaseUriBuilder().build(), is(URI.create("http://localhost/base/")));
            assertThat(UriInfoProxy.INSTANCE.getPathParameters().getFirst("id"), is("1"));
            assertThat(UriInfoProxy.INSTANCE.getPathParameters(false).getFirst("id"), is("1"));
            assertThat(UriInfoProxy.INSTANCE.getQueryParameters().getFirst("q"), is("v"));
            assertThat(UriInfoProxy.INSTANCE.getQueryParameters(false).getFirst("q"), is("v"));
            assertThat(UriInfoProxy.INSTANCE.getMatchedURIs().size(), is(1));
            assertThat(UriInfoProxy.INSTANCE.getMatchedURIs(false).size(), is(1));
            assertThat(UriInfoProxy.INSTANCE.getMatchedResources().size(), is(1));
            assertThat(UriInfoProxy.INSTANCE.resolve(URI.create("child")), is(URI.create("http://localhost/base/child")));
            assertThat(UriInfoProxy.INSTANCE.relativize(URI.create("http://localhost/base/child")), is(URI.create("child")));
        });
    }

    @Test
    void testHttpHeadersProxyDelegates() {
        Context ctx = Context.create();
        TestHttpHeaders headers = new TestHttpHeaders();
        ctx.register(HttpHeaders.class, headers);

        Contexts.runInContext(ctx, () -> {
            assertThat(HttpHeadersProxy.INSTANCE.getRequestHeader("X-Test").getFirst(), is("one"));
            assertThat(HttpHeadersProxy.INSTANCE.getHeaderString("X-Test"), is("one"));
            assertThat(HttpHeadersProxy.INSTANCE.getRequestHeaders().getFirst("X-Test"), is("one"));
            assertThat(HttpHeadersProxy.INSTANCE.getAcceptableMediaTypes().getFirst(), is(MediaType.TEXT_PLAIN_TYPE));
            assertThat(HttpHeadersProxy.INSTANCE.getAcceptableLanguages().getFirst(), is(Locale.US));
            assertThat(HttpHeadersProxy.INSTANCE.getMediaType(), is(MediaType.APPLICATION_JSON_TYPE));
            assertThat(HttpHeadersProxy.INSTANCE.getLanguage(), is(Locale.CANADA));
            assertThat(HttpHeadersProxy.INSTANCE.getCookies().get("id").getValue(), is("cookie"));
            assertThat(HttpHeadersProxy.INSTANCE.getDate(), is(new Date(0)));
            assertThat(HttpHeadersProxy.INSTANCE.getLength(), is(42));
        });
    }

    @Test
    void testSecurityContextProxyDelegates() {
        Context ctx = Context.create();
        TestSecurityContext securityContext = new TestSecurityContext();
        ctx.register(SecurityContext.class, securityContext);

        Contexts.runInContext(ctx, () -> {
            assertThat(SecurityContextProxy.INSTANCE.getUserPrincipal().getName(), is("user"));
            assertThat(SecurityContextProxy.INSTANCE.isUserInRole("admin"), is(true));
            assertThat(SecurityContextProxy.INSTANCE.isSecure(), is(true));
            assertThat(SecurityContextProxy.INSTANCE.getAuthenticationScheme(), is("BASIC"));
        });
    }

    @Test
    void testResourceInfoProxyDelegates() {
        Context ctx = Context.create();
        TestResourceInfo resourceInfo = new TestResourceInfo();
        ctx.register(ResourceInfo.class, resourceInfo);

        Contexts.runInContext(ctx, () -> {
            assertThat(ResourceInfoProxy.INSTANCE.getResourceClass().getName(), is(ContextProxyTest.class.getName()));
            assertThat(ResourceInfoProxy.INSTANCE.getResourceMethod().getName(), is("testResourceInfoProxyDelegates"));
        });
    }

    @Test
    void testProxyWithoutContextThrows() {
        assertThrows(IllegalStateException.class, () -> UriInfoProxy.INSTANCE.getPath());
        assertThrows(IllegalStateException.class, () -> HttpHeadersProxy.INSTANCE.getHeaderString("X"));
        assertThrows(IllegalStateException.class, () -> SecurityContextProxy.INSTANCE.isSecure());
        assertThrows(IllegalStateException.class, () -> ResourceInfoProxy.INSTANCE.getResourceClass());
    }

    private static final class TestUriInfo implements UriInfo {
        @Override
        public String getPath() {
            return "path";
        }

        @Override
        public String getPath(boolean decode) {
            return decode ? "path" : "raw";
        }

        @Override
        public List<PathSegment> getPathSegments() {
            return List.of(new TestPathSegment());
        }

        @Override
        public List<PathSegment> getPathSegments(boolean decode) {
            return List.of(new TestPathSegment());
        }

        @Override
        public URI getRequestUri() {
            return URI.create("http://localhost/request");
        }

        @Override
        public UriBuilder getRequestUriBuilder() {
            return UriBuilder.fromUri(getRequestUri());
        }

        @Override
        public URI getAbsolutePath() {
            return URI.create("http://localhost/absolute");
        }

        @Override
        public UriBuilder getAbsolutePathBuilder() {
            return UriBuilder.fromUri(getAbsolutePath());
        }

        @Override
        public URI getBaseUri() {
            return URI.create("http://localhost/base/");
        }

        @Override
        public UriBuilder getBaseUriBuilder() {
            return UriBuilder.fromUri(getBaseUri());
        }

        @Override
        public MultivaluedMap<String, String> getPathParameters() {
            MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
            params.add("id", "1");
            return params;
        }

        @Override
        public MultivaluedMap<String, String> getPathParameters(boolean decode) {
            return getPathParameters();
        }

        @Override
        public MultivaluedMap<String, String> getQueryParameters() {
            MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
            params.add("q", "v");
            return params;
        }

        @Override
        public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
            return getQueryParameters();
        }

        @Override
        public List<String> getMatchedURIs() {
            return List.of("matched");
        }

        @Override
        public List<String> getMatchedURIs(boolean decode) {
            return getMatchedURIs();
        }

        @Override
        public List<Object> getMatchedResources() {
            return List.of(this);
        }

        @Override
        public URI resolve(URI uri) {
            return getBaseUri().resolve(uri);
        }

        @Override
        public URI relativize(URI uri) {
            return getBaseUri().relativize(uri);
        }
    }

    private static final class TestPathSegment implements PathSegment {
        @Override
        public String getPath() {
            return "segment";
        }

        @Override
        public MultivaluedMap<String, String> getMatrixParameters() {
            return new MultivaluedHashMap<>();
        }
    }

    private static final class TestHttpHeaders implements HttpHeaders {
        @Override
        public List<String> getRequestHeader(String name) {
            return List.of("one");
        }

        @Override
        public String getHeaderString(String name) {
            return "one";
        }

        @Override
        public MultivaluedMap<String, String> getRequestHeaders() {
            MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
            headers.add("X-Test", "one");
            return headers;
        }

        @Override
        public List<MediaType> getAcceptableMediaTypes() {
            return List.of(MediaType.TEXT_PLAIN_TYPE);
        }

        @Override
        public List<Locale> getAcceptableLanguages() {
            return List.of(Locale.US);
        }

        @Override
        public MediaType getMediaType() {
            return MediaType.APPLICATION_JSON_TYPE;
        }

        @Override
        public Locale getLanguage() {
            return Locale.CANADA;
        }

        @Override
        public Map<String, Cookie> getCookies() {
            return Map.of("id", new Cookie("id", "cookie"));
        }

        @Override
        public Date getDate() {
            return new Date(0);
        }

        @Override
        public int getLength() {
            return 42;
        }
    }

    private static final class TestSecurityContext implements SecurityContext {
        @Override
        public Principal getUserPrincipal() {
            return () -> "user";
        }

        @Override
        public boolean isUserInRole(String role) {
            return "admin".equals(role);
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public String getAuthenticationScheme() {
            return "BASIC";
        }
    }

    private static final class TestResourceInfo implements ResourceInfo {
        @Override
        public Method getResourceMethod() {
            try {
                return ContextProxyTest.class.getDeclaredMethod("testResourceInfoProxyDelegates");
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Class<?> getResourceClass() {
            return ContextProxyTest.class;
        }
    }
}
