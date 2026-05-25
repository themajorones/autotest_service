package dev.themajorones.ats.config;

import java.io.File;

import org.springframework.boot.web.server.WebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfigurer implements WebServerFactoryCustomizer<WebServerFactory> {

    @Override
    public void customize(WebServerFactory factory) {
        if (factory instanceof ConfigurableServletWebServerFactory servletFactory) {
            File staticRoot = new File("target/classes/static");
            if (staticRoot.exists() && staticRoot.isDirectory()) {
                servletFactory.setDocumentRoot(staticRoot);
            }
        }
    }
}
