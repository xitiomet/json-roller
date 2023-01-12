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
import java.io.FileReader;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import org.apache.commons.cli.*;
import org.json.*;

import com.opencsv.CSVReader;

public class JSONRoller
{
    public static final String VERSION = "1.2";
    private static boolean verbose = false;
    private static List<String> keyLayers = new ArrayList<String>();
    private static List<String> columnOrder = new ArrayList<String>();

    public static void main(String[] args) throws IOException 
    {
        CommandLine cmd = null;
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();

        options.addOption(new Option("v", "verbose", false, "Be Verbose"));
        options.addOption(new Option("?", "help", false, "Shows help"));

        Option inputOption = new Option("i", "input", true, "Input file .json or .csv only use commas for multiple files");
        inputOption.setValueSeparator(',');
        inputOption.setOptionalArg(true);
        inputOption.setArgName("filename");
        options.addOption(inputOption);
        options.addOption(new Option("u", "url", true, "URL to read json from"));
        options.addOption(new Option("d", "dissect", false, "Dissect JSON data into each nested key value pair (STDOUT)"));

        Option propOption = new Option("p", "properties", true, "Dissect JSON data into properties for each nested key value pair (exclude filename for STDOUT)");
        propOption.setArgName("filename.ini");
        propOption.setOptionalArg(true);
        options.addOption(propOption);
        
        Option mergeOption = new Option("e", "merge", true, "Merge all input objects into a single object (STDOUT) optional numerical argument to format the data using spaces");
        mergeOption.setOptionalArg(true);
        mergeOption.setArgName("spaces");
        options.addOption(mergeOption);

        Option keyOption = new Option("k", "keys", true, "Comma seperated list of keys for nested structures. Used to replace layer0key,layer1key or provide keys for nesting");
        keyOption.setArgName("key1,key2");
        options.addOption(keyOption);

        Option filterOption = new Option("f", "filter", true, "Comma seperated list of filters (= != >= <= < >) output data will be limited by filters");
        filterOption.setArgName("column=value,column!=value");
        options.addOption(filterOption);

        Option csvOption = new Option("c", "csv", true, "Output Table CSV file (exclude filename for STDOUT)");
        csvOption.setOptionalArg(true);
        csvOption.setArgName("filename.csv");
        options.addOption(csvOption);

        Option htmlOption = new Option("h", "html", true, "Output HTML file (exclude filename for STDOUT)");
        htmlOption.setOptionalArg(true);
        htmlOption.setArgName("filename.html");
        options.addOption(htmlOption);

        Option jsonOption = new Option("j", "json", true, "Output Table as JSON Array file (exclude filename for STDOUT)");
        jsonOption.setOptionalArg(true);
        jsonOption.setArgName("filename.json");
        options.addOption(jsonOption);

        Option tsvOption = new Option("t", "tsv", true, "Output Table TSV file (exclude filename for STDOUT)");
        tsvOption.setOptionalArg(true);
        tsvOption.setArgName("filename.tsv");
        options.addOption(tsvOption);

        Option mdOption = new Option("m", "md", true, "Output Markdown Table file (exclude filename for STDOUT)");
        mdOption.setOptionalArg(true);
        mdOption.setArgName("filename.md");
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
            
            JSONArray workingData = new JSONArray();

            if (cmd.hasOption("i"))
            {
                List<String> filenames = Arrays.asList(cmd.getOptionValue("i").split(","));
                Iterator<String> fnIterator = filenames.iterator();
                while(fnIterator.hasNext())
                {
                    String filename = fnIterator.next().trim();
                    try
                    {
                        logIt("Reading File: " + filename);
                        if (filename.toLowerCase().endsWith(".csv"))
                        {
                            try (CSVReader reader = new CSVReader(new FileReader(filename))) 
                            {
                                List<String[]> r = reader.readAll();
                                String[] columns = r.get(0);
                                for(int i = 0; i < columns.length; i++)
                                    registerColumn(columns[i]);
                                r.remove(0);
                                r.forEach((row) -> {
                                    JSONObject rowObject = new JSONObject();
                                    for(int i = 0; i < row.length; i++)
                                    {
                                        rowObject.put(columns[i], row[i]);
                                    }
                                    workingData.put(rowObject);
                                });
                            }
                        } else {
                            File inFile = new File(filename);
                            String data = (new String(Files.readAllBytes(Paths.get(inFile.toURI())), StandardCharsets.UTF_8));
                            readJSONData(data).forEach((o) -> {
                                workingData.put(o);
                            });
                        }
                    } catch (Exception rfe) {
                        logIt("File Error: " + filename);
                        if (JSONRoller.verbose)
                            rfe.printStackTrace(System.err);
                    }
                }
            }
            
            if (cmd.hasOption("u")) 
            {
                String urlString = cmd.getOptionValue("u", "");
                URL u = new URL(urlString);
                try (InputStream in = u.openStream()) {
                    String data = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    readJSONData(data).forEach((o) -> {
                        workingData.put(o);
                    });
                }
            } 
            if (workingData.length() == 0)
            {
                System.err.println("You must specify an input file -i [filename] or url -u [url] use -? for help!");
                System.exit(0);
            } else {
                logIt("Root Objects: " + workingData.length());

            }

            // Here are all our pivoted outputs
            if (cmd.hasOption("c") || cmd.hasOption("t") || cmd.hasOption("m") || cmd.hasOption("j") || cmd.hasOption("h"))
            {
                JSONArray pivotedData = workingData;
                if (workingData.length() == 1)
                {
                    logIt("Singular Object Detected: performing table pivot");
                    pivotedData = new JSONArray(pivotJSONObject(new JSONObject(), 0, workingData.getJSONObject(0)));
                }
                if (cmd.hasOption("f"))
                {
                    pivotedData = filterData(pivotedData, cmd.getOptionValue("f"));
                }
                List<String[]> csvData = JSONArrayFlatten(pivotedData);

                if (cmd.hasOption("c"))
                {
                    String optionalArg = cmd.getOptionValue("c");
                    if (optionalArg != null)
                    {
                        File csvOutputFile = new File(optionalArg);
                        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
                            OutputData.writeCSV(pw, csvData);
                        }
                    } else {
                        try (PrintWriter pw = new PrintWriter(System.out)) {
                            OutputData.writeCSV(pw, csvData);
                        }
                    }
                }

                if (cmd.hasOption("h"))
                {
                    String optionalArg = cmd.getOptionValue("h");
                    if (optionalArg != null)
                    {
                        File htmlOutputFile = new File(optionalArg);
                        try (PrintWriter pw = new PrintWriter(htmlOutputFile)) {
                            OutputData.writeHTML(pw, csvData);
                        }
                    } else {
                        try (PrintWriter pw = new PrintWriter(System.out)) {
                            OutputData.writeHTML(pw, csvData);
                        }
                    }
                }

                if (cmd.hasOption("j"))
                {
                    String optionalArg = cmd.getOptionValue("j");
                    if (optionalArg != null)
                    {
                        File jsonOutputFile = new File(optionalArg);
                        try (PrintWriter pw = new PrintWriter(jsonOutputFile)) {
                            OutputData.writeJSON(pw, csvData);
                        }
                    } else {
                        try (PrintWriter pw = new PrintWriter(System.out)) {
                            OutputData.writeJSON(pw, csvData);
                        }
                    }
                }

                if (cmd.hasOption("t"))
                {
                    String optionalArg = cmd.getOptionValue("t");
                    if (optionalArg != null)
                    {
                        File tsvOutputFile = new File(optionalArg);
                        try (PrintWriter pw = new PrintWriter(tsvOutputFile)) {
                            OutputData.writeTSV(pw, csvData);
                        }
                    } else {
                        try (PrintWriter pw = new PrintWriter(System.out)) {
                            OutputData.writeTSV(pw, csvData);
                        }
                    }
                }
                if (cmd.hasOption("m"))
                {
                    String optionalArg = cmd.getOptionValue("m");
                    if (optionalArg != null)
                    {
                        File mdOutputFile = new File(optionalArg);
                        try (PrintWriter pw = new PrintWriter(mdOutputFile)) {
                            OutputData.writeMarkdown(pw, csvData);
                        }
                    } else {
                        try (PrintWriter pw = new PrintWriter(System.out)) {
                            OutputData.writeMarkdown(pw, csvData);
                        }
                    }
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
                    String formatOption = cmd.getOptionValue("e");
                    if (formatOption != null)
                    {
                        System.out.println(mergedJSON.toString(Integer.valueOf(formatOption).intValue()));
                    } else {
                        System.out.println(mergedJSON.toString());
                    }
                } catch (Exception e2) {
                    if (JSONRoller.verbose)
                        e2.printStackTrace(System.err);
                }
            }
            if (cmd.hasOption("p"))
            {
                try
                {
                    PrintWriter pw = new PrintWriter(System.out);
                    String optionalArg = cmd.getOptionValue("p");
                    if (optionalArg != null)
                    {
                        File iniOutputFile = new File(optionalArg);
                        pw = new PrintWriter(iniOutputFile);
                    } 
                    if (workingData.length() == 1)
                    {
                        Properties properties = JSONTools.dissectPropertiesFromJSONObject(workingData.getJSONObject(0));
                        properties.store(pw, "Generated by json-roller " + JSONRoller.VERSION + " https://openstatic.org/projects/json-roller/");
                    } else {
                        Properties properties = JSONTools.dissectPropertiesFromJSONArray(workingData);
                        properties.store(pw, "Generated by json-roller " + JSONRoller.VERSION + " https://openstatic.org/projects/json-roller/");
                    }
                    pw.close();
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
        formatter.printHelp( "json-roller", "JSON Roller v" + JSONRoller.VERSION + ": A tool for manipulating JSON and flattening complex structures into a simple table, or converting csv data into JSON" + System.lineSeparator() + "Project Page - https://openstatic.org/projects/json-roller/", options, "" );
        System.exit(0);
    }

    public static JSONArray filterData(JSONArray source, String filtersString)
    {
        String[] filters = filtersString.split(",");
        if (filters.length > 0)
        {
            if (JSONRoller.verbose)
                System.err.println("Filters: " + filtersString);
            JSONArray ra = new JSONArray();
            for (int s = 0; s < source.length(); s++)
            {
                boolean filterPass = true;
                JSONObject row = source.getJSONObject(s);
                try
                {
                    for(int i = 0; i < filters.length; i++)
                    {
                        String filter = filters[i];
                        if (filter.contains("!=")) 
                        {
                            String[] st = splitString(filter, "!=");
                            String key = st[0];
                            String value = st[1];
                            if (row.optString(key, "").equals(value))
                                filterPass = false;
                        } else if (filter.contains(">=")) {
                            String[] st = splitString(filter, ">=");
                            String key = st[0];
                            double value = Double.valueOf(st[1]).doubleValue();
                            double rowValue = Double.valueOf(row.optString(key, "0")).doubleValue();
                            if (rowValue < value)
                                filterPass = false;
                        } else if (filter.contains("<=")) {
                            String[] st = splitString(filter, "<=");
                            String key = st[0];
                            double value = Double.valueOf(st[1]).doubleValue();
                            double rowValue = Double.valueOf(row.optString(key, "0")).doubleValue();
                            if (rowValue > value)
                                filterPass = false;
                        } else if (filter.contains("=")) {
                            StringTokenizer st = new StringTokenizer(filter, "=");
                            String key = st.nextToken();
                            String value = st.nextToken();
                            if (!row.optString(key, "").equals(value))
                                filterPass = false;
                        } else if (filter.contains(">")) {
                            StringTokenizer st = new StringTokenizer(filter, ">");
                            String key = st.nextToken();
                            double value = Double.valueOf(st.nextToken()).doubleValue();
                            double rowValue = Double.valueOf(row.optString(key, "0")).doubleValue();
                            if (rowValue <= value)
                                filterPass = false;
                        } else if (filter.contains("<")) {
                            StringTokenizer st = new StringTokenizer(filter, "<");
                            String key = st.nextToken();
                            double value = Double.valueOf(st.nextToken()).doubleValue();
                            double rowValue = Double.valueOf(row.optString(key, "0")).doubleValue();
                            if (rowValue >= value)
                                filterPass = false;
                        }
                    }
                } catch (Exception e) {
                    if (JSONRoller.verbose)
                        e.printStackTrace(System.err);
                }
                if (filterPass)
                    ra.put(row);
            };
            return ra;
        } else {
            System.err.println("Empty Filter String");
            return source;
        }
    }

    // For splitting a string on multichar delimiters
    public static final String[] splitString(String stringToSplit, String delimiter)
    {
        String[] aRet;
        int iLast;
        int iFrom;
        int iFound;
        int iRecords;
  
        // return Blank Array if stringToSplit == "")
        if (stringToSplit.equals("")) {
            return new String[0];
        }
  
        // count Field Entries
        iFrom = 0;
        iRecords = 0;
        while (true) {
            iFound = stringToSplit.indexOf(delimiter, iFrom);
            if (iFound == -1) {
                break;
            }
            iRecords++;
            iFrom = iFound + delimiter.length();
        }
        iRecords = iRecords + 1;
  
        // populate aRet[]
        aRet = new String[iRecords];
        if (iRecords == 1) {
            aRet[0] = stringToSplit;
        } else {
            iLast = 0;
            iFrom = 0;
            iFound = 0;
            for (int i = 0; i < iRecords; i++) {
                iFound = stringToSplit.indexOf(delimiter, iFrom);
                if (iFound == -1) { // at End
                    aRet[i] = stringToSplit.substring(iLast + delimiter.length(), stringToSplit.length());
                } else if (iFound == 0) { // at Beginning
                    aRet[i] = "";
                } else { // somewhere in middle
                    aRet[i] = stringToSplit.substring(iFrom, iFound);
                }
                iLast = iFound;
                iFrom = iFound + delimiter.length();
            }
        }
        return aRet;
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

    public static void registerColumn(String name)
    {
        if (name != null)
        {
            if (!JSONRoller.columnOrder.contains(name))
            {
                columnOrder.add(name);
            }
        }
    }

    public static void registerColumns(Collection<String> names)
    {
        names.forEach((name) -> registerColumn(name));
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
                registerColumn(newField);
                if (isNestedJSONObjects(obj))
                {
                    returnList.addAll(pivotJSONObject(new JSONObject(addToAllRecords.toString()), layer+1, obj));
                } else {
                    obj.keySet().forEach((k) -> registerColumn(k));
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
                addToAllRecords.put(newField, field);
                registerColumn(newField);
                if (isNestedJSONObjects(obj))
                {
                    returnList.addAll(pivotJSONObject(new JSONObject(addToAllRecords.toString()), layer+1, obj));
                } else {
                    obj.keySet().forEach((k) -> registerColumn(k));
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
                    Set<String> keySet = objectMapped.keySet();
                    registerColumns(keySet);
                    columns.addAll(keySet);
                    rows.add(objectMapped);
                } else if (value instanceof JSONArray) {
                    Map<String, String> arrayMapped = JSONArrayFlatten(null, (JSONArray) value);
                    Set<String> keySet = arrayMapped.keySet();
                    registerColumns(keySet);
                    columns.addAll(keySet);
                    arrayMapped = fillNulls(columns, arrayMapped);
                    rows.add(arrayMapped);
                } else {
                    Map<String, String> stringMapped = objectToStringMap("[" + String.valueOf(m) + "]", value);
                    Set<String> keySet = stringMapped.keySet();
                    registerColumns(keySet);
                    columns.addAll(keySet);
                    rows.add(stringMapped);
                }
            }
        }
        ArrayList<String> orderedKeys = new ArrayList<String>();
        for(int i = 0; i < JSONRoller.columnOrder.size(); i ++)
        {
            String orderedKey = JSONRoller.columnOrder.get(i);
            if (columns.contains(orderedKey))
            {
                orderedKeys.add(orderedKey);
            }
        }
        logIt("Columns Created: " + String.join(", ", orderedKeys));
        ArrayList<String[]> dataLines = new ArrayList<String[]>();
        dataLines.add(0,orderedKeys.toArray(new String[orderedKeys.size()]));
        for(Map<String, String> mapRow : rows)
        {
            dataLines.add(orderMap(orderedKeys, mapRow));
        }
        return dataLines;
    }

    // Check to make sure map contains all the keys, if not fill them with blanks.
    public static String[] orderMap(List<String> keys, Map<String, String> map)
    {
        ArrayList<String> values = new ArrayList<String>();
        for(Iterator<String> i = keys.iterator(); i.hasNext();)
        {
            String key = i.next();
            if (map.containsKey(key))
            {
                values.add(map.get(key));
            } else {
                values.add("");
            }
        }
        return values.toArray(new String[values.size()]);
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
        registerColumn(fieldName);
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
        registerColumn(fieldName);
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
        registerColumn(fieldName);
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
        registerColumn(fieldName);
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
