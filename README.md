Linetracker Server
------------------

The server backend for the Linetracker webapp.

### Setup
- Create a file in `/src/main/resources/data` with name "server_info.txt". Put the address of the
  server and the absolute path to the linetracker website directory, each in its own line.
  You can put in `localhost` for local testing. Example:

    localhost  
    /home/john/git_repos/linetracker

- Put the data file "linetracker.json" into `/src/main/resources/data`. If you don't have the file,
  create a new file in the folder with the name "linetracker.json" and type in `[]` into it
  signifying an empty JSON array, meaning no data.

- In the project root, run `sbt` to get to the sbt console, and then
  run `compile` followed by `run` to start the server.

