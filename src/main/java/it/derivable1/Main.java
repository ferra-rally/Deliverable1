package it.derivable1;

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

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

public class Main {

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

    public static void main(String[] args) throws IOException, JSONException{
        String projName ="FALCON";
        String outFileName = "out.csv";

        int j;
        int i = 0;
        int total;
        Logger logger = Logger.getLogger(Main.class.getName());

        //Get JSON API for closed bugs w/ AV in the project
        List<ZonedDateTime> dates = new ArrayList<>();
        List<String> keys = new ArrayList<>();

        IssueMap map = new IssueMap();

        //Min and Max date to fill missing dates in evalueted period
        Instant minInstant = Instant.ofEpochMilli(Long.MAX_VALUE);
        Instant maxInstant = Instant.ofEpochMilli(Long.MIN_VALUE);
        ZonedDateTime minDate = minInstant.atZone(ZoneOffset.UTC);
        ZonedDateTime maxDate = maxInstant.atZone(ZoneOffset.UTC);

        do {
            //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22" + projName +
                    //"%22AND(%22status%22=%22closed%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    "%22AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    + i + "&maxResults=" + Integer.toString(j);
            JSONObject json = readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                //Iterate through each bug

                String key = issues.getJSONObject(i%1000).get("key").toString();
                String resolutionDate = issues.getJSONObject(i%1000).getJSONObject("fields").get("resolutiondate").toString();

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                ZonedDateTime date = ZonedDateTime.parse( resolutionDate, formatter);
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

                dates.add(date);
                keys.add(key);
            }
        } while (i < total);

        map.addMissingDates(minDate, maxDate);

        logger.log(Level.INFO,"Done generating map");

        //Write to csv file
        map.writeToCsv(outFileName);

        logger.log(Level.INFO,"Done formatting csv");
    }
}
