package edu.cmu.oli.content;

import edu.cmu.oli.content.boundary.endpoints.*;
import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder;
import io.swagger.v3.oas.integration.OpenApiConfigurationException;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Raphael Gachuhi
 */
@ApplicationPath("api")
public class JAXRSConfiguration extends Application {
    Logger log = LoggerFactory.getLogger(JAXRSConfiguration.class);

    public JAXRSConfiguration() {
        OpenAPI oas = new OpenAPI();
        Info info = new Info().title("Simon Content Service")
                .description("API endpoints details published by the Content Service")
                .contact(new Contact().email("oli-help@cmu.edu"))
                .license(new License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0.html"));
        oas.setInfo(info);
        List<Server> servers = new ArrayList<>();
        Server server = new Server();
        server.setUrl("/content-service/api");
        server.setDescription("Server");
        servers.add(server);
        oas.setServers(servers);

        Map<String, SecurityScheme> securitySchemes = new HashMap<>();
        SecurityScheme secScheme = new SecurityScheme();
        secScheme.setType(SecurityScheme.Type.HTTP);
        secScheme.setScheme("bearer");
        secScheme.setBearerFormat("JWT");
        securitySchemes.put("oliJWT", secScheme);

        Components components = new Components();
        components.setSecuritySchemes(securitySchemes);
        oas.setComponents(components);

        List<SecurityRequirement> security = new ArrayList<>();
        SecurityRequirement secRequirement = new SecurityRequirement();
        secRequirement.addList("oliJWT");
        security.add(secRequirement);
        oas.setSecurity(security);

        SwaggerConfiguration oasConfig = new SwaggerConfiguration().openAPI(oas)
                .resourcePackages(Stream.of("edu.cmu.oli.content.api.endpoints").collect(Collectors.toSet()));
        try {
            new JaxrsOpenApiContextBuilder().application(this).openApiConfiguration(oasConfig).buildContext(true);
        } catch (OpenApiConfigurationException e) {
        }
    }

    @Override
    public Set<Class<?>> getClasses() {
        HashSet<Class<?>> set = new HashSet<>();

        set.add(ContentPackageResource.class);
        set.add(ContentResource.class);
        set.add(ResourceDelivery.class);
        set.add(WebResource.class);
        set.add(LongPollResource.class);
        set.add(LockResource.class);
        set.add(DeveloperResource.class);
        set.add(CorsFilter.class);
        set.add(AnalyticsResource.class);
        set.add(EdgeResource.class);
        set.add(LDModelResource.class);

        set.add(io.swagger.v3.jaxrs2.integration.resources.OpenApiResource.class);

        return set;
    }
}
