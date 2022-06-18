## JSON-Roller ##

JSON Roller is a tool for flattening complex JSON data structures into tables. I know there are a lot of tools that perform this exact function, but json-roller is fast and native, it also features some other options for manipulating json on the fly! I often found myself in need of a tool to convert large chunks of json records into something i could share in an excel friendly format, this is the main purpose of json-roller.

In this document i refer to layers a lot, json-roller examines the depth of a document in order to decide what is a column and what is a row. Lets take a look at a small example.
```json
{
    "firstLayer1key": {
        "firstLayer2key": {
            "numbers": [ 1, 2, 3 ]
        }
    },
    "anotherLayer1key": {
        "anotherLayer2key": {
            "numbers": [ 4, 5, 6 ]
        }
    }
}
```

Converting this to a table with the keys "WHAT","STUFF" would result in the following table:

```bash
$ json-roller -i keys.json -k WHAT,STUFF -m
```

| numbers[1] | numbers[2] | numbers[0] | WHAT             | STUFF            |
|------------|------------|------------|------------------|------------------|
| 5          | 6          | 4          | anotherLayer1key | anotherLayer2key |
| 2          | 3          | 1          | firstLayer1key   | firstLayer2key   |


As you can see, "container" layers are treated as fields in the final output. When passing data into json-roller there are 3 formats to consider

 * Singular root object as the entire file (a full scan and pivot will be performed like above)
 * one object per line (each is treated as a row, columns are created as needed)
 * a singular root array with a json object as each entery (each object is treated as a row, columns are created as needed)

Note: when inputing multiple files they are concatinated first and treated as above

### How do i use it? ###
```bash
usage: json-roller
JSON Roller: A tool for manipulating JSON and flattening complex
structures into a simple table
Project Page - https://openstatic.org/projects/json-roller/
 -?,--help                        Shows help
 -c,--csv <filename.csv>          Output Table CSV file (exclude filename
                                  for STDOUT)
 -d,--dissect                     Dissect JSON data into each nested key
                                  value pair (STDOUT)
 -e,--merge <spaces>              Merge all input objects into a single
                                  object (STDOUT) optional numerical
                                  argument to format the data using spaces
 -i,--input <filename>            Input file .json only use commas for
                                  multiple files
 -k,--keys <key1,key2>            Comma seperated list of keys for nested
                                  structures. Used to replace
                                  layer0key,layer1key or provide keys for
                                  nesting
 -m,--md <filename.md>            Output Markdown Table file (exclude
                                  filename for STDOUT)
 -p,--properties <filename.ini>   Dissect JSON data into properties for
                                  each nested key value pair (exclude
                                  filename for STDOUT)
 -t,--tsv <filename.tsv>          Output Table TSV file (exclude filename
                                  for STDOUT)
 -u,--url <arg>                   URL to read json from
 -v,--verbose                     Be Verbose
```

### Root Arrays ###

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

### Large Files? ###

Using a test file movies.json (3.22 Megs, 3 layers of data)
```bash
$ time json-roller -i movies.json -c movies.csv

real    0m1.318s
user    0m1.000s
sys     0m0.313s

$ wc movies.csv
  28796  173765 3130845 movies.csv
```
That's 28,796 records in 1.318 seconds! Told you it was fast.

### Merging and Dissecting ##

Recently i've been working on some new features to really make this tool more powerful, I find myself thinking of ways to represent json in more piece-by-piece aspects. Which is why i added these features

#### Dissection ####

Lets imagine you want to stream a large json object as individual json objects that can be later merged back together

```bash
$ json-roller -i movies.json -d | head -n 20
{"movies":[{"actors":"Alec Baldwin, Geena Davis, Annie McEnroe, Maurice Page"}]}
{"movies":[{"posterUrl":"https://images-na.ssl-images-amazon.com/images/M/MV5BMTUwODE3MDE0MV5BMl5BanBnXkFtZTgwNTk1MjI4MzE@._V1_SX300.jpg"}]}
{"movies":[{"year":"1988"}]}
{"movies":[{"plot":"A couple of recently deceased ghosts contract the services of a \"bio-exorcist\" in order to remove the obnoxious new owners of their house."}]}
{"movies":[{"genres":["Comedy"]}]}
{"movies":[{"genres":[null,"Fantasy"]}]}
{"movies":[{"director":"Tim Burton"}]}
{"movies":[{"runtime":"92"}]}
{"movies":[{"id":1}]}
{"movies":[{"title":"Beetlejuice"}]}
{"movies":[null,{"actors":"Richard Gere, Gregory Hines, Diane Lane, Lonette McKee"}]}
{"movies":[null,{"posterUrl":"https://images-na.ssl-images-amazon.com/images/M/MV5BMTU5ODAyNzA4OV5BMl5BanBnXkFtZTcwNzYwNTIzNA@@._V1_SX300.jpg"}]}
{"movies":[null,{"year":"1984"}]}
{"movies":[null,{"plot":"The Cotton Club was a famous night club in Harlem. The story follows the people that visited the club, those that ran it, and is peppered with the Jazz music that made it so famous."}]}
{"movies":[null,{"genres":["Crime"]}]}
{"movies":[null,{"genres":[null,"Drama"]}]}
{"movies":[null,{"genres":[null,null,"Music"]}]}
{"movies":[null,{"director":"Francis Ford Coppola"}]}
{"movies":[null,{"runtime":"127"}]}
{"movies":[null,{"id":2}]}
```

