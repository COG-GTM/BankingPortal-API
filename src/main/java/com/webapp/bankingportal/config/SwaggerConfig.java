package com.webapp.bankingportal.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.LinkedHashMap;
import java.util.Map;

import com.webapp.bankingportal.util.ApiMessages;

import lombok.val;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String UNAUTHENTICATED_MESSAGE =
            "Access Denied !! Full authentication is required to access this resource";

    @Bean
    public OpenAPI customOpenAPI() {
        val securitySchemeName = "Bearer";
        return new OpenAPI()
                .info(new Info().title("Banking Portal API")
                        .description("This is auth service use for validate the user.")
                        .version("v0.0.1")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                .externalDocs(new ExternalDocumentation()
                        .description("BankingPortal-API Wiki Documentation")
                        .url("https://github.com/abhi9720/BankingPortal-API/wiki"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(
                        new Components()
                                .addSecuritySchemes(securitySchemeName,
                                        new SecurityScheme()
                                                .name(securitySchemeName)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT"))
                                .addResponses("Unauthenticated", textResponse(
                                        "Missing, invalid or expired JWT",
                                        Map.of("missingToken", UNAUTHENTICATED_MESSAGE)))
                                .addResponses("PinOrAuthError", textResponse(
                                        "Missing/invalid JWT, or the PIN is missing, wrong or not yet created",
                                        orderedExamples(
                                                "missingToken", UNAUTHENTICATED_MESSAGE,
                                                "invalidPin", ApiMessages.PIN_INVALID_ERROR.getMessage(),
                                                "emptyPin", ApiMessages.PIN_EMPTY_ERROR.getMessage(),
                                                "pinNotCreated", ApiMessages.PIN_NOT_CREATED.getMessage())))
                                .addResponses("AccountNotFound", textResponse(
                                        "Account does not exist",
                                        Map.of("accountNotFound", ApiMessages.ACCOUNT_NOT_FOUND.getMessage()))));
    }

    private static ApiResponse textResponse(String description, Map<String, String> examples) {
        val mediaType = new MediaType().schema(new StringSchema());
        examples.forEach((name, value) -> mediaType.addExamples(name, new Example().value(value)));
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("text/plain", mediaType));
    }

    private static Map<String, String> orderedExamples(String... namesAndValues) {
        val examples = new LinkedHashMap<String, String>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            examples.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return examples;
    }

}
