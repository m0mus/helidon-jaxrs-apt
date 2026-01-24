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

        handler.addContentLine("try {");
        handler.increaseContentPadding();

        // Create request context
        handler.addContent(REQUEST_CONTEXT).addContent(" requestContext = new ")
                .addContent(REQUEST_CONTEXT).addContentLine("(req);");

        // Method bindings for name-bound filter matching
        generateMethodBindings(handler, route.nameBindings);

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
        } else {
            TypeName resultType = TypeName.create(returnType.toString());
            handler.addContent(resultType).addContent(" result = ").addContent(invocation).addContentLine(";");
            handler.addContent(RESPONSE_CONTEXT).addContent(" responseContext = new ")
                    .addContent(RESPONSE_CONTEXT).addContentLine("(200, result);");
            generateResponseFilters(handler);

            if (returnType.toString().equals("java.lang.String") && "text/plain".equals(produces)) {
                handler.addContentLine("Object entity = responseContext.getEntity();");
                handler.addContentLine("String body = entity != null ? entity.toString() : \"\";");
                handler.addContentLine("res.status(responseContext.getStatus()).header(\"Content-Type\", \"text/plain\").send(body);");
            } else {
                generateWriterInterceptors(handler, contentType);
            }
        }
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

        handler.addContentLine("for (var entry : filterContext.getWriterInterceptorsWithBindings()) {");
        handler.increaseContentPadding();
        handler.addContentLine("if (entry.matches(methodBindings)) {");
        handler.increaseContentPadding();
        handler.addContentLine("entry.interceptor().aroundWriteTo(writerCtx);");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
        handler.decreaseContentPadding();
        handler.addContentLine("}");

        handler.addContentLine("String json = writerCtx.getResult();");
        handler.addContent("res.status(responseContext.getStatus()).header(\"Content-Type\", \"")
                .addContent(contentType).addContentLine("\").send(json);");
    }

    private void generateExceptionHandling(Method.Builder handler) {
        handler.addContent("} catch (").addContent(NOT_FOUND_EXCEPTION).addContentLine(" e) {");
        handler.increaseContentPadding();
        handler.addContentLine("res.status(404).send(e.getMessage() != null ? e.getMessage() : \"Not Found\");");
        handler.decreaseContentPadding();
        handler.addContent("} catch (").addContent(WEB_APPLICATION_EXCEPTION).addContentLine(" e) {");
        handler.increaseContentPadding();
        handler.addContentLine("int status = e.getResponse() != null ? e.getResponse().getStatus() : 500;");
        handler.addContentLine("res.status(status).send(e.getMessage() != null ? e.getMessage() : \"Error\");");
        handler.decreaseContentPadding();
        handler.addContentLine("} catch (Exception e) {");
        handler.increaseContentPadding();
        handler.addContentLine("res.status(500).send(\"Internal Server Error: \" + e.getMessage());");
        handler.decreaseContentPadding();
        handler.addContentLine("}");
    }

    private boolean isBodyParameter(VariableElement param) {
        return param.getAnnotation(PathParam.class) == null
                && param.getAnnotation(QueryParam.class) == null
                && param.getAnnotation(HeaderParam.class) == null
                && param.getAnnotation(CookieParam.class) == null
                && param.getAnnotation(FormParam.class) == null
                && param.getAnnotation(Context.class) == null
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

        return null;
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
                .addContent(" = req.headers().first(\"").addContent(escapeJavaString(paramName))
                .addContent("\").map(Object::toString).orElse(").addContent(defaultExpr).addContentLine(");");
    }

    private void generateCookieParam(Method.Builder handler, TypeName typeName, String varName, String paramName, String defaultVal) {
        String defaultExpr = defaultVal != null ? "\"" + escapeJavaString(defaultVal) + "\"" : "null";

        handler.addContent(typeName).addContent(" ").addContent(varName)
                .addContent(" = req.headers().cookies().first(\"").addContent(escapeJavaString(paramName))
                .addContent("\").orElse(").addContent(defaultExpr).addContentLine(");");
    }

    private void generateFormParam(Method.Builder handler, TypeName typeName, String varName, String paramName, String type, String defaultVal) {
        String defaultExpr = defaultVal != null ? "\"" + escapeJavaString(defaultVal) + "\"" : "null";

        handler.addContent("String _").addContent(varName)
                .addContent(" = req.content().as(io.helidon.http.media.FormParams.class).first(\"")
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
