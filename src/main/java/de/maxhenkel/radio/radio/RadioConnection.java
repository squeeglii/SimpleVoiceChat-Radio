package de.maxhenkel.radio.radio;

import de.maxhenkel.radio.Radio;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author https://github.com/mrchaoss1
 */
public final class RadioConnection {

    private static final String USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_REDIRECTS = 5;

    private RadioConnection() {
    }

    public static InputStream open(String url) throws IOException {
        String currentUrl = url;

        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            URI uri;
            try {
                uri = new URI(currentUrl);
            } catch (URISyntaxException e) {
                throw new IOException("Malformed radio stream URL: " + currentUrl, e);
            }

            String scheme = uri.getScheme();
            if (scheme == null) {
                throw new IOException("Radio stream URL is missing a scheme (http/https): " + currentUrl);
            }
            scheme = scheme.toLowerCase(Locale.ROOT);

            boolean https = scheme.equals("https");
            if (!https && !scheme.equals("http")) {
                throw new IOException("Unsupported URL scheme '%s'. Only http and https are supported.".formatted(scheme));
            }

            String host = uri.getHost();
            if (host == null) {
                throw new IOException("Could not determine host from radio stream URL: " + currentUrl);
            }
            int port = uri.getPort() != -1 ? uri.getPort() : (https ? 443 : 80);

            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (uri.getRawQuery() != null) {
                path += "?" + uri.getRawQuery();
            }

            Socket socket = https
                    ? SSLSocketFactory.getDefault().createSocket()
                    : new Socket();

            boolean handedOff = false;
            try {
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(READ_TIMEOUT_MS);

                writeRequest(socket, host, port, path, https);

                InputStream in = socket.getInputStream();
                String statusLine = readLine(in);
                if (statusLine == null) {
                    throw new IOException("Empty response from radio stream: " + currentUrl);
                }

                int statusCode = parseStatusCode(statusLine);
                Map<String, String> headers = readHeaders(in);

                if (isRedirect(statusCode)) {
                    String location = headers.get("location");
                    if (location == null || location.isBlank()) {
                        throw new IOException("Radio stream redirected (%d) without a location: %s".formatted(statusCode, currentUrl));
                    }
                    currentUrl = uri.resolve(location).toString();
                    continue;
                }

                if (statusCode != 200 && statusCode != 206) {
                    throw new IOException("Radio stream returned status %d: %s".formatted(statusCode, statusLine.trim()));
                }

                validateContentType(headers.get("content-type"), currentUrl);

                InputStream body = in;
                int metaInterval = parseMetaInterval(headers.get("icy-metaint"));
                if (metaInterval > 0) {
                    body = new IcyMetadataInputStream(body, metaInterval);
                }

                Radio.LOGGER.debug("Connected to radio stream {} (content-type={}, icy-metaint={})",
                        currentUrl, headers.get("content-type"), metaInterval);

                handedOff = true;
                return new BufferedInputStream(body);
            } finally {
                if (!handedOff) {
                    closeQuietly(socket);
                }
            }
        }

        throw new IOException("Too many redirects (>%d) for radio stream: %s".formatted(MAX_REDIRECTS, url));
    }

    private static void writeRequest(Socket socket, String host, int port, String path, boolean https) throws IOException {
        boolean defaultPort = (https && port == 443) || (!https && port == 80);
        String hostHeader = defaultPort ? host : host + ":" + port;
        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + "User-Agent: " + USER_AGENT + "\r\n"
                + "Accept: */*\r\n"
                + "Icy-MetaData: 1\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static int parseStatusCode(String statusLine) throws IOException {
        String[] parts = statusLine.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IOException("Malformed status line from radio stream: " + statusLine);
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IOException("Could not parse status code from radio stream: " + statusLine, e);
        }
    }

    private static Map<String, String> readHeaders(InputStream in) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            headers.put(name, value);
        }
        return headers;
    }

    private static void validateContentType(String contentType, String url) throws IOException {
        if (contentType == null) {
            return;
        }
        String type = contentType.toLowerCase(Locale.ROOT);

        if (type.startsWith("text/") || type.contains("html") || type.contains("json") || type.contains("xml")) {
            throw new IOException("URL '%s' is a web page (%s), not an audio stream. Use a direct MP3 stream URL.".formatted(url, contentType));
        }
        if (type.contains("mpeg") || type.contains("mp3") || type.contains("octet-stream")) {
            return;
        }
        if (type.contains("aac") || type.contains("ogg") || type.contains("opus") || type.contains("flac") || type.contains("wav")) {
            throw new IOException("Unsupported audio format '%s' for '%s'. Only MP3 streams are supported.".formatted(contentType, url));
        }
    }

    private static int parseMetaInterval(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                return sb.toString();
            }
            if (c != '\r') {
                sb.append((char) c);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
