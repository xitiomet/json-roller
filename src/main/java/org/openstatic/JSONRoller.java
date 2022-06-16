package org.openstatic;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.*;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.cli.*;
import org.json.*;

public class JSONRoller
{
    private static boolean verbose = false;
    private static List<String> keyLayers = new ArrayList<String>();
    public static void main(String[] args) throws IOException 
    {
        CommandLine cmd = null;
        File inFile = null;
        String data = "";
        String basename = "json-roller-data";
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();

        options.addOption(new Option("v", "verbose", false, "Be Verbose"));
        options.addOption(new Option("?", "help", false, "Shows help"));

        options.addOption(new Option("i", "input", true, "Input file .json only"));
        options.addOption(new Option("u", "url", true, "URL to read json from"));
        options.addOption(new Option("d", "dissect", false, "Dissect JSON data into each nested key value pair (STDOUT)"));
        options.addOption(new Option("p", "properties", false, "Dissect JSON data into properties for each nested key value pair (STDOUT)"));
        options.addOption(new Option("e", "merge", false, "Merge all input objects into a single object (STDOUT)"));

        options.addOption(new Option("k", "keys", true, "Comma seperated list of keys for nested structures. Used to replace layer0key,layer1key or provide keys for nesting"));
        Option csvOption = new Option("c", "csv", true, "Output CSV file");
        csvOption.setOptionalArg(true);
        options.addOption(csvOption);

        Option tsvOption = new Option("t", "tsv", true, "Output TSV file");
        tsvOption.setOptionalArg(true);
        options.addOption(tsvOption);

        Option mdOption = new Option("m", "md", true, "Output Markdown file");
        mdOption.setOptionalArg(true);
        options.addOption(mdOption);

        try
        {
            
            cmd = parser.parse(options, args);

            if (cmd.hasOption("?"))
            {
                showHelp(options);
            }

            if (cmd.hasOption("k"))
            {
                String[] keys = cmd.getOptionValue("k", "").split(",");
                keyLayers = Arrays.asList( keys );
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

            JSONArray workingData = readJSONData(data);

            // Here are all our pivoted outputs
            if (cmd.hasOption("c") || cmd.hasOption("t") || cmd.hasOption("m"))
            {
                JSONArray pivotedData = workingData;
                if (workingData.length() == 1)
                {
                    logIt("Singular Object Detected: performing table pivot");
                    pivotedData = new JSONArray(pivotJSONObject(new JSONObject(), 0, workingData.getJSONObject(0)));
                }
                List<String[]> csvData = JSONArrayFlatten(pivotedData);

                if (cmd.hasOption("c"))
                {
                    OutputData.writeCSV(new File(cmd.getOptionValue("c", basename + ".csv")), csvData);
                }
                if (cmd.hasOption("t"))
                {
                    OutputData.writeTSV(new File(cmd.getOptionValue("t", basename + ".tsv")), csvData);
                }
                if (cmd.hasOption("m"))
                {
                    OutputData.writeMarkdown(new File(cmd.getOptionValue("m", basename + ".md")), csvData);
                }
            }

            if (cmd.hasOption("d"))
            {
                try
                {
                    Collection<String> disectedJSON = dissectJSONData(workingData);
                    disectedJSON.forEach((line) -> {
                        System.out.println(line);
                    });
                } catch (Exception e2) {
                    if (JSONRoller.verbose)
                        e2.printStackTrace(System.err);
                }
            }
            if (cmd.hasOption("e"))
            {
                try
                {
                    JSONObject mergedJSON = mergeJSONData(workingData);
                    System.out.println(mergedJSON.toString());
                } catch (Exception e2) {
                    if (JSONRoller.verbose)
                        e2.printStackTrace(System.err);
                }
            }
            if (cmd.hasOption("p"))
            {
                try
                {
                    if (workingData.length() == 1)
                    {
                        Properties properties = JSONTools.dissectPropertiesFromJSONObject(workingData.getJSONObject(0));
                        properties.store(System.out, null);
                    } else {
                        Properties properties = JSONTools.dissectPropertiesFromJSONArray(workingData);
                        properties.store(System.out, null);
                    }
                } catch (Exception e2) {
                    if (JSONRoller.verbose)
                        e2.printStackTrace(System.err);
                }
            }
        } catch (Exception e) {
            if (JSONRoller.verbose)
                e.printStackTrace(System.err);
            showHelp(options);
        }
    }

    public static void showHelp(Options options)
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "json-roller", "JSON Roller: A tool for flattening a JSON structure into a table" + System.lineSeparator() + "Project Page - https://openstatic.org/projects/json-roller/", options, "" );
        System.exit(0);
    }
    
    public static String layerKey(int layer)
    {
        String returnKey = "layer" + String.valueOf(layer) + "key";
        try
        {
            returnKey = keyLayers.get(layer);
        } catch (Exception e) {
            //forget it
        }
        return returnKey;
    }

    public static void logIt(String text)
    {
        if (JSONRoller.verbose)
        {
            System.err.println(text);
        }
    }

    public static JSONObject mergeJSONData(JSONArray data)
    {
        JSONObject returnObject = new JSONObject();
        try
        {
            for (int m = 0; m < data.length(); m++)
            {
                Object o = data.get(m);
                if (o instanceof JSONObject)
                {
                    JSONObject aJsonObject = (JSONObject) o;
                    returnObject = JSONTools.mergeJSONObjects(returnObject, aJsonObject);
                }
            }
        } catch (Exception e) {
            if (JSONRoller.verbose) 
                e.printStackTrace(System.err);
        }
        return returnObject;
    }

    public static Collection<String> dissectJSONData(JSONArray data)
    {
        ArrayList<String> returnObjects = new ArrayList<String>();
        try
        {
            data.forEach((o) -> {
                if (o instanceof JSONObject)
                {
                    JSONObject aJsonObject = (JSONObject) o;
                    returnObjects.addAll(JSONTools.dissectJSONObject(aJsonObject).stream().map((x)->x.toString()).collect(Collectors.toList()));
                } else {
                    returnObjects.add((String) o);
                }
            });
        } catch (Exception e) {
            if (JSONRoller.verbose)
                e.printStackTrace(System.err);
        }
        return returnObjects;
    }

    public static JSONArray readJSONData(String data)
    {
        try
        {
            if (data.startsWith("["))
            {
                logIt("Format detected: Root JSONArray");
                JSONArray ary = new JSONArray(data);
                return ary;
            } else if (data.startsWith("{")) {
                if (data.contains("}\n{") || data.contains("}\r\n{"))
                {
                    logIt("Format detected: 1 object per line");
                    StringTokenizer st = new StringTokenizer(data, "\r\n");
                    JSONArray arr = new JSONArray();
                    while(st.hasMoreTokens())
                    {
                        String str = st.nextToken();
                        try
                        {
                            if (!"".equals(str) && str != null)
                            {
                                arr.put(new JSONObject(str));
                            }
                        } catch (Exception strEx) {}
                    }
                    return arr;
                } else {
                    logIt("Format detected: Root JSONObject");
                    JSONObject obj = new JSONObject(data);
                    JSONArray ja = new JSONArray();
                    ja.put(obj);
                    return ja;
                }
            }
        } catch (Exception e) {
            if (JSONRoller.verbose)
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

        This function will find the deepest layer, and transform all key layers into columns
    */
    public static List<JSONObject> pivotJSONObject(JSONObject addToAllRecords, int layer, JSONObject jo) throws CloneNotSupportedException
    {
        List<JSONObject> returnList = new ArrayList<JSONObject>();
        for(Iterator<String> fieldIterator = jo.keys(); fieldIterator.hasNext(); )
        {
            String field = fieldIterator.next();
            Object value = jo.get(field);
            //System.err.println("Layer " + String.valueOf(layer) + " - " + field + " = " + value.toString());
            if (value instanceof JSONObject)
            {
                JSONObject obj = (JSONObject) value;
                String newField = layerKey(layer);
                addToAllRecords.put(newField, field);
                if (isNestedJSONObjects(obj))
                {
                    returnList.addAll(pivotJSONObject(new JSONObject(addToAllRecords.toString()), layer+1, obj));
                } else {
                    returnList.add(JSONTools.mergeJSONObjects(addToAllRecords,obj));
                }
            } else if (value instanceof JSONArray) {
                JSONArray ary = (JSONArray) value;
                JSONObject obj = new JSONObject();
                for(int i = 0; i < ary.length(); i++)
                {
                    obj.put("[" + String.valueOf(i) + "]", ary.get(i));
                }
                String newField = layerKey(layer);
                //System.err.println("pre Want to add to all records " + addToAllRecords.toString());

                addToAllRecords.put(newField, field);
                //System.err.println("post Want to add to all records " + addToAllRecords.toString());
                if (isNestedJSONObjects(obj))
                {
                    returnList.addAll(pivotJSONObject(new JSONObject(addToAllRecords.toString()), layer+1,obj));
                } else {
                    returnList.add(JSONTools.mergeJSONObjects(addToAllRecords,obj));
                }
            } else {
                logIt("Discarded " + field + " on layer " + String.valueOf(layer));
            }
        }
        return returnList;
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
            if (JSONRoller.verbose)
                e.printStackTrace(System.err);
        }
        return returnMap;
    }
}
