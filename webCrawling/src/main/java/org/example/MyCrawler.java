package org.example;

import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.parser.HtmlParseData;
import edu.uci.ics.crawler4j.url.WebURL;
import edu.uci.ics.crawler4j.crawler.CrawlController;

import org.apache.log4j.Logger;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.HashSet;

public class MyCrawler extends WebCrawler {
    private static final Logger logger = Logger.getLogger(MyCrawler.class);
    private static CrawlController controller;
    private static FileWriter fetchWriter;
    private static FileWriter visitWriter;
    private static FileWriter urlsWriter;

    private static final Set<String> visitedUrls = new HashSet<>();
    private static final Pattern DISALLOWED_PATHS = Pattern.compile(
            ".*(search|recipes/search|changebrowser|thirdpartyservice|config|dzcfg|test-template|test/|_comments/|topic/.*\\?|deeplinkid|searchsuggest|svgimageproc|hive|get-galleryfragment).*"
    );

    public static void setCrawlController(CrawlController ctrl) {
        controller = ctrl; 
    }

    public static void initializeWriters(String siteName) {
        try {
            fetchWriter = new FileWriter("fetch_" + siteName + ".csv");
            visitWriter = new FileWriter("visit_" + siteName + ".csv");
            urlsWriter = new FileWriter("urls_" + siteName + ".csv");

            fetchWriter.write("URL,Status\n");
            visitWriter.write("URL,Size,Outlinks,ContentType\n");
            urlsWriter.write("URL,OK/N_OK\n");

            logger.info("File writers initialized successfully for site: " + siteName);
        } catch (IOException e) {
            logger.error("Error initializing file writers", e);
        }
    }

    @Override
    public boolean shouldVisit(Page referringPage, WebURL url) {
        String href = url.getURL().toLowerCase();

        if (DISALLOWED_PATHS.matcher(href).matches()) {
            return false;
        }

        if (href.startsWith("http://")) {
            href = href.replace("http://", "https://");
        }

        synchronized (visitedUrls) {
            if (visitedUrls.contains(href)) {
                return false; 
            }
        }

        String newsBaseUrl = "https://www.latimes.com";

        boolean isInDomain = href.startsWith(newsBaseUrl);

        try {
            String isOK = isInDomain ? "OK" : "N_OK";
            urlsWriter.write(href + "," + isOK + "\n");
            urlsWriter.flush();
        } catch (IOException e) {
            logger.error("Error writing to urls CSV file", e);
        }

        if (isInDomain) {
            synchronized (visitedUrls) {
                visitedUrls.add(href); 
            }
        }

        return isInDomain;
    }

    @Override
    protected void handlePageStatusCode(WebURL webUrl, int statusCode, String statusDescription) {
        String url = webUrl.getURL();
        try {
            fetchWriter.write(url + "," + statusCode + "\n");
            fetchWriter.flush();
            logger.info("Fetch attempt: " + url + ", Status: " + statusCode);

            // Handle 301 and 302 redirects
            if (statusCode == 301 || statusCode == 302) {
                String redirectedUrl = followRedirect(url);
                if (redirectedUrl != null) {
                    logger.info("Redirected to: " + redirectedUrl);
                    controller.addSeed(redirectedUrl); // Add the redirected URL to the scheduler
                }
            }

        } catch (IOException e) {
            logger.error("Error writing to fetch CSV file", e);
        }
    }

    private String followRedirect(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);

            int responseCode = connection.getResponseCode();
            logger.info("Response code for " + url + ": " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                String redirectedUrl = connection.getHeaderField("Location");
                logger.info("Redirected URL: " + redirectedUrl);

                WebURL webUrl = new WebURL();
                webUrl.setURL(redirectedUrl);

                if (shouldVisit(null, webUrl)) {
                    controller.addSeed(redirectedUrl); 
                    return redirectedUrl; 
                } else {
                    logger.info("Redirected URL not allowed: " + redirectedUrl);
                }
            }
        } catch (IOException e) {
            logger.error("Error following redirect for URL: " + url, e);
        }
        return null;
    }

    @Override
    public void visit(Page page) {
        String url = page.getWebURL().getURL();
        String contentType = determineContentType(page);

        logger.info("Visiting: " + url + ", Content-Type: " + contentType);

        if (!contentType.matches("text/html|application/pdf|application/msword|image/.*")) {
            logger.info("Skipping URL: " + url + ", Content-Type: " + contentType);
            return;
        }

        try {
            int size = page.getContentData().length;
            int outlinks = getOutgoingLinks(page);

            visitWriter.write(url + "," + size + "," + outlinks + "," + contentType + "\n");
            visitWriter.flush(); 
            logger.info("Processed: " + url + ", Size: " + size + ", Outlinks: " + outlinks + ", Type: " + contentType);

        } catch (IOException e) {
            logger.error("Error writing visit details to CSV file for URL: " + url, e);
        }
    }

    private String determineContentType(Page page) {
        String contentType = page.getContentType();

        if (contentType != null) {
            int index = contentType.indexOf(";");
            if (index != -1) {
                contentType = contentType.substring(0, index);
            }
        } else {
            contentType = "unknown";
        }

        return contentType.toLowerCase();
    }

    private int getOutgoingLinks(Page page) {
        if (page.getParseData() instanceof HtmlParseData) {
            HtmlParseData htmlParseData = (HtmlParseData) page.getParseData();
            Set<WebURL> outgoingUrls = htmlParseData.getOutgoingUrls();
            return outgoingUrls.size();
        }
        return 0;
    }

    public static void closeWriters() {
        try {
            if (fetchWriter != null) fetchWriter.close();
            if (visitWriter != null) visitWriter.close();
            if (urlsWriter != null) urlsWriter.close();
            logger.info("File writers closed successfully.");
        } catch (IOException e) {
            logger.error("Error closing file writers", e);
        }
    }
}
