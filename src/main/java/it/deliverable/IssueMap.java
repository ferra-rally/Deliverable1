package it.deliverable;

import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

public class IssueMap extends TreeMap<String, Integer> {
    public void addMissingDates(ZonedDateTime minDate, ZonedDateTime maxDate) {
        //Add missing dates
        for(ZonedDateTime testDate = minDate; testDate.compareTo(maxDate) <= 0; testDate = testDate.plusMonths(1)) {
            String dateString = DateTimeFormatter.ofPattern("yyyy-MM").format(testDate);

            this.putIfAbsent(dateString, 0);
        }
    }

    public void writeToCsv(String outFileName) {
        try (FileWriter writer = new FileWriter(outFileName)) {
            writer.write("Date, Fixed tickets\n");

            for(Map.Entry<String, Integer> entry: this.entrySet()) {
                writer.write(entry.getKey() + ", " + entry.getValue() + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
