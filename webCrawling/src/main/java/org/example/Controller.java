package org.example;

import edu.uci.ics.crawler4j.crawler.CrawlConfig;
import edu.uci.ics.crawler4j.crawler.CrawlController;
import edu.uci.ics.crawler4j.fetcher.PageFetcher;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtConfig;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtServer;
import org.apache.log4j.Logger;

public class Controller {
    private static final Logger logger = Logger.getLogger(Controller.class);
    private static final String CRAWL_STORAGE_FOLDER = "data/crawl";
    private static final int NUMBER_OF_CRAWLERS = 7;
    private static final int MAX_PAGES_TO_FETCH = 20000;
    private static final int MAX_DEPTH_OF_CRAWLING = 16;
    private static final int POLITENESS_DELAY = 200;
    private static final int MAX_DOWNLOAD_SIZE = 10 * 1024 * 1024; // 10MB

    public static void main(String[] args) {
        try {
            String seedUrl = "https://www.latimes.com";
            String siteName = "latimes";

            CrawlController controller = createCrawlController(seedUrl);

            logger.info("Crawler configuration complete. Starting crawl for: " + seedUrl);
            MyCrawler.setCrawlController(controller);
            MyCrawler.initializeWriters(siteName);
            controller.start(MyCrawler.class, NUMBER_OF_CRAWLERS);
            MyCrawler.closeWriters();
            ReportGenerator.generateReport(siteName, 7);
            logger.info("Crawling finished.");
        } catch (Exception e) {
            logger.error("An error occurred during crawling", e);
        }
    }

    private static CrawlController createCrawlController(String seedUrl) throws Exception {
        CrawlConfig config = new CrawlConfig();
        config.setCrawlStorageFolder(CRAWL_STORAGE_FOLDER);
        config.setMaxPagesToFetch(MAX_PAGES_TO_FETCH);
        config.setMaxDepthOfCrawling(MAX_DEPTH_OF_CRAWLING);
        config.setPolitenessDelay(POLITENESS_DELAY);
        config.setIncludeBinaryContentInCrawling(true);
        config.setProcessBinaryContentInCrawling(true);
        config.setMaxDownloadSize(MAX_DOWNLOAD_SIZE);
        config.setFollowRedirects(true);
        // config.setUserAgentString("MyBot/1.0");

        PageFetcher pageFetcher = new PageFetcher(config);
        RobotstxtConfig robotstxtConfig = new RobotstxtConfig();
        RobotstxtServer robotstxtServer = new RobotstxtServer(robotstxtConfig, pageFetcher);

        CrawlController controller = new CrawlController(config, pageFetcher, robotstxtServer);
        controller.addSeed(seedUrl);

        return controller;
    }
}