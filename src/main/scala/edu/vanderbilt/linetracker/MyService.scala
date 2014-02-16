package edu.vanderbilt.linetracker

import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import spray.routing._
import spray.http._
import MediaTypes._
import akka.util.Timeout
import akka.pattern.ask
import java.io._
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class MyServiceActor extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)
}

object MyServiceActor {
  val PROJECT_ROOT = ""; // Set this to the root of this project
  val RESOURCE = PROJECT_ROOT + "src/main/resources/"
}

// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService {

  import MyServiceActor._

  implicit def executionContext = actorRefFactory.dispatcher

  implicit val timeout = Timeout(5 seconds)
  val linetracker = actorRefFactory.actorOf(Props[LinetrackerServer], "linetracker")

  val myRoute =
    pathPrefix("linetracker") {
      path("index.html") {
        getFromFile(RESOURCE + "linetracker_index.txt")
      } ~
      path("alllines") {
        respondWithMediaType(`application/json`) {
          complete {
            import LinetrackerServer._
            (linetracker ? GetAllLines).mapTo[String]
          }
        }
      } ~
      pathPrefix("line" / IntNumber) { id =>
        path("submittime" / IntNumber) { waitTime =>
          linetracker ! SubmitTime(LineReport(id, waitTime))
          complete("OK")
        } ~
        path("summary") {
          complete {
            (linetracker ? SummaryFor(id)).mapTo[String]
          }
        } ~
        path("raw") {
          complete {
            (linetracker ? RawFor(id)).mapTo[String]
          }
        }
      }
    }
}

class LinetrackerServer extends Actor with ActorLogging {

  import scala.collection.JavaConverters._
  import MyServiceActor._
  import LinetrackerServer._

  private val parser = new JsonParser()

  private var reports = List.empty[ReportRecord]
  private var lines = List.empty[LineInfo]
  private var currentReportId = 0

  override def preStart(): Unit = {
    loadReports()
    loadLinesInfo()
  }

  override def receive = {
    case SaveState          => storeReports()
    case GetAllLines        => allLines()
    case SubmitTime(report) => insertReportIntoDb(report)
    case SummaryFor(lineId) => summary(lineId)
    case RawFor(lineId)     => raw(lineId)
    case a: Any             => log.warning("Message not understood: " + a)
  }

  private def loadReports() {

    import ReportRecord._

    val f = new File(RESOURCE + "data/linetracker.json")
    if (f.isFile) {

      val ifs = new InputStreamReader(new FileInputStream(f))
      val arr = (parser parse ifs).getAsJsonArray

      reports = for (
        elem <- arr.iterator.asScala.toList;
        o = elem.getAsJsonObject
      ) yield ReportRecord(
          o.get(REPORT_ID).getAsInt,
          o.get(LINE_ID).getAsInt,
          o.get(WAIT).getAsInt,
          o.get(STAMP).getAsInt
        )

      ifs.close()
      log.info("Successfully loaded this many reports: " + reports.length)
      updateCurrentId()

    } else {
      log.error("Failed to initialize database")
      context.become(walkingDead)
    }
  }

  private def updateCurrentId() {
    currentReportId = reports.
        sortWith( _.reportId > _.reportId ).
        apply(0).
        reportId + 1
  }

  private def storeReports() {

    def initBuffer(): String = {
      val buffer = new StringWriter()
      val writer = new JsonWriter(buffer)

      writer.beginArray()
      for (report <- reports) {
        report.writeToJson(writer)
      }
      writer.endArray()
      buffer.toString
    }

    def write(buffer: String, filename: String) {
      val f = new File(filename)
      if (!f.isFile) f.createNewFile()

      val writer = new OutputStreamWriter(new FileOutputStream(f))
      writer.write(buffer)
      writer.flush()
      writer.close()
    }

    val b = initBuffer()
    write(b, RESOURCE + "data/linetracker" + (System.currentTimeMillis() / 1000) + ".json")
    write(b, RESOURCE + "data/linetracker.json")
  }

