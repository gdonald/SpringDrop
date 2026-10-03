package dev.springdrop.web;

import dev.springdrop.kernel.security.RedirectSafety;
import dev.springdrop.kernel.theme.BigPipe;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Where a browser without JavaScript is sent, so its pages are drawn whole from then on. */
@Controller
public class BigPipeController {

    private final RedirectSafety redirects;

    public BigPipeController(RedirectSafety redirects) {
        this.redirects = redirects;
    }

    @GetMapping(BigPipe.NO_JS_PATH)
    public String noJavaScript(@RequestParam(name = "destination", defaultValue = "/") String destination,
            HttpServletResponse response) {
        Cookie cookie = new Cookie(BigPipe.NO_JS_COOKIE, "1");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return "redirect:" + redirects.destinationOr(destination, "/");
    }
}