Each non-json object or array value is broken into its own object following the same structure as the original. Arrays are filled with nulls wherever index ordering needs to be preserved.

Another way to dissect json is as a bunch of properties
```bash
$ json-roller -i movies.json -p | sort | head -n 23
#Fri Jun 17 13:51:01 EDT 2022
#Generated by json-roller http://openstatic.org/projects/json-roller/
movies[0].actors=Alec Baldwin, Geena Davis, Annie McEnroe, Maurice Page
movies[0].director=Tim Burton
movies[0].genres[0]=Comedy
movies[0].genres[1]=Fantasy
movies[0].id=1
movies[0].plot=A couple of recently deceased ghosts contract the services of a "bio-exorcist" in order to remove the obnoxious new owners of their house.
movies[0].posterUrl=https\://images-na.ssl-images-amazon.com/images/M/MV5BMTUwODE3MDE0MV5BMl5BanBnXkFtZTgwNTk1MjI4MzE@._V1_SX300.jpg
movies[0].runtime=92
movies[0].title=Beetlejuice
movies[0].year=1988
movies[100].actors=Ralph Fiennes, Juliette Binoche, Willem Dafoe, Kristin Scott Thomas
movies[100].director=Anthony Minghella
movies[100].genres[0]=Drama
movies[100].genres[1]=Romance
movies[100].genres[2]=War
movies[100].id=101
movies[100].plot=At the close of WWII, a young nurse tends to a badly-burned plane crash victim. His past is shown in flashbacks, revealing an involvement in a fateful love affair.
movies[100].posterUrl=https\://images-na.ssl-images-amazon.com/images/M/MV5BNDg2OTcxNDE0OF5BMl5BanBnXkFtZTgwOTg2MDM0MDE@._V1_SX300.jpg
movies[100].runtime=162
movies[100].title=The English Patient
movies[100].year=1996
```

#### Merging ####

Lets say we took the output from the "-d" dissection above and saved it to a file. We can restore the original using the "-e" merge option this will take all input json objects and merge them into a signular json object. Latter objects will overwrite former objects fields if there is a conflict so ordering is important.

```bash
$ json-roller -i file.json -e 2
{"movies": [
  {
    "actors": "Alec Baldwin, Geena Davis, Annie McEnroe, Maurice Page",
    "posterUrl": "https://images-na.ssl-images-amazon.com/images/M/MV5BMTUwODE3MDE0MV5BMl5BanBnXkFtZTgwNTk1MjI4MzE@._V1_SX300.jpg",
    "year": "1988",
    "plot": "A couple of recently deceased ghosts contract the services of a \"bio-exorcist\" in order to remove the obnoxious new owners of their house.",
    "genres": [
      "Comedy",
      "Fantasy"
    ],
    "director": "Tim Burton",
    "runtime": "92",
    "id": 1,
    "title": "Beetlejuice"
  },
  {
    "actors": "Richard Gere, Gregory Hines, Diane Lane, Lonette McKee",
    "posterUrl": "https://images-na.ssl-images-amazon.com/images/M/MV5BMTU5ODAyNzA4OV5BMl5BanBnXkFtZTcwNzYwNTIzNA@@._V1_SX300.jpg",
    "year": "1984",
    "plot": "The Cotton Club was a famous night club in Harlem. The story follows the people that visited the club, those that ran it, and is peppered with the Jazz music that made it so famous.",
    "genres": [
      "Crime",
      "Drama",
      "Music"
    ],
    "director": "Francis Ford Coppola",
    "runtime": "127",
    "id": 2
  }
]}
```