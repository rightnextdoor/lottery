package com.lotteryapp.lottery.ingestion.source;

import com.lotteryapp.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.IDN;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
public class DefaultHttpDrawSourceClient implements DrawSourceClient {

    private final HttpClient httpClient;

    private final Set<String> allowedDomains;
    private final int maxBytes;
    private final int maxRedirects;

    public DefaultHttpDrawSourceClient(
            @Value("${lottery.ingestion.allowedDomains:}") List<String> allowedDomains,
            @Value("${lottery.ingestion.maxBytes:5242880}") int maxBytes,
            @Value("${lottery.ingestion.maxRedirects:5}") int maxRedirects
    ) {
        this.allowedDomains = normalizeDomains(allowedDomains);
        this.maxBytes = maxBytes;
        this.maxRedirects = maxRedirects;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public FetchedContent fetch(String url, Map<String, String> headers) {
        if (url == null || url.isBlank()) {
            throw new BadRequestException("url is required");
        }

        URI uri = URI.create(url);
        validateUri(uri);

        URI current = uri;
        for (int redirectCount = 0; redirectCount <= maxRedirects; redirectCount++) {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(current)
                    .timeout(Duration.ofSeconds(25))
                    .GET();

            req.header("User-Agent", "LotteryApp/1.0");

            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        req.header(e.getKey(), e.getValue());
                    }
                }
            }

            HttpResponse<InputStream> resp;
            try {
                resp = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (Exception e) {
                throw new BadRequestException("Failed to download content: " + safeMsg(e));
            }

            int status = resp.statusCode();

            if (isRedirect(status)) {
                String location = resp.headers().firstValue("location").orElse(null);
                if (location == null || location.isBlank()) {
                    throw new BadRequestException("Redirect response missing Location header (status=" + status + ")");
                }
                URI next = current.resolve(location);
                validateUri(next);
                current = next;
                continue;
            }

            if (status < 200 || status >= 300) {
                throw new BadRequestException("Download failed with status=" + status, Map.of(
                        "statusCode", status,
                        "url", current.toString()
                ));
            }

            String contentType = resp.headers().firstValue("content-type").orElse(null);

            resp.headers().firstValue("content-length").ifPresent(cl -> {
                try {
                    long len = Long.parseLong(cl);
                    if (len > maxBytes) {
                        throw new BadRequestException("Downloaded content exceeds maxBytes (" + maxBytes + ")", Map.of(
                                "contentLength", len,
                                "maxBytes", maxBytes
                        ));
                    }
                } catch (NumberFormatException ignored) { }
            });

            byte[] bytes = readUpToMax(resp.body(), maxBytes);

            // Soft content-type check: don’t block if missing, but block obvious mismatches.
            if (contentType != null) {
                String ct = contentType.toLowerCase(Locale.ROOT);
                boolean ok =
                        ct.contains("application/pdf")
                                || ct.contains("text/csv")
                                || ct.contains("application/json")
                                || ct.contains("text/html")
                                || ct.contains("text/plain")
                                || ct.contains("application/octet-stream");
                if (!ok) {
                    throw new BadRequestException("Unsupported content-type: " + contentType, Map.of(
                            "contentType", contentType,
                            "url", current.toString()
                    ));
                }
            }

            return new FetchedContent(bytes, contentType, current.toString(), status);
        }

        throw new BadRequestException("Too many redirects (maxRedirects=" + maxRedirects + ")", Map.of("url", url));
    }

    private void validateUri(URI uri) {
        if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BadRequestException("Only https URLs are allowed", Map.of("url", String.valueOf(uri)));
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BadRequestException("URL host is missing", Map.of("url", String.valueOf(uri)));
        }

        String normalizedHost = normalizeHost(host);

        if (!allowedDomains.isEmpty()) {
            boolean ok = allowedDomains.contains(normalizedHost);
            if (!ok) {
                for (String allowed : allowedDomains) {
                    if (normalizedHost.endsWith("." + allowed)) {
                        ok = true;
                        break;
                    }
                }
            }
            if (!ok) {
                throw new BadRequestException("URL host not allowed: " + normalizedHost, Map.of(
                        "host", normalizedHost,
                        "url", String.valueOf(uri)
                ));
            }
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static byte[] readUpToMax(InputStream in, int maxBytes) {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;

            while (true) {
                int r = input.read(buf);
                if (r < 0) break;

                total += r;
                if (total > maxBytes) {
                    throw new BadRequestException("Downloaded content exceeds maxBytes (" + maxBytes + ")");
                }

                out.write(buf, 0, r);
            }

            return out.toByteArray();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Failed to read download stream: " + safeMsg(e));
        }
    }

    private static Set<String> normalizeDomains(List<String> domains) {
        if (domains == null) return Set.of();
        Set<String> out = new HashSet<>();
        for (String d : domains) {
            if (d == null) continue;
            String t = d.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String normalizeHost(String host) {
        return IDN.toASCII(host.trim().toLowerCase(Locale.ROOT));
    }

    private static String safeMsg(Exception e) {
        if (e == null) return "unknown";
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? e.getClass().getSimpleName() : m;
    }
}
