package edu.vanderbilt.linetracker

import akka.actor._
import java.io._
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter

/**
 * Interface for the database of waiting time reports. Use the `props`
 * method on the companion object as a factory method, because we want
 * to abstract away which database technology we are using.
 *
 * Send the resulting actor the `SubmitTime` message to add the waiting
 * time into the database.
 *
 * Send to the actor the `GetReportsFor` message to get all the reports
 * in the database with the given lineId. The actor will reply with
 * an Iterator[ReportRecord]
 *
 * Created by athran on 2/24/14.
 */
trait DataServer extends Actor with ActorLogging {

  def walkingDead: Receive = {
    case _ => log.error("I'm dead, please don't bother me.")
  }

}

object DataServer {

  def props: Props = Props[BasicServer]

  private class BasicServer extends DataServer {

    import scala.collection.JavaConverters._
    import LinetrackerServer._

    private var reports         = List.empty[ReportRecord]
    private val parser          = new JsonParser()
    private var currentReportId = 0

    override def preStart(): Unit = {
      loadReports()
    }

    override def receive: Receive = {
      case SubmitTime(lineId, waitTime) => insertReportIntoDb(lineId, waitTime)
      case SaveState                    => storeReports()
      case GetReportsFor(lineId)        => sender ! reports.iterator
      case _                            =>
    }

    private def loadReports() {
      import ReportRecord._

      val f = new File(this.getClass.getResource("/data/linetracker.json").getPath)
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
      write(b, this.getClass.getResource("/data/linetracker.json").getPath)
    }

    private def insertReportIntoDb(lineId: Int, waitTime: Int) {

      val recorded = ReportRecord(currentReportId,
        lineId,
        waitTime,
        (System.currentTimeMillis() / 1000).asInstanceOf[Int])

      reports = recorded :: reports
      currentReportId = currentReportId + 1
      self ! SaveState
    }

  }

  case object SaveState
  case class GetReportsFor(lineId: Int)

}
