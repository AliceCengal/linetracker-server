Linetracker Server
------------------

The server backend for the Linetracker webapp.

### Setup
- Create a file in `/src/main/resources/data` with name "server_info.txt". Put the address of the server
  as the only content of this file, in the first line.
- Put the data file "linetracker.json" in `/src/main/resources/data/`
- In the project root, run `sbt` to get to the sbt console, and then
  run `compile` followed by `run`

