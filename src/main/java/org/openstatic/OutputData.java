package org.openstatic;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.text.StringEscapeUtils;

import org.json.JSONArray;
import org.json.JSONObject;

public class OutputData 
{

    public static void writeJSON(PrintWriter pw, List<String[]> dataLines) throws IOException 
    {
        List<String[]> data = new ArrayList<String[]>(dataLines);
        JSONArray outputArray = new JSONArray();
        String[] columns = data.get(0);
        data.remove(0);
        data.forEach((row) -> {
            JSONObject rowObject = new JSONObject();
            for(int i = 0; i < row.length; i++)
            {
                rowObject.put(columns[i], row[i]);
            }
            outputArray.put(rowObject);
        });
        pw.print(outputArray.toString(2));
    }

    public static void writeHTML(PrintWriter pw, List<String[]> dataLines) throws IOException 
    {
        pw.println("<!-- Generated By JSON-Roller v" + JSONRoller.VERSION + " https://openstatic.org/projects/json-roller/ -->");
        pw.println("""
            <html>
            <head>
                <script type="text/javascript">
                var upTriangle = String.fromCharCode(9650);
                var downTriangle = String.fromCharCode(9660);
                
                function sortTable(table, col, reverse)
                {
                    var headerElements = table.tHead.children[0].children;
                    for(e of headerElements)
                    {
                        e.style.backgroundColor = "#808080";
                        e.innerHTML = e.innerHTML.replaceAll(new RegExp(upTriangle, "g"), '');
                        e.innerHTML = e.innerHTML.replaceAll(new RegExp(downTriangle, "g"), '');
                    }
                    if (reverse == 1)
                    {
                        headerElements[col].style.backgroundColor = "#04AA6D";
                        headerElements[col].innerHTML = headerElements[col].innerHTML + downTriangle;
                    } else {
                        headerElements[col].style.backgroundColor = "#04AA6D";
                        headerElements[col].innerHTML = headerElements[col].innerHTML + upTriangle;
                    }
                    var tb = table.tBodies[0], // use `<tbody>` to ignore `<thead>` and `<tfoot>` rows
                        tr = Array.prototype.slice.call(tb.rows, 0), // put rows into array
                        i;
                    reverse = -((+reverse) || -1);
                    tr = tr.sort(function (a, b) { // sort rows
                        return reverse // `-1 *` if want opposite order
                            * (a.cells[col].textContent.trim() // using `.textContent.trim()` for test
                                .localeCompare(b.cells[col].textContent.trim(), 'en', {numeric: true})
                            );
                    });
                    for(i = 0; i < tr.length; ++i) tb.appendChild(tr[i]); // append each row in order
                }
            
                function makeSortable(table)
                {
                    var th = table.tHead, i;
                    th && (th = th.rows[0]) && (th = th.cells);
                    if (th) i = th.length;
                    else return; // if no `<thead>` then do nothing
                    while (--i >= 0) (function (i) {
                        var dir = 1;
                        th[i].addEventListener('click', function () {sortTable(table, i, (dir = 1 - dir))});
                    }(i));
                }
            
                function makeAllSortable()
                {
                    var t = document.body.getElementsByTagName('table'), i = t.length;
                    while (--i >= 0) makeSortable(t[i]);
                }
            
                </script>
                <style>
                table
                {
                    font-family: Arial, Helvetica, sans-serif;
                    border-collapse: collapse;
                    width: 100%;
                }
            
                td, th
                {
                border: 1px solid #ddd;
                padding: 8px;
                }
            
                tr:nth-child(even){background-color: #f2f2f2;}
            
                tr:hover {background-color: #ddd;}
            
                th {
                    padding-top: 12px;
                    padding-bottom: 12px;
                    text-align: left;
                    cursor: pointer;
                    background-color: #808080;
                    color: white;
                }

                img {
                    max-height: 128px;
                }
                </style>
            </head>
            <body onload="makeAllSortable()">
                """);
        pw.println("<table>");
        pw.println("<thead><tr>");
        String[] columnHeaders = dataLines.get(0);
        pw.println(Stream.of(columnHeaders).map(OutputData::makeColumnHeader).collect(Collectors.joining()));
        pw.println("</tr></thead><tbody>");
        dataLines.stream().skip(1)
            .map(OutputData::convertToHTML)
            .forEach(pw::println);
        pw.println("</tbody></table></body></html>");
    }
    
    public static String convertToHTML(String[] data)
    {
        return "<tr>" + Stream.of(data)
                .map(OutputData::makeCell)
                .collect(Collectors.joining()) + "</tr>";
    }
    

    public static String makeColumnHeader(String data) 
    {
        return "<th>" + StringEscapeUtils.escapeHtml4(data) + "</th>";
    }

    public static String makeCell(String data) 
    {
        String dataLC = data.toLowerCase();
        if (dataLC.startsWith("https://") || dataLC.startsWith("http://"))
        {
            if (dataLC.endsWith(".jpg") || dataLC.endsWith(".png") || dataLC.endsWith(".gif") || dataLC.endsWith(".webp"))
            {
                return "<td><img src=\"" + data + "\" onerror=\"this.style.display = 'none';\"><br /><a target=\"_blank\" href=\"" + data + "\">" +  StringEscapeUtils.escapeHtml4(data) + "</a></td>";
            } else {
               return "<td><a target=\"_blank\" href=\"" + data + "\">" +  StringEscapeUtils.escapeHtml4(data) + "</a></td>";
            }
        } else {
            return "<td>" + StringEscapeUtils.escapeHtml4(data) + "</td>";
        }
    }

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
        if (data.contains(",") || data.contains("\"") || data.contains("'") || data.contains(" ")) {
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