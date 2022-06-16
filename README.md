## JSON-Roller ##

JSON Roller is a tool for flattening complex JSON data structures into tables. I know there are a lot of tools that perform this exact function, but json-roller is fast and native! I often found myself in need of a tool to convert large chucks of json records into something i could share in an excel friendly format, this is the main purpose of json-roller.

Using a test file movies.json (3.22 Megs, 3 layers of data)
```bash
$ time json-roller -i movies.json -c movies.csv

real    0m1.318s
user    0m1.000s
sys     0m0.313s

$ wc movies.csv
  28796  173765 3130845 movies.csv
```
That's 28,796 records in 1.318 seconds!

** How do i use it? **
```bash
usage: json-roller
JSON Roller: A tool for flattening a JSON structure into a table
Project Page - https://openstatic.org/projects/json-roller/
 -?,--help          Shows help
 -c,--csv <arg>     Output CSV file
 -d,--dissect       Dissect JSON data into each nested key value pair
                    (STDOUT)
 -e,--merge         Merge all input objects into a single object (STDOUT)
 -i,--input <arg>   Input file .json only
 -k,--keys <arg>    Comma seperated list of keys for nested structures.
                    Used to replace layer0key,layer1key or provide keys
                    for nesting
 -m,--md <arg>      Output Markdown file
 -p,--properties    Dissect JSON data into properties for each nested key
                    value pair (STDOUT)
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
```bash
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

Lets run the tool again with verbose logging enabled. This time we are going to
output a Markdown file for ease of viewing.
```bash
json-roller -v -i people.json -m people.md
Root JSONObject detected
Columns Created: layer1key, extra, name, layer0key, age
```

Result (note if you are viewing this on my website, the markdown is probably being rendered to a table):
| layer1key | extra | name  | layer0key | age |
|-----------|-------|-------|-----------|-----|
| 387532    |       | Karen | Sales     | 22  |
| 142348    | stuff | Lenny | Sales     | 25  |
| 531241    |       | Joe   | Sales     | 30  |
| 43232     |       | Erin  | IT Dept   | 27  |
| 4432      |       | Bob   | IT Dept   | 34  |
| 14535     | stuff | Mark  | IT Dept   | 31  |


As you can see the data structure was still flattened, with new columns created
for dept and user_id, (layer0key and layer1key). If the names of the keys are known
in advance you can provide the -k option to add key names based on layer.

```bash
json-roller -v -i people.json -m people.md -k dept,user_id
Root JSONObject detected
Columns Created: user_id, extra, name, dept, age
```

Result:
| user_id   | extra | name  | dept      | age |
|-----------|-------|-------|-----------|-----|
| 387532    |       | Karen | Sales     | 22  |
| 142348    | stuff | Lenny | Sales     | 25  |
| 531241    |       | Joe   | Sales     | 30  |
| 43232     |       | Erin  | IT Dept   | 27  |
| 4432      |       | Bob   | IT Dept   | 34  |
| 14535     | stuff | Mark  | IT Dept   | 31  |
