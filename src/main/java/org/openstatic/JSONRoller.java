package org.openstatic;

import java.util.Vector;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.*;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import org.apache.commons.cli.*;
import org.json.JSONArray;

import org.json.*;

public class JSONRoller
{
    private static boolean verbose = false;
    public static void main(String[] args) throws IOException 
    {
        CommandLine cmd = null;
        File inFile = null;
        File outFile = null;
        String data = "";
        String basename = "json-roller-data.csv";
        try
        {
            Options options = new Options();
            CommandLineParser parser = new DefaultParser();

            options.addOption(new Option("v", "verbose", false, "Be Verbose"));
            options.addOption(new Option("?", "help", false, "Shows help"));
            options.addOption(new Option("i", "input", true, "Input file .json only"));
            options.addOption(new Option("u", "url", true, "URL to read json from"));
            options.addOption(new Option("o", "output", true, "Specify output file"));

            cmd = parser.parse(options, args);

            if (cmd.hasOption("?"))
            {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "json-roller", options );
                System.exit(0);
            }

            if (cmd.hasOption("v"))
                JSONRoller.verbose = true;
            
            if (cmd.hasOption("i"))
            {
                inFile = new File(cmd.getOptionValue("i", "data.json"));
                basename = inFile.getName().split("\\.(?=[^\\.]+$)")[0];
                data = new String(Files.readAllBytes(Paths.get(inFile.toURI())), StandardCharsets.UTF_8);
            } else if (cmd.hasOption("u")) {
                String urlString = cmd.getOptionValue("u", "");
                URL u = new URL(urlString);
                try (InputStream in = u.openStream()) {
                    data = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                System.err.println("You must specify an input file -i [filename] or url -u [url]");
                System.exit(0);
            }
            outFile = new File(cmd.getOptionValue("o", basename + ".csv"));

            JSONArray ja = readJSONData(data);
            List<String[]> csvData = JSONArrayFlatten(ja);
            writeCSV(outFile, csvData);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }
    
    public static void logIt(String text)
    {
        if (JSONRoller.verbose)
        {
            System.err.println(text);
        }
    }

    public static void writeCSV(File csvOutputFile, List<String[]> dataLines) throws IOException 
    {
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            dataLines.stream()
              .map(JSONRoller::convertToCSV)
              .forEach(pw::println);
        }
    }
    
