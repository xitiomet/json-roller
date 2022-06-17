package org.openstatic;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OutputData 
{
    public static void writeCSV(PrintWriter pw, List<String[]> dataLines) throws IOException 
    {
        dataLines.stream()
            .map(OutputData::convertToCSV)
            .forEach(pw::println);
    }
    
    public static String convertToCSV(String[] data)
    {
        return Stream.of(data)
                .map(OutputData::escapeSpecialCharacters)
                .collect(Collectors.joining(","));
    }
    
    public static String escapeSpecialCharacters(String data) 
    {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    public static void writeTSV(PrintWriter pw, List<String[]> dataLines) throws IOException 
    {
        dataLines.stream()
            .map(OutputData::convertToTSV)
            .forEach(pw::println);
    }
    
    public static String convertToTSV(String[] data)
    {
        return Stream.of(data)
                .map(OutputData::escapeSpecialTabCharacters)
                .collect(Collectors.joining("\t"));
    }
    
    public static String escapeSpecialTabCharacters(String data) 
    {
        String escapedData = data.replaceAll("\\t", " ").replaceAll("\\r", " ");
        return escapedData;
    }

    public static void writeMarkdown(PrintWriter pw, List<String[]> dataLines) throws IOException 
    {
        boolean firstLine = true;
        int[] columnSizes = new int[dataLines.get(0).length];
        for(String[] row : dataLines)
        {
            for(int i = 0; i < row.length; i++)
            {
                if (row[i].length() > columnSizes[i])
                    columnSizes[i] = row[i].length();
            }
        }
        for(int i = 0; i < columnSizes.length; i++)
        {
            columnSizes[i]++;
        }
        for(String[] row : dataLines)
        {
            for(int i = 0; i < row.length; i++)
            {
                pw.append("| ");
                pw.append(createSizedString(row[i], columnSizes[i]));
            }
            pw.println("|");
            if (firstLine)
            {
                firstLine = false;
                for(int i = 0; i < columnSizes.length; i++)
                {
                    pw.append("|-");
                    pw.append(getDashed(columnSizes[i]));
                }
                pw.println("|");
            }
        }
        pw.flush();
        pw.close();
    }

    public static String getPaddingSpace(int value)
    {
        StringBuffer x = new StringBuffer("");
        for (int n = 0; n < value; n++)
        {
            x.append(" ");
        }
        return x.toString();
    }

    public static String getDashed(int value)
    {
        StringBuffer x = new StringBuffer("");
        for (int n = 0; n < value; n++)
        {
            x.append("-");
        }
        return x.toString();
    }

    public static String createSizedString(String value, int size)
    {
        if (value == null)
        {
            return getPaddingSpace(size);
        } else if (value.length() == size) {
            return value;
        } else if (value.length() > size) {
            return value.substring(0, size);
        } else if (value.length() < size) {
            return value + getPaddingSpace(size - value.length());
        } else {
            return null;
        }
    }
}