  private def loadLinesInfo() {

    import LineInfo._

    val f = new File(RESOURCE + "lines_info.json")
    if (f.isFile) {

      val ifs = new InputStreamReader(new FileInputStream(f))
      val arr = (parser parse ifs).getAsJsonArray

      lines = for (
        elem <- arr.iterator().asScala.toList;
        o = elem.getAsJsonObject
      ) yield LineInfo(
          o.get(ID).getAsInt,
          o.get(LAT).getAsDouble,
          o.get(LON).getAsDouble,
          o.get(TAG).getAsString
        )

      ifs.close()
      log.info("Successfully loaded this many line infos: " + lines.length)

    } else {
      log.error("Failed to initialize lines database")
      context.become(walkingDead)
    }

  }

  private def insertReportIntoDb(report: LineReport) {
    reports = ReportRecord(
      currentReportId,
      report.lineId,
      report.waitTime,
      (System.currentTimeMillis() / 1000).asInstanceOf[Int]) ::
        reports

    currentReportId = currentReportId + 1
    self ! SaveState
  }

  private def allLines() {
    val buffer = new StringWriter()
    val writer = new JsonWriter(buffer)

    writer.beginArray()
    for (l <- lines) {
      l.writeToJson(writer)
    }
    writer.endArray()

    sender ! buffer.toString
  }

  private def summary(lineId: Int) {
    val buffer = new StringWriter()
    val writer = new JsonWriter(buffer)

    writer.beginObject()
    writer.name("lineId").value(lineId)

    val shortList = reports.
                    filter(_.lineId == lineId).
                    take(10).
                    sortWith(_.timeStamp > _.timeStamp) // most recent first

    // TODO: Strategy for calculating estimatedTime
    writer.name("estimatedTime").value(shortList(0).waitTime)

    writer.name("recentTimes").beginArray()
    for (report <- shortList) {
      writer.
          beginObject().
          name("waitTime").value(report.waitTime).
          name("timeStamp").value(report.timeStamp).
          endObject()
    }
    writer.endArray()

    writer.endObject()

    sender ! buffer.toString
  }

  private def raw(lineId: Int) {
    val buffer = new StringWriter()
    val writer = new JsonWriter(buffer)

    writer.beginArray()

    for (
      report <- reports.
                filter(_.lineId == lineId).
                sortWith(_.timeStamp > _.timeStamp)) {
      writer.
          beginObject().
          name("waitTime").value(report.waitTime).
          name("timeStamp").value(report.timeStamp).
          endObject()
    }

    writer.endArray()

    sender ! buffer.toString
  }

  def walkingDead: Receive = {
    case _ => log.error("I'm dead, please don't bother me.")
  }

}

object LinetrackerServer {
  case object SaveState
  case object GetAllLines
  case class SubmitTime(report: LineReport)
  case class SummaryFor(lineId: Int)
  case class RawFor(lineId: Int)
}

case class ReportRecord(reportId: Int, // This object's id
                        lineId: Int, // The Line being reported
                        waitTime: Int, // The line'e waiting time
                        timeStamp: Int // When the report occured, seconds, utc unix
                           ) {

  def writeToJson(writer: JsonWriter) {

    import ReportRecord._

    writer.
        beginObject().
        name(REPORT_ID).value(reportId.toString).
        name(LINE_ID).value(lineId.toString).
        name(WAIT).value(waitTime).
        name(STAMP).value(timeStamp).
        endObject()
  }

}

object ReportRecord {
  val REPORT_ID = "reportId"
  val LINE_ID = "lineId"
  val WAIT = "waitTime"
  val STAMP = "timeStamp"
}

case class LineInfo(lineId: Int,
                    latitude: Double,
                    longitude: Double,
                    tag: String
                       ) {

  def writeToJson(writer: JsonWriter) {

    import LineInfo._

    writer.
        beginObject().
        name(ID).value(lineId.toString).
        name(LAT).value(latitude).
        name(LON).value(longitude).
        name(TAG).value(tag).
        endObject()

  }

}

object LineInfo {
  val ID = "lineId"
  val LAT = "latitude"
  val LON = "longitude"
  val TAG = "lineTag"
}

case class LineReport(lineId: Int,
                      waitTime: Int)

case object Get