    public static String convertToCSV(String[] data)
    {
        return Stream.of(data)
                .map(JSONRoller::escapeSpecialCharacters)
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

    public static JSONArray readJSONData(String data)
    {
        try
        {
            if (data.startsWith("["))
            {
                logIt("Root JSONArray detected");
                JSONArray ary = new JSONArray(data);
                return ary;
            } else if (data.startsWith("{")) {
                logIt("Root JSONObject detected");
                JSONObject obj = new JSONObject(data);
                return new JSONArray(pivotJSONObject(new JSONObject(), 0, obj));
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return new JSONArray();
    }

    /*
        for converting nested keystructures
        Example:
        {
            "abc": {
                "age": 21,
                "name": "bob"
            },
            "abd": {
                "age": 41,
                "name": "tom"
            },
        }

        Result:
        age, name, layer0key
        21,  bob,  abc
        41,  tom,  abd

        This function will find the deepest layer, and transform all key layers into columnts
    */
    public static List<JSONObject> pivotJSONObject(JSONObject addToAllRecords, int layer, JSONObject jo) throws CloneNotSupportedException
    {
        List<JSONObject> returnList = new ArrayList<JSONObject>();
        for(Iterator<String> fieldIterator = jo.keys(); fieldIterator.hasNext(); )
        {
            String field = fieldIterator.next();
            Object value = jo.get(field);
            if (value instanceof JSONObject)
            {
                JSONObject obj = (JSONObject) value;
                String newField = "layer" + String.valueOf(layer) + "key";
                addToAllRecords.put(newField, field);
                if (isNestedJSONObjects(obj))
                {
                    returnList.addAll(pivotJSONObject(addToAllRecords, layer+1, obj));
                } else {
                    returnList.add(mergeJSONObjects(addToAllRecords,obj));
                }
            } else {

            }
        }
        return returnList;
    }

    // Merge two JSONObjects together, object b may overwrite object a's keys
    public static JSONObject mergeJSONObjects(JSONObject a, JSONObject b)
    {
        JSONObject ro = new JSONObject();
        for(Iterator<String> fieldIterator = a.keys(); fieldIterator.hasNext(); )
        {
            String field = fieldIterator.next();
            Object value = a.get(field);
            ro.put(field,value);
        }
        for(Iterator<String> fieldIterator = b.keys(); fieldIterator.hasNext(); )
        {
            String field = fieldIterator.next();
            Object value = b.get(field);
            ro.put(field,value);
        }
        return ro;
    }

    // Check if this JSONObject is just a collection of other JSONObjects.
    public static boolean isNestedJSONObjects(JSONObject jo)
    {
        for(Iterator<String> fieldIterator = jo.keys(); fieldIterator.hasNext(); )
        {
            String field = fieldIterator.next();
            Object value = jo.get(field);
            if (!(value instanceof JSONObject))
            {
                return false;
            }
        }
        return true;
    }

    public static List<String[]> JSONArrayFlatten(JSONArray jarray) throws Exception
    {
        HashSet<String> columns = new HashSet<String>();
        ArrayList<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        for (int m = 0; m < jarray.length(); m++)
        {
            Object value = jarray.get(m);
            if (value != null)
            {
                if (value instanceof JSONObject)
                {
                    Map<String, String> objectMapped = JSONObjectFlatten(null, (JSONObject) value);
                    columns.addAll(objectMapped.keySet());
                    rows.add(objectMapped);
                } else if (value instanceof JSONArray) {
                    Map<String, String> arrayMapped = JSONArrayFlatten(null, (JSONArray) value);
                    columns.addAll(arrayMapped.keySet());
                    arrayMapped = fillNulls(columns, arrayMapped);
                    rows.add(arrayMapped);
                } else {
                    Map<String, String> stringMapped = objectToStringMap("[" + String.valueOf(m) + "]", value);
                    columns.addAll(stringMapped.keySet());
                    rows.add(stringMapped);
                }
            }
        }
        logIt("Columns Created: " + String.join(", ", columns));
        ArrayList<String[]> dataLines = new ArrayList<String[]>();
        dataLines.add(0,columns.toArray(new String[columns.size()]));
        for(Map<String, String> mapRow : rows)
        {
            Collection<String> values = fillNulls(columns, mapRow).values();
            dataLines.add(values.toArray(new String[values.size()]));
        }
        return dataLines;
    }

    // Check to make sure map contains all the keys, if not fill them with blanks.
    public static Map<String, String> fillNulls(Set<String> keys, Map<String, String> map)
    {
        Map<String, String> returnMap = new HashMap<String,String>();
        for(Iterator<String> i = keys.iterator(); i.hasNext();)
        {
            String key = i.next();
            if (map.containsKey(key))
            {
                returnMap.put(key,map.get(key));
            } else {
                returnMap.put(key,"");
            }
        }
        return returnMap;
    }

    // Recursive function for flattening JSONArrays. 
    public static Map<String, String> JSONArrayFlatten(String fieldName, JSONArray jarray) throws Exception
    {
        HashMap<String, String> returnMap = new HashMap<String, String>();
        for (int m = 0; m < jarray.length(); m++)
        {
            Object value = jarray.get(m);
            if (value != null)
            {
                if (value instanceof JSONObject)
                {
                    JSONObject subJO = (JSONObject) value;
                    if (fieldName != null)
                        returnMap.putAll(JSONObjectFlatten(fieldName + "[" + String.valueOf(m) + "]", subJO));
                    else
                        returnMap.putAll(JSONObjectFlatten("[" + String.valueOf(m) + "]", subJO));
                } else if (value instanceof JSONArray) {
                    JSONArray subJA = (JSONArray) value;
                    if (fieldName != null)
                        returnMap.putAll(JSONArrayFlatten(fieldName + "[" + String.valueOf(m) + "]", subJA));
                    else
                        returnMap.putAll(JSONArrayFlatten("[" + String.valueOf(m) + "]", subJA));
                } else {
                    if (fieldName != null)
                        returnMap.putAll(objectToStringMap(fieldName + "[" + String.valueOf(m) + "]", value));
                    else
                        returnMap.putAll(objectToStringMap("[" + String.valueOf(m) + "]", value));
                }
            }
        }
        return returnMap;

    }

    // Recursive function for flattening JSONObjects. 
    public static Map<String, String> JSONObjectFlatten(String fieldName, JSONObject jo) throws Exception
    {
        HashMap<String, String> returnMap = new HashMap<String, String>();
        for(Iterator<String> fieldIterator = jo.keys(); fieldIterator.hasNext(); )
        {
            String field = fieldIterator.next();
            Object value = jo.get(field);
            if (value != null)
            {
                if (value instanceof JSONObject)
                {
                    JSONObject subJO = (JSONObject) value;
                    if (fieldName != null)
                        returnMap.putAll(JSONObjectFlatten(fieldName + "." + field, subJO));
                    else
                        returnMap.putAll(JSONObjectFlatten(field, subJO));
                } else if (value instanceof JSONArray) {
                    JSONArray subJA = (JSONArray) value;
                    if (fieldName != null)
                        returnMap.putAll(JSONArrayFlatten(fieldName + "." + field, subJA));
                    else
                        returnMap.putAll(JSONArrayFlatten(field, subJA));
                } else {
                    if (fieldName != null)
                        returnMap.putAll(objectToStringMap(fieldName + "." + field, value));
                    else
                        returnMap.putAll(objectToStringMap(field, value));
                }
            }
        }
        return returnMap;
    }

    // Make a map out of a String field, if that string field is a query string, call queryStringToStringMap
    public static Map<String, String> objectToStringMap(String fieldName, Object obj)
    {
        HashMap<String, String> returnMap = new HashMap<String, String>();
        if (obj != null)
        {
            String objString = obj.toString();
            if (fieldName != null)
            {
                if (objString.startsWith("?") && objString.contains("="))
                {
                    returnMap.putAll(queryStringToStringMap(fieldName, objString));
                } else {
                    returnMap.put(fieldName, objString);
                }
            }
        }
        return returnMap;
    }

    // Converts a query string to a Map
    public static Map<String, String> queryStringToStringMap(String fieldName, String queryString)
    {
        HashMap<String, String> returnMap = new HashMap<String, String>();
        try
        {
            if (queryString != null)
            {
                StringTokenizer st = new StringTokenizer(queryString.substring(1), "&");
                while(st.hasMoreTokens())
                {
                    String pair = st.nextToken();
                    int idx = pair.indexOf("=");
                    returnMap.put(fieldName + "." + URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return returnMap;
    }
}
