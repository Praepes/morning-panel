package com.xperiatouch.clock;

import android.util.Xml;
import android.text.Html;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.List;

final class RssFeedClient {
    static final class Article {
        final String title;
        final String url;
        final String source;
        final String description;
        final long publishedAt;
        final String imageUrl;
        Article(String title, String url, String source, String description, long publishedAt, String imageUrl) {
            this.title = title;
            this.url = url;
            this.source = source;
            this.description = description;
            this.publishedAt = publishedAt;
            this.imageUrl = imageUrl;
        }
    }

    private RssFeedClient() { }

    static List<Article> fetch(String address, int limit) throws Exception {
        URL url = new URL(address);
        if (!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol()))
            throw new IllegalArgumentException("RSS feed URL must use HTTP or HTTPS");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(9000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "XperiaTouchClock/0.1");
        try {
            if (connection.getResponseCode() != 200) throw new java.io.IOException("RSS feed returned HTTP " + connection.getResponseCode());
            return parse(connection.getInputStream(), limit, sourceName(url.getHost()), url);
        } finally {
            connection.disconnect();
        }
    }

    private static List<Article> parse(InputStream input, int limit, String source, URL feedUrl) throws Exception {
        ArrayList<Article> articles = new ArrayList<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        parser.setInput(input, "UTF-8");
        boolean inside = false;
        String title = "";
        String link = "";
        String description = "";
        String content = "";
        String published = "";
        String mediaThumbnail = "";
        String mediaContent = "";
        String enclosureImage = "";
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT && articles.size() < limit) {
            String tag = parser.getName();
            if (event == XmlPullParser.START_TAG && ("item".equalsIgnoreCase(tag) || "entry".equalsIgnoreCase(tag))) {
                inside = true;
                title = "";
                link = "";
                description = "";
                content = "";
                published = "";
                mediaThumbnail = "";
                mediaContent = "";
                enclosureImage = "";
            } else if (inside && event == XmlPullParser.START_TAG && "title".equalsIgnoreCase(tag)) {
                title = readElementText(parser).trim();
            } else if (inside && event == XmlPullParser.START_TAG && "link".equalsIgnoreCase(tag)) {
                String href = parser.getAttributeValue(null, "href");
                link = href == null ? readElementText(parser).trim() : href.trim();
            } else if (inside && event == XmlPullParser.START_TAG && "description".equalsIgnoreCase(tag)) {
                description = readElementText(parser).trim();
            } else if (inside && event == XmlPullParser.START_TAG
                    && ("content:encoded".equalsIgnoreCase(tag) || "encoded".equalsIgnoreCase(tag))) {
                content = readElementText(parser).trim();
            } else if (inside && event == XmlPullParser.START_TAG
                    && ("pubDate".equalsIgnoreCase(tag) || "published".equalsIgnoreCase(tag)
                    || "updated".equalsIgnoreCase(tag) || "date".equalsIgnoreCase(tag))) {
                published = readElementText(parser).trim();
            } else if (inside && event == XmlPullParser.START_TAG) {
                String prefix = parser.getPrefix();
                String namespace = parser.getNamespace();
                String urlAttribute = attribute(parser, "url");
                String typeAttribute = attribute(parser, "type");
                if ("thumbnail".equalsIgnoreCase(tag) && isMediaNamespace(prefix, namespace)) {
                    if (mediaThumbnail.isEmpty()) mediaThumbnail = urlAttribute;
                } else if ("content".equalsIgnoreCase(tag) && isMediaNamespace(prefix, namespace)) {
                    if (mediaContent.isEmpty() && (isImageUrl(urlAttribute, typeAttribute)
                            || "image".equalsIgnoreCase(attribute(parser, "medium")))) mediaContent = urlAttribute;
                } else if ("enclosure".equalsIgnoreCase(tag) && isImageUrl(urlAttribute, typeAttribute)) {
                    if (enclosureImage.isEmpty()) enclosureImage = urlAttribute;
                }
            } else if (event == XmlPullParser.END_TAG && ("item".equalsIgnoreCase(tag) || "entry".equalsIgnoreCase(tag))) {
                inside = false;
                if (!title.isEmpty() && safeLink(link)) {
                    String summary = plainText(content.isEmpty() ? description : content);
                    String html = content.isEmpty() ? description : content;
                    String image = firstNonEmpty(mediaThumbnail, mediaContent, enclosureImage,
                            extractImage(html), extractImage(description));
                    URL imageBase = feedUrl;
                    try { imageBase = new URL(feedUrl, link); } catch (Exception ignored) { }
                    articles.add(new Article(title, link, source, summary, parseDate(published), resolveImageUrl(image, imageBase)));
                }
            }
        }
        input.close();
        return articles;
    }

    private static boolean isMediaNamespace(String prefix, String namespace) {
        return "media".equalsIgnoreCase(prefix) || (namespace != null && namespace.toLowerCase(Locale.ROOT).contains("search.yahoo.com/mrss"));
    }

    private static String attribute(XmlPullParser parser, String wanted) {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String name = parser.getAttributeName(i);
            if (wanted.equalsIgnoreCase(name) || (name != null && name.endsWith(":" + wanted)))
                return parser.getAttributeValue(i);
        }
        return "";
    }

    private static boolean isImageUrl(String url, String mime) {
        if (url == null || url.trim().isEmpty()) return false;
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("image/")) return true;
        String path = url.toLowerCase(Locale.ROOT).split("[?#]", 2)[0];
        return path.matches(".*\\.(png|jpe?g|gif|webp|bmp|avif)$");
    }

    private static String extractImage(String html) {
        if (html == null || html.isEmpty()) return "";
        Matcher matcher = Pattern.compile("(?is)<img\\b[^>]*?\\bsrc\\s*=\\s*(['\\\"])(.*?)\\1").matcher(html);
        return matcher.find() ? Html.fromHtml(matcher.group(2), Html.FROM_HTML_MODE_COMPACT).toString().trim() : "";
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private static String resolveImageUrl(String value, URL base) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            String decoded = Html.fromHtml(value.trim(), Html.FROM_HTML_MODE_COMPACT).toString().trim();
            URL resolved = new URL(base, decoded);
            String protocol = resolved.getProtocol();
            return ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))
                    ? resolved.toExternalForm() : "";
        } catch (Exception ignored) { return ""; }
    }

    private static String readElementText(XmlPullParser parser) throws Exception {
        int depth = parser.getDepth();
        StringBuilder text = new StringBuilder();
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT
                    || event == XmlPullParser.ENTITY_REF) {
                String value = parser.getText();
                if (value != null) text.append(value);
            } else if (event == XmlPullParser.END_TAG && parser.getDepth() == depth) {
                break;
            }
        }
        return text.toString();
    }

    private static String plainText(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String html = value.replaceAll("(?is)<(script|style|noscript)\\b[^>]*>.*?</\\1\\s*>", " ")
                .replaceAll("(?is)<img\\b[^>]*>", " ")
                .replaceAll("(?is)<iframe\\b[^>]*>.*?</iframe\\s*>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li|h[1-6])\\s*>", "\n\n");
        String text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
                .replace('\u00a0', ' ').replace('\uFFFC', ' ').replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n+ *", "\n\n").trim();
        return text.length() > 12000 ? text.substring(0, 12000).trim() + "…" : text;
    }

    private static long parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        String[] patterns = {
                "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            if (pattern.endsWith("'Z'")) format.setTimeZone(TimeZone.getTimeZone("UTC"));
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(value.trim(), position);
            if (date != null && position.getIndex() == value.trim().length()) return date.getTime();
        }
        return 0L;
    }

    private static String sourceName(String host) {
        if (host == null) return "科技资讯";
        if (host.endsWith("sspai.com")) return "少数派";
        if (host.endsWith("ifanr.com")) return "爱范儿";
        if (host.endsWith("ithome.com")) return "IT之家";
        if (host.endsWith("solidot.org")) return "Solidot";
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static boolean safeLink(String link) {
        return link != null && (link.startsWith("https://") || link.startsWith("http://"));
    }
}
