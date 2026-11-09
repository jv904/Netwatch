package com.netwatch.netwatch_api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração de CORS ({@code netwatch.cors.*}).
 *
 * @param allowedOrigins origens autorizadas a chamar a API pelo navegador (ex.: o frontend Angular)
 */
@ConfigurationProperties("netwatch.cors")
public record CorsProperties(@DefaultValue("http://localhost:4200") List<String> allowedOrigins) {
}
