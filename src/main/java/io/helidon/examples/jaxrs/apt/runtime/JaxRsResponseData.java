package io.helidon.examples.jaxrs.apt.runtime;

/**
 * Response data produced by a generated handler before post-matching filters run.
 *
 * @param responseContext container response context used by response filters
 * @param contentType content type to send (null to omit)
 * @param output serialized response body (null for no entity)
 * @param applyResponseFilters whether response filters should run
 * @param suppressResponseFilterExceptions whether IOExceptions from response filters should be suppressed
 */
public record JaxRsResponseData(HelidonContainerResponseContext responseContext,
                                String contentType,
                                String output,
                                boolean applyResponseFilters,
                                boolean suppressResponseFilterExceptions) {
}
