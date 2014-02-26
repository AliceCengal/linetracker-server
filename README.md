Linetracker Server
------------------

The server backend for the Linetracker webapp.

### Setup
- Create a file in `/src/main/resources/data` with name "server_info.txt". Put the address of the server
  as the only content of this file, in the first line. You can put in "localhost" without the double
  quote for local testing.
- Put the data file "linetracker.json" into `/src/main/resources/data`. If you don't have the file, create
  a new file in the folder with the name "linetracker.json" and type in `[]` into it signifying an empty
  JSON array, meaning no data.
- Copy the website files into `/src/main/resources/website`
- In the project root, run `sbt` to get to the sbt console, and then
  run `compile` followed by `run` to start the server.

