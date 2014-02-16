Linetracker Server
------------------

The server backend for the Linetracker webapp.

### Setup
- Set the IP address of the server in "Boot.scala"
- Set the path to the project root in the `MyServiceActor` object
  in "MyService.scala"
- Put the data file "linetracker.json" in `resources/data/`
- In the project root, run `sbt` to get to the sbt console, and then
  run `compile` followed by `run`

