package io.helidon.examples.jaxrs.apt.processor;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Annotation processor that generates Helidon routing code from JAX-RS annotations.
 *
 * <p>Processes {@code @Path} annotated classes at compile time and generates
 * optimized handler code with proper error handling.
 */
@SupportedAnnotationTypes({
        "jakarta.ws.rs.Path",
        "jakarta.ws.rs.ext.Provider"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class JaxRsProcessor extends AbstractProcessor {

    // Type constants
    private static final TypeName SERVER_REQUEST = TypeName.create("io.helidon.webserver.http.ServerRequest");
    private static final TypeName SERVER_RESPONSE = TypeName.create("io.helidon.webserver.http.ServerResponse");
    private static final TypeName HTTP_ROUTING_BUILDER = TypeName.create("io.helidon.webserver.http.HttpRouting.Builder");
    private static final TypeName OBJECT_MAPPER = TypeName.create("com.fasterxml.jackson.databind.ObjectMapper");
    private static final TypeName NOT_FOUND_EXCEPTION = TypeName.create("jakarta.ws.rs.NotFoundException");
    private static final TypeName WEB_APPLICATION_EXCEPTION = TypeName.create("jakarta.ws.rs.WebApplicationException");
    private static final TypeName GENERATED = TypeName.create("io.helidon.examples.jaxrs.apt.runtime.Generated");
    private static final TypeName URI_INFO = TypeName.create("io.helidon.examples.jaxrs.apt.runtime.HelidonUriInfo");
    private static final TypeName HTTP_HEADERS = TypeName.create("io.helidon.examples.jaxrs.apt.runtime.HelidonHttpHeaders");
    private static final TypeName FILTER_CONTEXT = TypeName.create("io.helidon.examples.jaxrs.apt.runtime.FilterContext");
    private static final TypeName REQUEST_CONTEXT = TypeName.create("io.helidon.examples.jaxrs.apt.runtime.HelidonContainerRequestContext");
    private static final TypeName RESPONSE_CONTEXT = TypeName.create("io.helidon.examples.jaxrs.apt.runtime.HelidonContainerResponseContext");
    private static final TypeName READER_CONTEXT = TypeName.create("io.helidon.examples.jaxrs.apt.runtime.HelidonReaderInterceptorContext");
    private static final TypeName WRITER_CONTEXT = TypeName.create("io.helidon.examples.jaxrs.apt.runtime.HelidonWriterInterceptorContext");

    private static final int DEFAULT_PRIORITY = 5000;

    private Messager messager;
    private Filer filer;

    // Collected filters - reset for each processing round
    private List<FilterInfo> preMatchingRequestFilters;
    private List<FilterInfo> requestFilters;
    private List<FilterInfo> responseFilters;
    private List<FilterInfo> readerInterceptors;
    private List<FilterInfo> writerInterceptors;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        // Reset filter lists for each round to avoid accumulation
        preMatchingRequestFilters = new ArrayList<>();
        requestFilters = new ArrayList<>();
        responseFilters = new ArrayList<>();
        readerInterceptors = new ArrayList<>();
        writerInterceptors = new ArrayList<>();

        collectFiltersAndInterceptors(roundEnv);
        sortFiltersByPriority();
        processResourceClasses(roundEnv);

        return true;
    }

    private void collectFiltersAndInterceptors(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Provider.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                continue;
            }

            TypeElement typeElement = (TypeElement) element;
            int priority = getPriority(typeElement);
            Set<String> nameBindings = getNameBindings(typeElement);
            boolean isPreMatching = typeElement.getAnnotation(PreMatching.class) != null;

            FilterInfo info = new FilterInfo(typeElement, priority, nameBindings);

            if (implementsInterface(typeElement, "jakarta.ws.rs.container.ContainerRequestFilter")) {
                if (isPreMatching) {
                    preMatchingRequestFilters.add(info);
                    log("Found pre-matching request filter: " + typeElement.getQualifiedName() + " (priority=" + priority + ")");
                } else {
                    requestFilters.add(info);
                    log("Found request filter: " + typeElement.getQualifiedName() + " (priority=" + priority + ")");
                }
            }

            if (implementsInterface(typeElement, "jakarta.ws.rs.container.ContainerResponseFilter")) {
                responseFilters.add(info);
                log("Found response filter: " + typeElement.getQualifiedName() + " (priority=" + priority + ")");
            }

            if (implementsInterface(typeElement, "jakarta.ws.rs.ext.ReaderInterceptor")) {
                readerInterceptors.add(info);
                log("Found reader interceptor: " + typeElement.getQualifiedName() + " (priority=" + priority + ")");
            }

            if (implementsInterface(typeElement, "jakarta.ws.rs.ext.WriterInterceptor")) {
                writerInterceptors.add(info);
                log("Found writer interceptor: " + typeElement.getQualifiedName() + " (priority=" + priority + ")");
            }
        }
    }

    private void sortFiltersByPriority() {
        Comparator<FilterInfo> byPriority = Comparator.comparingInt(f -> f.priority);
        preMatchingRequestFilters.sort(byPriority);
        requestFilters.sort(byPriority);
        responseFilters.sort(byPriority.reversed()); // Response filters run in reverse order
        readerInterceptors.sort(byPriority);
        writerInterceptors.sort(byPriority);
    }

    private void processResourceClasses(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Path.class)) {
            if (element.getKind() == ElementKind.CLASS) {
                try {
                    processResourceClass((TypeElement) element);
                } catch (Exception e) {
                    error("Failed to process " + element + ": " + e.getMessage(), element);
                }
            }
        }
    }

    private int getPriority(TypeElement typeElement) {
        for (AnnotationMirror am : typeElement.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals("jakarta.annotation.Priority")) {
                for (var entry : am.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().toString().equals("value")) {
                        Object value = entry.getValue().getValue();
                        if (value instanceof Integer) {
                            return (Integer) value;
                        }
                        // Handle potential Number type
                        if (value instanceof Number) {
                            return ((Number) value).intValue();
                        }
                    }
                }
            }
        }
        return DEFAULT_PRIORITY;
    }

    private Set<String> getNameBindings(TypeElement typeElement) {
        Set<String> bindings = new HashSet<>();
        for (AnnotationMirror am : typeElement.getAnnotationMirrors()) {
            Element annotationElement = am.getAnnotationType().asElement();
            if (annotationElement.getAnnotation(NameBinding.class) != null) {
                bindings.add(am.getAnnotationType().toString());
            }
        }
        return bindings;
    }

    private Set<String> getNameBindings(ExecutableElement method, TypeElement resourceClass) {
        Set<String> bindings = new HashSet<>();

        for (AnnotationMirror am : method.getAnnotationMirrors()) {
            Element annotationElement = am.getAnnotationType().asElement();
            if (annotationElement.getAnnotation(NameBinding.class) != null) {
                bindings.add(am.getAnnotationType().toString());
            }
        }

        for (AnnotationMirror am : resourceClass.getAnnotationMirrors()) {
            Element annotationElement = am.getAnnotationType().asElement();
            if (annotationElement.getAnnotation(NameBinding.class) != null) {
                bindings.add(am.getAnnotationType().toString());
            }
        }

        return bindings;
    }

    private boolean implementsInterface(TypeElement typeElement, String interfaceName) {
        for (TypeMirror iface : typeElement.getInterfaces()) {
            if (iface.toString().equals(interfaceName)) {
                return true;
            }
        }
        return false;
    }

    private void processResourceClass(TypeElement resourceClass) throws IOException {
        String packageName = getPackageName(resourceClass);
        String className = resourceClass.getSimpleName().toString();
        String generatedClassName = className + "$$JaxRsRouting";

        log("Generating routing for: " + resourceClass.getQualifiedName());

        TypeName resourceTypeName = TypeName.create(resourceClass.getQualifiedName().toString());

        Path classPath = resourceClass.getAnnotation(Path.class);
        String basePath = classPath != null ? normalizePath(classPath.value()) : "";
        String classProduces = getProduces(resourceClass);

        ClassModel.Builder classBuilder = ClassModel.builder()
                .packageName(packageName)
                .name(generatedClassName)
                .accessModifier(AccessModifier.PUBLIC)
                .isFinal(true)
                .addAnnotation(ann -> ann.type(GENERATED).addParameter("value", JaxRsProcessor.class.getName()))
                .javadoc(Javadoc.builder()
                        .addLine("Generated routing for {@link " + className + "}.")
                        .addLine("")
                        .addLine("<p>This class is generated at compile time. DO NOT EDIT.")
                        .build());

        addFields(classBuilder, resourceTypeName);
        addConstructor(classBuilder, resourceTypeName);

        List<RouteInfo> routes = collectRoutes(resourceClass, basePath, classProduces);
        addRegisterMethod(classBuilder, routes);
        addHandlerMethods(classBuilder, routes, resourceClass);
        addHelperMethods(classBuilder);

        writeClass(classBuilder.build(), packageName, generatedClassName, resourceClass);
    }

    private void addFields(ClassModel.Builder classBuilder, TypeName resourceTypeName) {
        classBuilder.addField(Field.builder()
                .name("resource")
                .type(resourceTypeName)
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .build());

        classBuilder.addField(Field.builder()
                .name("objectMapper")
                .type(OBJECT_MAPPER)
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .build());

        classBuilder.addField(Field.builder()
                .name("filterContext")
                .type(FILTER_CONTEXT)
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .build());
    }

    private void addConstructor(ClassModel.Builder classBuilder, TypeName resourceTypeName) {
        classBuilder.addConstructor(ctor -> {
            // Initialize RuntimeDelegate first to support JAX-RS exceptions
            ctor.addContentLine("io.helidon.examples.jaxrs.apt.runtime.SimpleRuntimeDelegate.init();");

            ctor.accessModifier(AccessModifier.PUBLIC)
                    .addContent("this.resource = new ").addContent(resourceTypeName).addContentLine("();")
                    .addContent("this.objectMapper = new ").addContent(OBJECT_MAPPER).addContentLine("();")
                    .addContent("this.filterContext = new ").addContent(FILTER_CONTEXT).addContentLine("();");

            addFilterRegistrations(ctor, preMatchingRequestFilters, "addPreMatchingRequestFilter");
            addFilterRegistrations(ctor, requestFilters, "addRequestFilter");
            addFilterRegistrations(ctor, responseFilters, "addResponseFilter");
            addFilterRegistrations(ctor, readerInterceptors, "addReaderInterceptor");
            addFilterRegistrations(ctor, writerInterceptors, "addWriterInterceptor");
        });
    }

    private void addFilterRegistrations(io.helidon.codegen.classmodel.Constructor.Builder ctor,
                                        List<FilterInfo> filters, String methodName) {
        for (FilterInfo filter : filters) {
            TypeName filterType = TypeName.create(filter.typeElement.getQualifiedName().toString());
            if (filter.nameBindings.isEmpty()) {
                ctor.addContent("this.filterContext.").addContent(methodName).addContent("(new ")
                        .addContent(filterType).addContentLine("());");
            } else {
                ctor.addContent("this.filterContext.").addContent(methodName).addContent("(new ")
                        .addContent(filterType).addContent("(), java.util.Set.of(")
                        .addContent(formatBindings(filter.nameBindings)).addContentLine("));");
            }
        }
    }

    private List<RouteInfo> collectRoutes(TypeElement resourceClass, String basePath, String classProduces) {
        List<RouteInfo> routes = new ArrayList<>();

        for (Element enclosed : resourceClass.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement method = (ExecutableElement) enclosed;
            String httpMethod = getHttpMethod(method);
            if (httpMethod == null) {
                continue;
            }

            String methodPath = getMethodPath(method);
            String fullPath = basePath + methodPath;
            String path = fullPath.isEmpty() ? "/" : fullPath;
            Set<String> methodBindings = getNameBindings(method, resourceClass);

            routes.add(new RouteInfo(httpMethod, path, method, classProduces, methodBindings));
        }

        // Sort: specific paths before parameterized, then by HTTP method for determinism
        routes.sort((a, b) -> {
            boolean aParam = a.path.contains("{");
            boolean bParam = b.path.contains("{");
            if (aParam != bParam) return aParam ? 1 : -1;
            int lenDiff = b.path.length() - a.path.length();
            if (lenDiff != 0) return lenDiff;
            int pathCmp = a.path.compareTo(b.path);
            return pathCmp != 0 ? pathCmp : a.httpMethod.compareTo(b.httpMethod);
        });

        return routes;
    }

    private void addRegisterMethod(ClassModel.Builder classBuilder, List<RouteInfo> routes) {
        Method.Builder registerMethod = Method.builder()
                .name("register")
                .accessModifier(AccessModifier.PUBLIC)
                .addParameter(p -> p.name("routing").type(HTTP_ROUTING_BUILDER))
                .javadoc(Javadoc.builder()
                        .addLine("Register all routes with the routing builder.")
                        .addParameter("routing", "the Helidon HTTP routing builder")
                        .build());

        for (RouteInfo route : routes) {
            registerMethod.addContent("routing.")
                    .addContent(route.httpMethod.toLowerCase())
                    .addContent("(\"").addContent(route.path).addContent("\", this::")
                    .addContent(route.handlerName()).addContentLine(");");
        }

        classBuilder.addMethod(registerMethod.build());
    }

    private void addHandlerMethods(ClassModel.Builder classBuilder, List<RouteInfo> routes, TypeElement resourceClass) {
        for (RouteInfo route : routes) {
            classBuilder.addMethod(generateHandler(route, resourceClass));
        }
    }

    private void addHelperMethods(ClassModel.Builder classBuilder) {
        classBuilder.addMethod(Method.builder()
                .name("sendAbortResponse")
                .accessModifier(AccessModifier.PRIVATE)
                .addParameter(p -> p.name("res").type(SERVER_RESPONSE))
                .addParameter(p -> p.name("ctx").type(REQUEST_CONTEXT))
                .addContentLine("String msg = ctx.getAbortMessage();")
                .addContentLine("res.status(ctx.getAbortStatus()).send(msg != null ? msg : \"\");")
                .build());
    }

    private Method generateHandler(RouteInfo route, TypeElement resourceClass) {
        ExecutableElement method = route.method;
        String methodName = method.getSimpleName().toString();

        Method.Builder handler = Method.builder()
                .name(route.handlerName())
                .accessModifier(AccessModifier.PRIVATE)
                .addParameter(p -> p.name("req").type(SERVER_REQUEST))
                .addParameter(p -> p.name("res").type(SERVER_RESPONSE))
                .javadoc(Javadoc.builder()
                        .addLine("Handler for {@link " + resourceClass.getSimpleName() + "#" + methodName + "}.")
                        .build());

        // Create request context and method bindings before try block so they're accessible in catch blocks
        handler.addContent(REQUEST_CONTEXT).addContent(" requestContext = new ")
                .addContent(REQUEST_CONTEXT).addContentLine("(req);");
        generateMethodBindings(handler, route.nameBindings);

        handler.addContentLine("try {");
        handler.increaseContentPadding();

        // Pre-matching filters
        generatePreMatchingFilters(handler);

        // Post-matching request filters
        generateRequestFilters(handler);

        // Extract parameters
        List<String> paramNames = new ArrayList<>();
        BodyParamInfo bodyParam = extractParameters(method, handler, paramNames);

        // Handle body parameter with reader interceptors
        if (bodyParam != null) {
            generateBodyReading(handler, bodyParam, paramNames);
        }

        // Invoke resource method and handle response
        generateMethodInvocation(handler, route, method, paramNames);

        handler.decreaseContentPadding();
        generateExceptionHandling(handler);

        return handler.build();
    }

    private void generateMethodBindings(Method.Builder handler, Set<String> nameBindings) {
        if (nameBindings.isEmpty()) {
            handler.addContentLine("java.util.Set<String> methodBindings = java.util.Set.of();");
        } else {
            handler.addContent("java.util.Set<String> methodBindings = java.util.Set.of(")
                    .addContent(formatBindings(nameBindings)).addContentLine(");");
        }
    }

    private void generatePreMatchingFilters(Method.Builder handler) {
        handler.addContentLine("for (var filter : filterContext.getPreMatchingRequestFilters()) {");
        handler.increaseContentPadding();
        handler.addContentLine("filter.filter(requestContext);");
        handler.addContentLine("if (requestContext.isAborted()) {");
        handler.increaseContentPadding();
        handler.addContentLine("sendAbortResponse(res, requestContext);");
        handler.addContentLine("return;");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
    }

    private void generateRequestFilters(Method.Builder handler) {
        handler.addContentLine("for (var filterEntry : filterContext.getRequestFiltersWithBindings()) {");
        handler.increaseContentPadding();
        handler.addContentLine("if (filterEntry.matches(methodBindings)) {");
        handler.increaseContentPadding();
        handler.addContentLine("filterEntry.filter().filter(requestContext);");
        handler.addContentLine("if (requestContext.isAborted()) {");
        handler.increaseContentPadding();
        handler.addContentLine("sendAbortResponse(res, requestContext);");
        handler.addContentLine("return;");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
    }

    private BodyParamInfo extractParameters(ExecutableElement method, Method.Builder handler, List<String> paramNames) {
        BodyParamInfo bodyParam = null;

        // First pass: check if we have any form params (direct or in BeanParam) and cache the form data
        boolean hasFormParams = method.getParameters().stream()
                .anyMatch(p -> p.getAnnotation(FormParam.class) != null || hasFormParamInBean(p));

        if (hasFormParams) {
            handler.addContentLine("io.helidon.common.parameters.Parameters _formParams = req.content().as(io.helidon.common.parameters.Parameters.class);");
        }

        // Second pass: extract all parameters
        for (VariableElement param : method.getParameters()) {
            if (isBodyParameter(param)) {
                bodyParam = new BodyParamInfo(
                        param.getSimpleName().toString(),
                        param.asType().toString()
                );
            } else {
                String name = extractParameter(param, handler);
                if (name != null) {
                    paramNames.add(name);
                }
            }
        }

        return bodyParam;
    }

    private void generateBodyReading(Method.Builder handler, BodyParamInfo bodyParam, List<String> paramNames) {
        TypeName bodyType = TypeName.create(bodyParam.type);

        handler.addContent(READER_CONTEXT).addContent(" readerCtx = new ").addContent(READER_CONTEXT)
                .addContent("(req, objectMapper, ").addContent(bodyType).addContentLine(".class);");

        handler.addContentLine("for (var entry : filterContext.getReaderInterceptorsWithBindings()) {");
        handler.increaseContentPadding();
        handler.addContentLine("if (entry.matches(methodBindings)) {");
        handler.increaseContentPadding();
        handler.addContentLine("entry.interceptor().aroundReadFrom(readerCtx);");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
        handler.decreaseContentPadding();
        handler.addContentLine("}");

        handler.addContent(bodyType).addContent(" ").addContent(bodyParam.name)
                .addContent(" = (").addContent(bodyType).addContentLine(") readerCtx.proceed();");

        paramNames.add(bodyParam.name);
    }

    private void generateMethodInvocation(Method.Builder handler, RouteInfo route, ExecutableElement method, List<String> paramNames) {
        String args = String.join(", ", paramNames);
        String invocation = "resource." + method.getSimpleName() + "(" + args + ")";

        TypeMirror returnType = method.getReturnType();
        String produces = getProduces(method);
        if (produces == null) produces = route.classProduces;
        String contentType = produces != null ? produces : "application/json";

        if (returnType.getKind() == TypeKind.VOID) {
            handler.addContent(invocation).addContentLine(";");
            handler.addContent(RESPONSE_CONTEXT).addContent(" responseContext = new ")
                    .addContent(RESPONSE_CONTEXT).addContentLine("(204, null);");
            generateResponseFilters(handler);
            handler.addContentLine("res.status(responseContext.getStatus()).send();");
        } else if (returnType.toString().equals("jakarta.ws.rs.core.Response")) {
            // Handle Response return type
            generateResponseHandling(handler, invocation, contentType);
        } else {
            TypeName resultType = TypeName.create(returnType.toString());
            handler.addContent(resultType).addContent(" result = ").addContent(invocation).addContentLine(";");
            handler.addContent(RESPONSE_CONTEXT).addContent(" responseContext = new ")
                    .addContent(RESPONSE_CONTEXT).addContentLine("(200, result);");

            // JAX-RS order: Writer interceptors execute before response filters
            generateWriterInterceptors(handler, contentType);
            generateResponseFilters(handler);

            // Send the response after all filters/interceptors
            handler.addContentLine("res.status(responseContext.getStatus()).header(\"Content-Type\", \"" + contentType + "\").send(_output);");
        }
    }

    private void generateResponseHandling(Method.Builder handler, String invocation, String defaultContentType) {
        handler.addContentLine("jakarta.ws.rs.core.Response _jaxrsResponse = " + invocation + ";");
        handler.addContentLine("int _status = _jaxrsResponse.getStatus();");
        handler.addContentLine("Object _entity = _jaxrsResponse.getEntity();");

        // Determine content type from Response or use default
        handler.addContent("String _contentType = _jaxrsResponse.getMediaType() != null ? ")
                .addContent("_jaxrsResponse.getMediaType().toString() : \"").addContent(defaultContentType).addContentLine("\";");

        // Create response context
        handler.addContent(RESPONSE_CONTEXT).addContentLine(" responseContext = new " + RESPONSE_CONTEXT + "(_status, _entity);");

        // Copy headers from Response to Helidon response
        handler.addContentLine("for (var _hdr : _jaxrsResponse.getStringHeaders().entrySet()) {");
        handler.addContentLine("    for (var _val : _hdr.getValue()) {");
        handler.addContentLine("        res.header(io.helidon.http.HeaderNames.create(_hdr.getKey()), _val);");
        handler.addContentLine("    }");
        handler.addContentLine("}");

        // Handle entity
        handler.addContentLine("if (_entity != null) {");
        handler.increaseContentPadding();

        // For text/plain String entities, send directly without JSON serialization
        handler.addContentLine("if (_contentType.startsWith(\"text/plain\") && _entity instanceof String) {");
        handler.increaseContentPadding();
        generateResponseFilters(handler);
        handler.addContentLine("res.status(responseContext.getStatus()).header(\"Content-Type\", _contentType).send((String) _entity);");
        handler.decreaseContentPadding();
        handler.addContentLine("} else {");
        handler.increaseContentPadding();

        // JSON or other content types - use writer interceptors
        handler.addContent(WRITER_CONTEXT).addContentLine(" writerCtx = new " + WRITER_CONTEXT + "(_entity, objectMapper);");
        handler.addContentLine("for (var entry : filterContext.getWriterInterceptorsWithBindings()) {");
        handler.addContentLine("    if (entry.matches(methodBindings)) {");
        handler.addContentLine("        entry.interceptor().aroundWriteTo(writerCtx);");
        handler.addContentLine("    }");
        handler.addContentLine("}");
        handler.addContentLine("String _output = writerCtx.getResult();");

        generateResponseFilters(handler);

        handler.addContentLine("res.status(responseContext.getStatus()).header(\"Content-Type\", _contentType).send(_output);");
        handler.decreaseContentPadding();
        handler.addContentLine("}");

        handler.decreaseContentPadding();
        handler.addContentLine("} else {");
        handler.increaseContentPadding();
        generateResponseFilters(handler);
        handler.addContentLine("res.status(responseContext.getStatus()).send();");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
    }

    private void generateResponseFilters(Method.Builder handler) {
        handler.addContentLine("for (var entry : filterContext.getResponseFiltersWithBindings()) {");
        handler.increaseContentPadding();
        handler.addContentLine("if (entry.matches(methodBindings)) {");
        handler.increaseContentPadding();
        handler.addContentLine("entry.filter().filter(requestContext, responseContext);");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
    }

    private void generateWriterInterceptors(Method.Builder handler, String contentType) {
        handler.addContent(WRITER_CONTEXT).addContent(" writerCtx = new ").addContent(WRITER_CONTEXT)
                .addContentLine("(responseContext.getEntity(), objectMapper);");

        // Set the media type so WriterInterceptorContext knows how to serialize
        if ("text/plain".equals(contentType)) {
            handler.addContentLine("writerCtx.setMediaType(jakarta.ws.rs.core.MediaType.TEXT_PLAIN_TYPE);");
        }

        handler.addContentLine("for (var entry : filterContext.getWriterInterceptorsWithBindings()) {");
        handler.increaseContentPadding();
        handler.addContentLine("if (entry.matches(methodBindings)) {");
        handler.increaseContentPadding();
        handler.addContentLine("entry.interceptor().aroundWriteTo(writerCtx);");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
        handler.decreaseContentPadding();
        handler.addContentLine("}");

        // Get the serialized output - response is sent after response filters run
        handler.addContentLine("String _output = writerCtx.getResult();");
    }

    private void generateExceptionHandling(Method.Builder handler) {
        // Catch NotFoundException specifically (returns 404)
        handler.addContent("} catch (").addContent(NOT_FOUND_EXCEPTION).addContentLine(" e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 404, "Not Found");
        handler.decreaseContentPadding();
        // Catch BadRequestException (returns 400)
        handler.addContentLine("} catch (jakarta.ws.rs.BadRequestException e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 400, "Bad Request");
        handler.decreaseContentPadding();
        // Catch NotAuthorizedException (returns 401)
        handler.addContentLine("} catch (jakarta.ws.rs.NotAuthorizedException e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 401, "Unauthorized");
        handler.decreaseContentPadding();
        // Catch ForbiddenException (returns 403)
        handler.addContentLine("} catch (jakarta.ws.rs.ForbiddenException e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 403, "Forbidden");
        handler.decreaseContentPadding();
        // Catch NotAllowedException (returns 405)
        handler.addContentLine("} catch (jakarta.ws.rs.NotAllowedException e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 405, "Method Not Allowed");
        handler.decreaseContentPadding();
        // Catch NotAcceptableException (returns 406)
        handler.addContentLine("} catch (jakarta.ws.rs.NotAcceptableException e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 406, "Not Acceptable");
        handler.decreaseContentPadding();
        // Catch NotSupportedException (returns 415)
        handler.addContentLine("} catch (jakarta.ws.rs.NotSupportedException e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 415, "Unsupported Media Type");
        handler.decreaseContentPadding();
        // Catch InternalServerErrorException (returns 500)
        handler.addContentLine("} catch (jakarta.ws.rs.InternalServerErrorException e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 500, "Internal Server Error");
        handler.decreaseContentPadding();
        // Catch ServiceUnavailableException (returns 503)
        handler.addContentLine("} catch (jakarta.ws.rs.ServiceUnavailableException e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 503, "Service Unavailable");
        handler.decreaseContentPadding();
        // Catch ClientErrorException (4xx errors) - avoid getResponse()
        handler.addContentLine("} catch (jakarta.ws.rs.ClientErrorException e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 400, "Client Error");
        handler.decreaseContentPadding();
        // Catch ServerErrorException (5xx errors) - avoid getResponse()
        handler.addContentLine("} catch (jakarta.ws.rs.ServerErrorException e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 500, "Server Error");
        handler.decreaseContentPadding();
        // Catch RedirectionException (3xx) - avoid getResponse()
        handler.addContentLine("} catch (jakarta.ws.rs.RedirectionException e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 307, "Redirect");
        handler.decreaseContentPadding();
        // Catch generic WebApplicationException - avoid getResponse() which triggers RuntimeDelegate
        handler.addContent("} catch (").addContent(WEB_APPLICATION_EXCEPTION).addContentLine(" e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 500, "Error");
        handler.decreaseContentPadding();
        // Catch all other exceptions
        handler.addContentLine("} catch (Exception e) {");
        handler.increaseContentPadding();
        generateErrorResponse(handler, 500, "Internal Server Error");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
    }

    private void generateErrorResponse(Method.Builder handler, int status, String defaultMessage) {
        handler.addContent("String _errMsg = e.getMessage() != null ? e.getMessage() : \"").addContent(defaultMessage).addContentLine("\";");
        handler.addContent(RESPONSE_CONTEXT).addContent(" responseContext = new ")
                .addContent(RESPONSE_CONTEXT).addContent("(").addContent(String.valueOf(status)).addContentLine(", _errMsg);");
        // Run response filters even for errors (JAX-RS spec)
        handler.addContentLine("try {");
        handler.increaseContentPadding();
        generateResponseFilters(handler);
        handler.decreaseContentPadding();
        handler.addContentLine("} catch (java.io.IOException ioEx) {");
        handler.addContentLine("    // Response filter IOException is suppressed in error handling");
        handler.addContentLine("}");
        handler.addContentLine("res.status(responseContext.getStatus()).send(_errMsg);");
    }

    private boolean isBodyParameter(VariableElement param) {
        return param.getAnnotation(PathParam.class) == null
                && param.getAnnotation(QueryParam.class) == null
                && param.getAnnotation(HeaderParam.class) == null
                && param.getAnnotation(CookieParam.class) == null
                && param.getAnnotation(FormParam.class) == null
                && param.getAnnotation(Context.class) == null
                && param.getAnnotation(BeanParam.class) == null
                && !isPrimitive(param.asType().toString());
    }

    private String extractParameter(VariableElement param, Method.Builder handler) {
        String varName = param.getSimpleName().toString();
        String type = param.asType().toString();
        TypeName typeName = TypeName.create(type);

        DefaultValue defaultValue = param.getAnnotation(DefaultValue.class);
        String defaultVal = defaultValue != null ? defaultValue.value() : null;

        PathParam pathParam = param.getAnnotation(PathParam.class);
        if (pathParam != null) {
            generatePathParam(handler, typeName, varName, pathParam.value(), type);
            return varName;
        }

        QueryParam queryParam = param.getAnnotation(QueryParam.class);
        if (queryParam != null) {
            generateQueryParam(handler, typeName, varName, queryParam.value(), type, defaultVal);
            return varName;
        }

        HeaderParam headerParam = param.getAnnotation(HeaderParam.class);
        if (headerParam != null) {
            generateHeaderParam(handler, typeName, varName, headerParam.value(), defaultVal);
            return varName;
        }

        CookieParam cookieParam = param.getAnnotation(CookieParam.class);
        if (cookieParam != null) {
            generateCookieParam(handler, typeName, varName, cookieParam.value(), defaultVal);
            return varName;
        }

        FormParam formParam = param.getAnnotation(FormParam.class);
        if (formParam != null) {
            generateFormParam(handler, typeName, varName, formParam.value(), type, defaultVal);
            return varName;
        }

        Context context = param.getAnnotation(Context.class);
        if (context != null) {
            return generateContextParam(handler, varName, type);
        }

        BeanParam beanParam = param.getAnnotation(BeanParam.class);
        if (beanParam != null) {
            generateBeanParam(handler, typeName, varName, param.asType());
            return varName;
        }

        return null;
    }

    private void generateBeanParam(Method.Builder handler, TypeName typeName, String varName, TypeMirror beanType) {
        // Create bean instance
        handler.addContent(typeName).addContent(" ").addContent(varName)
                .addContent(" = new ").addContent(typeName).addContentLine("();");

        // Get the type element for the bean class
        TypeElement beanElement = (TypeElement) processingEnv.getTypeUtils().asElement(beanType);
        if (beanElement == null) {
            return;
        }

        // Process all fields in the bean
        for (Element enclosed : beanElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                generateBeanFieldExtraction(handler, varName, field);
            }
        }
    }

    private void generateBeanFieldExtraction(Method.Builder handler, String beanVar, VariableElement field) {
        String fieldName = field.getSimpleName().toString();
        String fieldType = field.asType().toString();
        String setterName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);

        DefaultValue defaultValue = field.getAnnotation(DefaultValue.class);
        String defaultVal = defaultValue != null ? defaultValue.value() : null;

        PathParam pathParam = field.getAnnotation(PathParam.class);
        if (pathParam != null) {
            generateBeanFieldPathParam(handler, beanVar, setterName, pathParam.value(), fieldType);
            return;
        }

        QueryParam queryParam = field.getAnnotation(QueryParam.class);
        if (queryParam != null) {
            generateBeanFieldQueryParam(handler, beanVar, setterName, queryParam.value(), fieldType, defaultVal);
            return;
        }

        HeaderParam headerParam = field.getAnnotation(HeaderParam.class);
        if (headerParam != null) {
            generateBeanFieldHeaderParam(handler, beanVar, setterName, headerParam.value(), defaultVal);
            return;
        }

        CookieParam cookieParam = field.getAnnotation(CookieParam.class);
        if (cookieParam != null) {
            generateBeanFieldCookieParam(handler, beanVar, setterName, cookieParam.value(), defaultVal);
            return;
        }

        FormParam formParam = field.getAnnotation(FormParam.class);
        if (formParam != null) {
            generateBeanFieldFormParam(handler, beanVar, setterName, formParam.value(), fieldType, defaultVal);
        }
    }

    private void generateBeanFieldPathParam(Method.Builder handler, String beanVar, String setter, String paramName, String type) {
        String tempVar = "_bp_" + paramName.replace("-", "_");
        handler.addContent("String ").addContent(tempVar).addContent(" = req.path().pathParameters().get(\"")
                .addContent(escapeJavaString(paramName)).addContentLine("\");");

        if (needsConversion(type)) {
            handler.addContent("if (").addContent(tempVar).addContentLine(" != null) {");
            handler.addContent("    ").addContent(beanVar).addContent(".").addContent(setter).addContent("(")
                    .addContent(convertType(tempVar, type)).addContentLine(");");
            handler.addContentLine("}");
        } else {
            handler.addContent(beanVar).addContent(".").addContent(setter).addContent("(")
                    .addContent(tempVar).addContentLine(");");
        }
    }

    private void generateBeanFieldQueryParam(Method.Builder handler, String beanVar, String setter, String paramName, String type, String defaultVal) {
        String tempVar = "_bq_" + paramName.replace("-", "_");
        String defaultExpr = defaultVal != null ? "\"" + escapeJavaString(defaultVal) + "\"" : "null";

        handler.addContent("String ").addContent(tempVar).addContent(" = req.query().first(\"")
                .addContent(escapeJavaString(paramName)).addContent("\").orElse(").addContent(defaultExpr).addContentLine(");");

        if (needsConversion(type)) {
            handler.addContent("if (").addContent(tempVar).addContentLine(" != null) {");
            handler.addContent("    ").addContent(beanVar).addContent(".").addContent(setter).addContent("(")
                    .addContent(convertType(tempVar, type)).addContentLine(");");
            handler.addContentLine("}");
        } else {
            handler.addContent(beanVar).addContent(".").addContent(setter).addContent("(")
                    .addContent(tempVar).addContentLine(");");
        }
    }

    private void generateBeanFieldHeaderParam(Method.Builder handler, String beanVar, String setter, String paramName, String defaultVal) {
        String tempVar = "_bh_" + paramName.replace("-", "_");
        String defaultExpr = defaultVal != null ? "\"" + escapeJavaString(defaultVal) + "\"" : "null";

        handler.addContent("String ").addContent(tempVar).addContent(" = req.headers().first(io.helidon.http.HeaderNames.create(\"")
                .addContent(escapeJavaString(paramName)).addContent("\")).orElse(").addContent(defaultExpr).addContentLine(");");

        handler.addContent(beanVar).addContent(".").addContent(setter).addContent("(")
                .addContent(tempVar).addContentLine(");");
    }

    private void generateBeanFieldCookieParam(Method.Builder handler, String beanVar, String setter, String paramName, String defaultVal) {
        String tempVar = "_bc_" + paramName.replace("-", "_");
        String defaultExpr = defaultVal != null ? "\"" + escapeJavaString(defaultVal) + "\"" : "null";

        handler.addContent("String ").addContent(tempVar).addContent(" = req.headers().cookies().first(\"")
                .addContent(escapeJavaString(paramName)).addContent("\").orElse(").addContent(defaultExpr).addContentLine(");");

        handler.addContent(beanVar).addContent(".").addContent(setter).addContent("(")
                .addContent(tempVar).addContentLine(");");
    }

    private void generateBeanFieldFormParam(Method.Builder handler, String beanVar, String setter, String paramName, String type, String defaultVal) {
        String tempVar = "_bf_" + paramName.replace("-", "_");
        String defaultExpr = defaultVal != null ? "\"" + escapeJavaString(defaultVal) + "\"" : "null";

        handler.addContent("String ").addContent(tempVar).addContent(" = _formParams.first(\"")
                .addContent(escapeJavaString(paramName)).addContent("\").orElse(").addContent(defaultExpr).addContentLine(");");

        if (needsConversion(type)) {
            handler.addContent("if (").addContent(tempVar).addContentLine(" != null) {");
            handler.addContent("    ").addContent(beanVar).addContent(".").addContent(setter).addContent("(")
                    .addContent(convertType(tempVar, type)).addContentLine(");");
            handler.addContentLine("}");
        } else {
            handler.addContent(beanVar).addContent(".").addContent(setter).addContent("(")
                    .addContent(tempVar).addContentLine(");");
        }
    }

    private void generatePathParam(Method.Builder handler, TypeName typeName, String varName, String paramName, String type) {
        handler.addContent("String _").addContent(varName).addContent(" = req.path().pathParameters().get(\"")
                .addContent(escapeJavaString(paramName)).addContentLine("\");");

        if (needsConversion(type)) {
            handler.addContent(typeName).addContent(" ").addContent(varName).addContent(" = _")
                    .addContent(varName).addContent(" != null ? ")
                    .addContent(convertType("_" + varName, type))
                    .addContentLine(" : null;");
        } else {
            handler.addContent(typeName).addContent(" ").addContent(varName).addContent(" = _")
                    .addContent(varName).addContentLine(";");
        }
    }

    private void generateQueryParam(Method.Builder handler, TypeName typeName, String varName, String paramName, String type, String defaultVal) {
        String defaultExpr = defaultVal != null ? "\"" + escapeJavaString(defaultVal) + "\"" : "null";

        handler.addContent("String _").addContent(varName).addContent(" = req.query().first(\"")
                .addContent(escapeJavaString(paramName)).addContent("\").orElse(").addContent(defaultExpr).addContentLine(");");

        if (needsConversion(type)) {
            handler.addContent(typeName).addContent(" ").addContent(varName).addContent(" = _")
                    .addContent(varName).addContent(" != null ? ")
                    .addContent(convertType("_" + varName, type))
                    .addContentLine(" : null;");
        } else {
            handler.addContent(typeName).addContent(" ").addContent(varName).addContent(" = _")
                    .addContent(varName).addContentLine(";");
        }
    }

    private void generateHeaderParam(Method.Builder handler, TypeName typeName, String varName, String paramName, String defaultVal) {
        String defaultExpr = defaultVal != null ? "\"" + escapeJavaString(defaultVal) + "\"" : "null";

        handler.addContent(typeName).addContent(" ").addContent(varName)
                .addContent(" = req.headers().first(io.helidon.http.HeaderNames.create(\"").addContent(escapeJavaString(paramName))
                .addContent("\")).map(Object::toString).orElse(").addContent(defaultExpr).addContentLine(");");
    }

    private void generateCookieParam(Method.Builder handler, TypeName typeName, String varName, String paramName, String defaultVal) {
        String defaultExpr = defaultVal != null ? "\"" + escapeJavaString(defaultVal) + "\"" : "null";

        handler.addContent(typeName).addContent(" ").addContent(varName)
                .addContent(" = req.headers().cookies().first(\"").addContent(escapeJavaString(paramName))
                .addContent("\").orElse(").addContent(defaultExpr).addContentLine(");");
    }

    private void generateFormParam(Method.Builder handler, TypeName typeName, String varName, String paramName, String type, String defaultVal) {
        String defaultExpr = defaultVal != null ? "\"" + escapeJavaString(defaultVal) + "\"" : "null";

        // Use cached _formParams instead of reading request body again
        handler.addContent("String _").addContent(varName)
                .addContent(" = _formParams.first(\"")
                .addContent(escapeJavaString(paramName)).addContent("\").orElse(").addContent(defaultExpr).addContentLine(");");

        if (needsConversion(type)) {
            handler.addContent(typeName).addContent(" ").addContent(varName).addContent(" = _")
                    .addContent(varName).addContent(" != null ? ")
                    .addContent(convertType("_" + varName, type))
                    .addContentLine(" : null;");
        } else {
            handler.addContent(typeName).addContent(" ").addContent(varName).addContent(" = _")
                    .addContent(varName).addContentLine(";");
        }
    }

    private String generateContextParam(Method.Builder handler, String varName, String type) {
        if (type.equals("jakarta.ws.rs.core.UriInfo")) {
            handler.addContent(URI_INFO).addContent(" ").addContent(varName)
                    .addContent(" = new ").addContent(URI_INFO).addContentLine("(req);");
            return varName;
        } else if (type.equals("jakarta.ws.rs.core.HttpHeaders")) {
            handler.addContent(HTTP_HEADERS).addContent(" ").addContent(varName)
                    .addContent(" = new ").addContent(HTTP_HEADERS).addContentLine("(req);");
            return varName;
        }
        return null;
    }

    private boolean needsConversion(String type) {
        return switch (type) {
            case "java.lang.Long", "long", "java.lang.Integer", "int",
                    "java.lang.Double", "double", "java.lang.Boolean", "boolean" -> true;
            default -> false;
        };
    }

    private String convertType(String value, String type) {
        return switch (type) {
            case "java.lang.Long", "long" -> "Long.parseLong(" + value + ")";
            case "java.lang.Integer", "int" -> "Integer.parseInt(" + value + ")";
            case "java.lang.Double", "double" -> "Double.parseDouble(" + value + ")";
            case "java.lang.Boolean", "boolean" -> "Boolean.parseBoolean(" + value + ")";
            default -> value;
        };
    }

    private boolean isPrimitive(String type) {
        return Set.of("int", "long", "double", "boolean",
                "java.lang.String", "java.lang.Integer", "java.lang.Long",
                "java.lang.Double", "java.lang.Boolean").contains(type);
    }

    private boolean hasFormParamInBean(VariableElement param) {
        if (param.getAnnotation(BeanParam.class) == null) {
            return false;
        }
        TypeElement beanElement = (TypeElement) processingEnv.getTypeUtils().asElement(param.asType());
        if (beanElement == null) {
            return false;
        }
        for (Element enclosed : beanElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD &&
                enclosed.getAnnotation(FormParam.class) != null) {
                return true;
            }
        }
        return false;
    }

    private String escapeJavaString(String value) {
        if (value == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private String formatBindings(Set<String> bindings) {
        return bindings.stream()
                .map(b -> "\"" + escapeJavaString(b) + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private String getHttpMethod(ExecutableElement method) {
        if (method.getAnnotation(GET.class) != null) return "GET";
        if (method.getAnnotation(POST.class) != null) return "POST";
        if (method.getAnnotation(PUT.class) != null) return "PUT";
        if (method.getAnnotation(DELETE.class) != null) return "DELETE";
        if (method.getAnnotation(PATCH.class) != null) return "PATCH";
        if (method.getAnnotation(HEAD.class) != null) return "HEAD";
        if (method.getAnnotation(OPTIONS.class) != null) return "OPTIONS";
        return null;
    }

    private String getMethodPath(ExecutableElement method) {
        Path path = method.getAnnotation(Path.class);
        return path != null ? normalizePath(path.value()) : "";
    }

    private String getProduces(Element element) {
        Produces produces = element.getAnnotation(Produces.class);
        return produces != null && produces.value().length > 0 ? produces.value()[0] : null;
    }

    private String normalizePath(String path) {
        if (!path.startsWith("/")) path = "/" + path;
        if (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
        return path;
    }

    private String getPackageName(TypeElement element) {
        return processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    }

    private void writeClass(ClassModel classModel, String packageName, String className, TypeElement originatingElement) throws IOException {
        JavaFileObject sourceFile = filer.createSourceFile(packageName + "." + className, originatingElement);
        try (Writer writer = sourceFile.openWriter()) {
            classModel.write(writer);
        }
        log("Generated: " + packageName + "." + className);
    }

    private void log(String message) {
        messager.printMessage(Diagnostic.Kind.NOTE, message);
    }

    private void error(String message, Element element) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    // Internal data classes
    private record FilterInfo(TypeElement typeElement, int priority, Set<String> nameBindings) {}
    private record RouteInfo(String httpMethod, String path, ExecutableElement method, String classProduces, Set<String> nameBindings) {
        String handlerName() {
            return method.getSimpleName() + "_handler";
        }
    }
    private record BodyParamInfo(String name, String type) {}
}
