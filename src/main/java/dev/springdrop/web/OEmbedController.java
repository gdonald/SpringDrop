package dev.springdrop.web;

import dev.springdrop.kernel.media.MediaSourceException;
import dev.springdrop.kernel.media.oembed.OEmbedEmbeds;
import dev.springdrop.kernel.media.oembed.OEmbedResource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

/**
 * The page a remote media item's embed markup is served alone on, for a page of
 * the site to frame. Its policy lets it frame https pages and nothing else run,
 * and only the site may frame it. An address without a valid hash, or one the
 * provider cannot describe, is not found.
 */
@Controller
public class OEmbedController {

    public static final String POLICY = "default-src 'none'; frame-src https:; img-src https: data:; "
            + "style-src 'unsafe-inline'; frame-ancestors 'self'";

    private final OEmbedEmbeds embeds;

    public OEmbedController(OEmbedEmbeds embeds) {
        this.embeds = embeds;
    }

    @GetMapping(OEmbedEmbeds.PATH)
    public ResponseEntity<String> embed(@RequestParam(OEmbedEmbeds.URL) String address,
            @RequestParam(name = OEmbedEmbeds.HASH, required = false) String hash, HttpServletResponse response) {
        if (!embeds.validHash(address, hash)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        OEmbedResource resource;
        try {
            resource = embeds.resource(address);
        } catch (MediaSourceException unavailable) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String page = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>"
                + HtmlUtils.htmlEscape(resource.title())
                + "</title><style>html,body{margin:0;height:100%;overflow:hidden}"
                + "iframe{width:100%;height:100%;border:0}</style></head><body>" + resource.html() + "</body></html>";
        response.setHeader("Content-Security-Policy", POLICY);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(page);
    }
}
