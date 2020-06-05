## JSON-Roller ##

JSON Roller is a tool for flattening complex JSON data structures into tables. 

```
usage: json-roller
 -?,--help          Shows help
 -c,--csv <arg>     Output CSV file
 -i,--input <arg>   Input file .json only
 -m,--md <arg>      Output Markdown file
 -t,--tsv <arg>     Output TSV file
 -u,--url <arg>     URL to read json from
 -v,--verbose       Be Verbose
```

Lets take a look a simple example

**Input: people.json**
```json
[
    {
        "name": "Bob",
        "id": 44432,
        "age": 34
    },
    {
        "name": "Mark",
        "id": 14535,
        "age": 31,
        "extra": "stuff"
    },
    {
        "name": "Erin",
        "id": 43232,
        "age": 27
    }
]
```

So in order to turn this into a nice table we would run:
```
json-roller -i people.json -c people.csv
```

**Output: people.csv**
```csv
extra,name,id,age
,Bob,44432,34
stuff,Mark,14535,31
,Erin,43232,27
```

### What about more complex JSON? ###
This tool really shines when dealing with more complex data structures, lets try another example that isnt as straight forward.

```json
{
    "IT Dept":
    {
        "4432": {
            "name": "Bob",
            "age": 34
        },
        "14535": {
            "name": "Mark",
            "age": 31,
            "extra": "stuff"
        },
        "43232": {
            "name": "Erin",
            "age": 27
        }
    },
    "Sales":
    {
        "387532": {
            "name": "Karen",
            "age": 22
        },
        "142348": {
            "name": "Lenny",
            "age": 25,
            "extra": "stuff"
        },
        "531241": {
            "name": "Joe",
            "age": 30
        }
    }
}
```

Lets run the tool again with verbose logging enabled.
```
json-roller -v -i ~/brian/Desktop/people.json -c people.csv
Root JSONObject detected
Columns Created: layer1key, extra, name, layer0key, age
```

Result:
| layer1key | extra | name  | layer0key | age |
|-----------|-------|-------|-----------|-----|
| 387532    |       | Karen | Sales     | 22  |
| 142348    | stuff | Lenny | Sales     | 25  |
| 531241    |       | Joe   | Sales     | 30  |
| 43232     |       | Erin  | IT Dept   | 27  |
| 4432      |       | Bob   | IT Dept   | 34  |
| 14535     | stuff | Mark  | IT Dept   | 31  |

