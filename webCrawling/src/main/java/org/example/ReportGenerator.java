package org.example;

import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;

public class ReportGenerator {
    private static final Logger logger = Logger.getLogger(ReportGenerator.class);

    public static void generateReport(String siteName, int numberOfThreads) {
        try {
            Map<String, Integer> statusCodes = new HashMap<String, Integer>();
            Map<String, Integer> fileSizes = new HashMap<String, Integer>();
            Map<String, Integer> contentTypes = new HashMap<String, Integer>();
            Set<String> uniqueUrls = new HashSet<String>();
            int[] counts = new int[6]; 
            processFetchCsv(siteName, statusCodes, counts);
            processVisitCsv(siteName, fileSizes, contentTypes);
            processUrlsCsv(siteName, uniqueUrls, counts);

            validateStatistics(counts, statusCodes, fileSizes, contentTypes, uniqueUrls);

            writeReport(siteName, numberOfThreads, statusCodes, fileSizes, contentTypes, counts[0], uniqueUrls.size(),
                    counts[1], counts[2], counts[3], counts[4], counts[5]);

            logger.info("Report generated successfully: CrawlReport_" + siteName + ".txt");
        } catch (IOException e) {
            logger.error("Error generating report", e);
        }
    }

    private static void processFetchCsv(String siteName, Map<String, Integer> statusCodes,
                                        int[] counts) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("fetch_" + siteName + ".csv"));
            String line;
            if ((line = reader.readLine()) == null || !line.equals("URL,Status")) {
                throw new IOException("fetch_" + siteName + ".csv is missing header or has incorrect format");
            }
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    counts[3]++; 
                    String statusCode = parts[1];
                    if (!statusCodes.containsKey(statusCode)) {
                        statusCodes.put(statusCode, 1);
                    } else {
                        statusCodes.put(statusCode, statusCodes.get(statusCode) + 1);
                    }
                    if (statusCode.startsWith("2")) {
                        counts[4]++; 
                    } else {
                        counts[5]++;
                    }
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static void processVisitCsv(String siteName, Map<String, Integer> fileSizes,
                                        Map<String, Integer> contentTypes) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("visit_" + siteName + ".csv"));
            String line;
            if ((line = reader.readLine()) == null || !line.equals("URL,Size,Outlinks,ContentType")) {
                throw new IOException("visit_" + siteName + ".csv is missing header or has incorrect format");
            }
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 4) {
                    int size = Integer.parseInt(parts[1]);
                    String contentType = parts[3].split(";")[0].trim(); 

                    String sizeCategory;
                    if (size < 1024) sizeCategory = "< 1KB";
                    else if (size < 10 * 1024) sizeCategory = "1KB ~ <10KB";
                    else if (size < 100 * 1024) sizeCategory = "10KB ~ <100KB";
                    else if (size < 1024 * 1024) sizeCategory = "100KB ~ <1MB";
                    else sizeCategory = ">= 1MB";

                    if (!fileSizes.containsKey(sizeCategory)) {
                        fileSizes.put(sizeCategory, 1);
                    } else {
                        fileSizes.put(sizeCategory, fileSizes.get(sizeCategory) + 1);
                    }

                    if (!contentTypes.containsKey(contentType)) {
                        contentTypes.put(contentType, 1);
                    } else {
                        contentTypes.put(contentType, contentTypes.get(contentType) + 1);
                    }
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static void processUrlsCsv(String siteName, Set<String> uniqueUrls, int[] counts) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("urls_" + siteName + ".csv"));
            String line;
            if ((line = reader.readLine()) == null || !line.equals("URL,OK/N_OK")) {
                throw new IOException("urls_" + siteName + ".csv is missing header or has incorrect format");
            }
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    counts[0]++; 
                    if (uniqueUrls.add(parts[0])) {
                        if ("OK".equals(parts[1])) {
                            counts[1]++; 
                        } else {
                            counts[2]++; 
                        }
                    }
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static void validateStatistics(int[] counts, Map<String, Integer> statusCodes,
                                           Map<String, Integer> fileSizes, Map<String, Integer> contentTypes,
                                           Set<String> uniqueUrls) {
        if (counts[3] != counts[4] + counts[5]) {
            logger.warn("Mismatch in fetch statistics: attempted != succeeded + failed");
        }

        if (Math.abs(counts[3] - 20000) > 2000) {
            logger.warn("Number of fetches attempted is not close to 20,000");
        }

        if (uniqueUrls.size() != counts[1] + counts[2]) {
            logger.warn("Mismatch in unique URL counts");
        }

        Integer successfulFetches = statusCodes.get("200");
        if (successfulFetches == null || successfulFetches.intValue() != counts[4]) {
            logger.warn("Mismatch in successful fetches and 200 status codes");
        }

        int totalSizeStats = 0;
        for (Integer count : fileSizes.values()) {
            totalSizeStats += count;
        }
        if (totalSizeStats > counts[4]) {
            logger.warn("More file size entries than successful fetches");
        }

        int totalContentTypes = 0;
        for (Integer count : contentTypes.values()) {
            totalContentTypes += count;
        }
        if (totalContentTypes > counts[4]) {
            logger.warn("More content type entries than successful fetches");
        }
    }

    private static void writeReport(String siteName, int numberOfThreads, Map<String, Integer> statusCodes,
                                    Map<String, Integer> fileSizes, Map<String, Integer> contentTypes,
                                    int totalUrls, int uniqueUrls, int uniqueUrlsInside, int uniqueUrlsOutside,
                                    int attemptedFetches, int successfulFetches, int failedFetches) throws IOException {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter("CrawlReport_" + siteName + ".txt");
            writer.println("News site crawled: " + siteName);
            writer.println("Number of threads: " + numberOfThreads);
            writer.println();

            writer.println("Fetch Statistics");
            writer.println("================");
            writer.println("# fetches attempted: " + attemptedFetches);
            writer.println("# fetches succeeded: " + successfulFetches);
            writer.println("# fetches failed or aborted: " + failedFetches);
            writer.println();

            writer.println("Outgoing URLs:");
            writer.println("==============");
            writer.println("Total URLs extracted: " + totalUrls);
            writer.println("# unique URLs extracted: " + uniqueUrls);
            writer.println("# unique URLs within News Site: " + uniqueUrlsInside);
            writer.println("# unique URLs outside News Site: " + uniqueUrlsOutside);
            writer.println();

            writer.println("Status Codes:");
            writer.println("=============");
            for (Map.Entry<String, Integer> entry : statusCodes.entrySet()) {
                writer.println(entry.getKey() + ": " + entry.getValue());
            }
            writer.println();

            writer.println("File Sizes:");
            writer.println("===========");
            for (Map.Entry<String, Integer> entry : fileSizes.entrySet()) {
                writer.println(entry.getKey() + ": " + entry.getValue());
            }
            writer.println();

            writer.println("Content Types:");
            writer.println("==============");
            for (Map.Entry<String, Integer> entry : contentTypes.entrySet()) {
                writer.println(entry.getKey() + ": " + entry.getValue());
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}