package org.openstatic;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.json.*;

public class JSONTools {

    /* Starting point for comparing JSONObjects, Arrays and plain old object 
       Will return true of the objects are equal or the first object contains
       all of the components of the second object
    */
    public static boolean matchesFilter(Object other, Object filter)
    {
        if (filter == other)
        {
            return true;
        } else if (filter instanceof JSONArray) {
            return matchesFilter((JSONArray) filter, other);
        } else if (filter instanceof JSONArray) {
            return matchesFilter((JSONObject) filter, other);
        } else {
            return filter.equals(other);
        }
    }

    public static boolean matchesFilter(Object array, JSONArray filter)
    {
        //System.err.println("Nested Array Filter: " + filter.toString());
        try {
            if (!(array instanceof JSONArray)) {
                return false;
            }
            JSONArray jArray = (JSONArray)array;
            List<Object> jArrayList = listJSONArray(jArray);
            Iterator<Object> iterator = filter.iterator();
            while (iterator.hasNext()) {
                Object valueFilter = iterator.next();
                if (valueFilter instanceof JSONObject) {
                    //System.err.println("We found a JSONObject in our array..");
                    JSONObject valueFilterJSONObject = (JSONObject)valueFilter;
                    if (jArrayList.stream().filter( (i) -> matchesFilter(i, valueFilterJSONObject) ).count() == 0)
                    {
                        return false;
                    }
                } else if (valueFilter instanceof JSONArray) {
                    //System.err.println("We found a JSONArray in our array..");
                    JSONArray valueFilterJSONArray = (JSONArray)valueFilter;
                    if (jArrayList.stream().filter( (i) -> matchesFilter(i, valueFilterJSONArray) ).count() == 0)
                    {
                        return false;
                    }
                } else if (!jArrayList.contains(valueFilter)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable exception) {
            return false;
        }
    }
    public static boolean matchesFilter(Object object, JSONObject filter)
    {
        //System.err.println("Nested Object Filter: " + filter.toString() + " against " + object.toString());
        try {
            if (!(object instanceof JSONObject)) {
                System.err.println("Not a JSONObject");
                return false;
            }
            JSONObject jObject = (JSONObject)object;
            Set<String> filterFieldSet = filter.keySet();
            Set<String> objectFieldSet = jObject.keySet();
            
            if (!objectFieldSet.containsAll(filterFieldSet)) {
                //System.err.println("Keyset differs");
                return false;
            }
            Iterator<String> iterator = filterFieldSet.iterator();
            while (iterator.hasNext()) {
                String name = iterator.next();
                Object valueFilter = filter.get(name);
                Object valueObject = jObject.get(name);
                if (valueFilter instanceof JSONObject) {
                    if (!matchesFilter(valueObject, ((JSONObject)valueFilter))) {
                        return false;
                    }
                } else if (valueFilter instanceof JSONArray) {
                    if (!matchesFilter(valueObject, ((JSONArray)valueFilter))) {
                        return false;
                    }
                } else if (!valueFilter.equals(valueObject)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable exception) {
            return false;
        }
    }

    /* return a list of the objects (unchanged) from a JSONArray */
    public static List<Object> listJSONArray(JSONArray array)
    {
        if (array == null)
            return new ArrayList<Object>();
        ArrayList<Object> al = new ArrayList<Object>(array.length());
        for(int i = 0; i < array.length(); i++)
        {
            al.add(i, array.get(i));
        }
        return al;
    }

    // Merge two JSONObjects together, object b may overwrite object a's keys
    public static JSONObject mergeJSONObjects(JSONObject a, JSONObject b)
    {
        JSONObject ro = new JSONObject();
        if (a != null)
        {
            // Add all of A's fields
            for(Iterator<String> fieldIterator = a.keys(); fieldIterator.hasNext(); )
            {
                String field = fieldIterator.next();
                Object value = a.get(field);
                ro.put(field,value);
            }
        }
        if (b != null)
        {
            // Go through B and merge add its fields.
            for(Iterator<String> fieldIterator = b.keys(); fieldIterator.hasNext(); )
            {
                String field = fieldIterator.next();
                Object value = b.get(field);
                if (ro.opt(field) instanceof JSONObject && value instanceof JSONObject)
                {
                    ro.put(field, mergeJSONObjects((JSONObject) ro.opt(field), (JSONObject) value));
                } else if (ro.opt(field) instanceof JSONArray && value instanceof JSONArray) {
                    ro.put(field, mergeJSONArrays((JSONArray) ro.opt(field), (JSONArray) value));
                } else {
                    ro.put(field,value);
                }
            }
        }
        return ro;
    }

    // Merge two JSONArrays together, array b will be appended after array a
    public static JSONArray mergeJSONArrays(JSONArray a, JSONArray b)
    {
        //System.err.println("Merging " + a.toString() + " with " + b.toString());
        JSONArray ra = new JSONArray();
        if (a != null)
        {
            if (b != null)
            {
                // Add all of A's entries
                for (int m = 0; (m < a.length() || m < b.length()); m++)
                {
                    Object aValue = a.opt(m);
                    Object bValue = b.opt(m);
                    if (aValue instanceof JSONObject && bValue instanceof JSONObject)
                    {
                        ra.put(mergeJSONObjects((JSONObject) aValue, (JSONObject) bValue));
                    } else if (aValue instanceof JSONArray && bValue instanceof JSONArray) {
                        ra.put(mergeJSONArrays((JSONArray) aValue, (JSONArray) bValue));
                    } else if (bValue != null) {
                        if (!bValue.toString().equals("null"))
                            ra.put(bValue);
                        else
                            ra.put(aValue);
                    } else {
                        ra.put(aValue);
                    }
                }
            }
        }
        return ra;
    }

    // Compare two JSONObjects, create a third object showing b's differences from a
    // this will only include new keys that A doesn't contain or changes to existing keys
    public static JSONObject diffJSONObjects(JSONObject a, JSONObject b)
    {
        JSONObject ro = new JSONObject();
        // Add all of A's fields
        if (b != null)
        {
            // Scan all subobjects on b for updates to a
            for(Iterator<String> fieldIterator = a.keys(); fieldIterator.hasNext(); )
            {
                String field = fieldIterator.next();
                Object a_value = a.get(field);
                Object b_value = b.opt(field);
                if (a_value instanceof JSONObject && b_value instanceof JSONObject)
                {
                    JSONObject diffReturn = diffJSONObjects((JSONObject) a_value, (JSONObject) b_value);
                    if (diffReturn.length() > 0)
                    {
                        ro.put(field, diffReturn);
                    }
                } else if (!a_value.equals(b_value)) {
                    ro.put(field, b_value);
                }
            }

            // Add keys missing from A as part of the diff
            for(Iterator<String> fieldIterator = b.keys(); fieldIterator.hasNext(); )
            {
                String field = fieldIterator.next();
                Object b_value = b.get(field);
                if (!a.has(field)) {
                    ro.put(field, b_value);
                }
            }
        }
        return ro;
    }

    // Compare two JSONObjects, create a third object showing a's values but only
    // with the keys contained in b
    public static JSONObject filterJSONObjects(JSONObject a, JSONObject b)
    {
        JSONObject ro = new JSONObject();
        // Add all of B's fields
        if (a != null && b != null)
        {
            // Scan all subobjects on b for updates to a
            for(Iterator<String> fieldIterator = b.keys(); fieldIterator.hasNext(); )
            {
                String field = fieldIterator.next();
                Object a_value = a.opt(field);
                Object b_value = b.opt(field);
                if (a_value instanceof JSONObject && b_value instanceof JSONObject)
                {
                    JSONObject diffReturn = filterJSONObjects((JSONObject) a_value, (JSONObject) b_value);
                    if (diffReturn.length() > 0)
                    {
                        ro.put(field, diffReturn);
                    }
                } else if (!a_value.equals(b_value)) {
                    ro.put(field, a_value);
                }
            }
        }
        return ro;
    }

    // break a JSONObject into a collection of JSONObjects maintaining path for each key
    // {"x":{"y":3, "z": 4}} would create two objects {"x":{"y":3}} and {"x":{"z": 4}}
    public static Collection<JSONObject> dissectJSONObject(JSONObject aJsonObject)
    {
        ArrayList<JSONObject> rList = new ArrayList<JSONObject>();
        // Add all of B's fields
        if (aJsonObject != null)
        {
            // Scan all subobjects on b for updates to a
            for(Iterator<String> fieldIterator = aJsonObject.keys(); fieldIterator.hasNext(); )
            {
                String field = fieldIterator.next();
                Object aJsonObjectValue = aJsonObject.opt(field);
                final ArrayList<String> keys = new ArrayList<String>();
                keys.add(field);
                if (aJsonObjectValue instanceof JSONObject)
                {
                    Collection<JSONObject> disectReturn = dissectJSONObject((JSONObject) aJsonObjectValue);
                    Collection<JSONObject> remapped = disectReturn.stream()
                    .map((dr) ->
                    {
                        return buildJSONPath(keys, dr);
                    }).collect(Collectors.toList());
                    rList.addAll(remapped);
                } else if (aJsonObjectValue instanceof JSONArray) {
                    Collection<JSONArray> disectReturn = dissectJSONArray((JSONArray) aJsonObjectValue);
                    Collection<JSONObject> remapped = disectReturn.stream()
                    .map((dr) ->
                    {
                        return buildJSONPath(keys, dr);
                    }).collect(Collectors.toList());
                    rList.addAll(remapped);
                 } else {
                    JSONObject ro = new JSONObject();
                    ro.put(field, aJsonObjectValue);
                    rList.add(ro);
                }
            }
        }
        return rList;
    }

    // break a JSONArray into a collection of JSONArrays maintaining index order for each entry
    // [1,2,3] would create three objects [1] [null,1] [null,null,1]
    public static Collection<JSONArray> dissectJSONArray(JSONArray aJsonArray)
    {
        ArrayList<JSONArray> rList = new ArrayList<JSONArray>();
        // Add all of B's fields
        if (aJsonArray != null)
        {
            // Scan all subobjects on b for updates to a
            // Scan all subobjects on b for updates to a
            for (int m = 0; m < aJsonArray.length(); m++)
            {
                Object aJsonArrayValue = aJsonArray.get(m);
                final int i = m;
                if (aJsonArrayValue instanceof JSONObject)
                {
                    Collection<JSONObject> disectReturn = dissectJSONObject((JSONObject) aJsonArrayValue);
                    Collection<JSONArray> remapped = disectReturn.stream()
                    .map((dr) ->
                    {
                        return indexedJSONArrayFor(dr, i);
                    }).collect(Collectors.toList());
                    rList.addAll(remapped);
                } else if (aJsonArrayValue instanceof JSONArray) {
                    Collection<JSONArray> disectReturn = dissectJSONArray((JSONArray) aJsonArrayValue);
                    Collection<JSONArray> remapped = disectReturn.stream()
                    .map((dr) ->
                    {
                        return indexedJSONArrayFor(dr, i);
                    }).collect(Collectors.toList());
                    rList.addAll(remapped);
                } else {
                    rList.add(indexedJSONArrayFor(aJsonArrayValue, i));
                }
            }
        }
        return rList;
    }

    // break a JSONObject into a collection of JSONObjects maintaining path for each key
    // {"x":{"y":3, "z": 4}} would create two objects {"x":{"y":3}} and {"x":{"z": 4}}
    public static Properties dissectPropertiesFromJSONObject(JSONObject aJsonObject)
    {
        Properties rProperties = new Properties();
        // Add all of B's fields
        if (aJsonObject != null)
        {
            // Scan all subobjects on b for updates to a
            for(Iterator<String> fieldIterator = aJsonObject.keys(); fieldIterator.hasNext(); )
            {
                String field = fieldIterator.next();
                Object aJsonObjectValue = aJsonObject.opt(field);
                if (aJsonObjectValue instanceof JSONObject)
                {
                    Properties disectReturn = dissectPropertiesFromJSONObject((JSONObject) aJsonObjectValue);
                    disectReturn.forEach((k, v) -> {
                        rProperties.put(field + "." + k,v);
                    });
                } else if (aJsonObjectValue instanceof JSONArray) {
                    Properties disectReturn = dissectPropertiesFromJSONArray((JSONArray) aJsonObjectValue);
                    disectReturn.forEach((k, v) -> {
                        rProperties.put(field + k,v);
                    });
                } else {
                    JSONObject ro = new JSONObject();
                    ro.put(field, aJsonObjectValue);
                    rProperties.put(field, aJsonObjectValue.toString());
                }
            }
        }
        return rProperties;
    }

    // break a JSONObject into a collection of JSONObjects maintaining path for each key
    // {"x":{"y":3, "z": 4}} would create two objects {"x":{"y":3}} and {"x":{"z": 4}}
    public static Properties dissectPropertiesFromJSONArray(JSONArray aJsonArray)
    {
        Properties rProperties = new Properties();
        // Add all of B's fields
        if (aJsonArray != null)
        {
            // Scan all subobjects on b for updates to a
            for (int m = 0; m < aJsonArray.length(); m++)
            {
                Object aJsonObjectValue = aJsonArray.get(m);
                final int i = m;
                if (aJsonObjectValue instanceof JSONObject)
                {
                    Properties disectReturn = dissectPropertiesFromJSONObject((JSONObject) aJsonObjectValue);
                    disectReturn.forEach((k, v) -> {
                        rProperties.put("[" + String.valueOf(i) + "]." + k, v);
                    });
                } else if (aJsonObjectValue instanceof JSONArray) {
                    Properties disectReturn = dissectPropertiesFromJSONArray((JSONArray) aJsonObjectValue);
                    disectReturn.forEach((k, v) -> {
                        rProperties.put("[" + String.valueOf(i) + "]" + k, v);
                    });
                } else {
                    rProperties.put("[" + String.valueOf(i) + "]", aJsonObjectValue.toString());
                }
            }
        }
        return rProperties;
    }

    // Provided a list of keys create a nested JSONObject following the list order
    // ["a","b","c"], 100 would result in ("a":{"b":{"c": 100}})
    public static JSONObject buildJSONPath(List<String> keys, Object finalValue)
    {
        JSONObject ro = new JSONObject();
        String[] keysArray = keys.toArray(new String[keys.size()]);
        int endIndex = (keysArray.length-1);
        for(int i = endIndex; i > -1; i--)
        {
            if (i == endIndex)
            {
                ro.put(keysArray[i], finalValue);
            } else {
                JSONObject levelDown = ro;
                ro = new JSONObject();
                ro.put(keysArray[i], levelDown);
            }
        }
        return ro;
    }

    public static JSONArray indexedJSONArrayFor(Object o, int idx)
    {
        JSONArray ja = new JSONArray();
        for (int i = 0; i < idx; i++)
        {
            ja.put((Object) null);
        }
        ja.put(o);
        return ja;
    }
}
