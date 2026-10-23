package dev.vrailas.payments.assistant.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Optional shared secret for the API, so a public deployment cannot be used to burn the Gemini quota. The UI and health probes stay open. */
public class AccessTokenFilter extends OncePerRequestFilter {

    private final byte[] token;

    public AccessTokenFilter(String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String given = request.getHeader("X-Access-Token");
        if (given != null && MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
