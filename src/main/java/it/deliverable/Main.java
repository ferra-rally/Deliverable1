package it.deliverable;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final String PROJECTURL = "https://github.com/apache/falcon.git";

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public static JSONArray readJsonArrayFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));){
            String jsonText = readAll(rd);

            return new JSONArray(jsonText);
        } finally {
            is.close();
        }
    }

    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException{
        InputStream is = new URL(url).openStream();
        try (
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            )
        {
            String jsonText = readAll(rd);
            return new JSONObject(jsonText);
        } finally {
            is.close();
        }
    }

    public static Map<String, ZonedDateTime> getCommitsDate(Runtime runtime, File localPath) throws IOException {
        HashMap<String, ZonedDateTime> dateMap = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss ZZ");

        Process process = runtime.exec("git log --pretty=format:%ci###%s", null, localPath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while((line = reader.readLine()) != null) {
            String[] tokens = line.split("###");

            String dateString = tokens[0];
            ZonedDateTime dateTime = ZonedDateTime.parse(dateString, formatter);

            String key = tokens[1].split(" ")[0];
            if(key.contains("FALCON")) {
                if(dateMap.containsKey(key)) {
                    ZonedDateTime oldTime = dateMap.get(key);
                    if(oldTime.isBefore(dateTime)) {
                        dateMap.remove(key);
                        dateMap.put(key, dateTime);
                    }
                } else {
                    dateMap.put(key, dateTime);
                }
            }
        }

        return dateMap;
    }

    public static ZonedDateTime getDateTimeGit(String key, File localPath, Runtime runtime) throws IOException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss ZZ");

        Process process = runtime.exec("git log --grep=" + key + "[ ] --pretty=format:%ci", null, localPath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        ZonedDateTime dateTime = null;
        boolean first = true;

        String line;
        while((line = reader.readLine()) != null) {
            ZonedDateTime newDateTime = ZonedDateTime.parse(line, formatter);

            if(first || dateTime.isBefore(newDateTime)) {
                first = false;
                dateTime = newDateTime;
            }
        }

        return dateTime;
    }

    public static ZonedDateTime getDateTime(String key, Map<String, ZonedDateTime> map, JSONArray issues, int index) {
        ZonedDateTime date;

        if(map.containsKey(key)) {
            date = map.get(key);
        } else {
            //If no commits
            String resolutionDate = issues.getJSONObject(index%1000).getJSONObject("fields").get("resolutiondate").toString();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            date = ZonedDateTime.parse( resolutionDate, formatter);
        }

        return date;
    }

    public static void main(String[] args) throws IOException, JSONException, GitAPIException {
        String projName ="FALCON";
        String outFileName = "out.csv";
        Runtime runtime = Runtime.getRuntime();

        final File localPath = new File("./" + projName + "Repo");

        int j;
        int i = 0;
        int total;

        if (!localPath.exists()) {
            LOGGER.log(Level.INFO, "Repository not found, downloading...");
            //Clone repo from GitHub    FALCON-152 REST API for entity & Admin resources only returns XML. Contribtued by Venkatesh Seetharam

            Git.cloneRepository()
                    .setURI(PROJECTURL)
                    .setDirectory(localPath)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("***", "***"))
                    .call();
            LOGGER.log(Level.INFO, "Download complete");
        }

        Logger logger = Logger.getLogger(Main.class.getName());

        IssueMap map = new IssueMap();

        //Min and Max date to fill missing dates in evalueted period
        Instant minInstant = Instant.ofEpochMilli(Long.MAX_VALUE);
        Instant maxInstant = Instant.ofEpochMilli(Long.MIN_VALUE);
        ZonedDateTime minDate = minInstant.atZone(ZoneOffset.UTC);
        ZonedDateTime maxDate = maxInstant.atZone(ZoneOffset.UTC);

        Map<String, ZonedDateTime> dateMap = getCommitsDate(runtime, localPath);

        do {
            //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22" + projName +
                    "%22AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    //"%22%20AND%20(%22status%22=%22closed%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    + i + "&maxResults=" + Integer.toString(j);
            JSONObject json = readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");

            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                //Iterate through each bug
                String key = issues.getJSONObject(i%1000).get("key").toString();
                ZonedDateTime date = getDateTime(key, dateMap, issues, i);

                String dateString = DateTimeFormatter.ofPattern("yyyy-MM").format(date);

                if(date.compareTo(minDate) < 0) {
                    minDate = date;
               }

                if(date.compareTo(maxDate) > 0) {
                    maxDate = date;
                }

                if(map.containsKey(dateString)) {
                    map.put(dateString, map.get(dateString) + 1);
                } else {
                    map.put(dateString, 1);
                }
            }
        } while (i < total);

        //Add missing dates to represent in CSV even the months without fixed tickets
        map.addMissingDates(minDate, maxDate);

        logger.log(Level.INFO,"Done generating map");

        logger.log(Level.INFO,"Map size {0}", map.size());

        //Write to csv file
        map.writeToCsv(outFileName);

        logger.log(Level.INFO,"Done formatting csv");
    }
}
