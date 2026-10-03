package dev.springdrop.kernel.path;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/** Reaches the filter's request wrapper from tests in other packages. */
public class PathAliasFilterTestAccess extends HttpServletRequestWrapper {

    public PathAliasFilterTestAccess(HttpServletRequest request, String source) {
        super(new PathAliasFilter.SourceRequest(request, source));
    }
}
