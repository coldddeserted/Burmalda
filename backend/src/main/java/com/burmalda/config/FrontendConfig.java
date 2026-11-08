package com.burmalda.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Serves the frontend straight from the repository root (one copy for the whole team):
 *
 *   Burmalda/
 *   ├── login.html, employee.html, accountant.html, index.html, css/, js/   <- frontend
 *   └── backend/                                                           <- this app
 *
 * Only the frontend files are exposed (whitelist below) — never backend sources, .git, etc.
 * Files are read from disk on every request (no cache), so frontend edits show up after F5,
 * without rebuilding or restarting the server.
 */
@Configuration
public class FrontendConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(FrontendConfig.class);

    /** What the browser may load. Everything else -> 404. */
    private static final Pattern ALLOWED = Pattern.compile(
            "[\\w-]+\\.html|css/[\\w.-]+\\.css|js/[\\w.-]+\\.js|(img|images|assets)/[\\w./-]+\\.(png|jpe?g|svg|gif|webp|ico)|favicon\\.ico");

    private final Path dir;

    public FrontendConfig(@Value("${app.frontend-dir:}") String configured) {
        this.dir = locate(configured);
        if (dir == null) {
            log.warn("Frontend not found (looked for login.html + js/api.js in app.frontend-dir, '..' and '.'). "
                    + "Only the API is available.");
        } else {
            log.info("Serving frontend from {}", dir);
        }
    }

    /** IDEA runs the app from backend/, so the frontend is one level up ("..") by default. */
    static Path locate(String configured) {
        List<String> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) candidates.add(configured);
        candidates.add("..");
        candidates.add(".");
        for (String c : candidates) {
            Path p = Path.of(c).toAbsolutePath().normalize();
            if (Files.isRegularFile(p.resolve("login.html")) && Files.isRegularFile(p.resolve("js").resolve("api.js"))) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (dir == null) return;
        registry.addResourceHandler("/**")
                .addResourceLocations(dir.toUri().toString())
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        return ALLOWED.matcher(path).matches() ? super.getResource(path, location) : null;
                    }
                })
                .addTransformer(new RealBackendSwitch());
    }

    /**
     * Safety net: when the backend serves the frontend, it must talk to the backend.
     * js/api.js has a USE_MOCK switch for opening the pages without a server; if a copy with
     * USE_MOCK: true gets committed, the site would silently show demo data. So we flip it here.
     */
    static class RealBackendSwitch implements ResourceTransformer {
        private static final Pattern MOCK_ON = Pattern.compile("USE_MOCK\\s*:\\s*true");

        @Override
        public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
                throws IOException {
            resource = chain.transform(request, resource);
            if (!"api.js".equals(resource.getFilename())) return resource;
            String js = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
            if (!MOCK_ON.matcher(js).find()) return resource;
            log.warn("js/api.js has USE_MOCK: true — serving it with USE_MOCK: false (real backend)");
            return new TransformedResource(resource, MOCK_ON.matcher(js).replaceFirst("USE_MOCK: false")
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (dir != null) registry.addViewController("/").setViewName("forward:/index.html");
    }
}
