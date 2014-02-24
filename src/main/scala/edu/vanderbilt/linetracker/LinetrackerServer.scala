package edu.vanderbilt.linetracker

import akka.actor.{ActorLogging, Actor}
import java.io._
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter

/**
 * Created by athran on 2/24/14.
 */
class LinetrackerServer extends Actor with ActorLogging {

  import scala.collection.JavaConverters._
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
    case SaveState                    => storeReports()
    case GetAllLines                  => allLines()
    case SubmitTime(lineId, waitTime) => insertReportIntoDb(lineId, waitTime)
    case SummaryFor(lineId)           => summary(lineId)
    case RawFor(lineId)               => raw(lineId)
    case a: Any                       => log.warning("Message not understood: " + a)
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

  private def loadLinesInfo() {

    import LineInfo._

    val f = new File(this.getClass.getResource("/lines_info.json").getPath)
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

  private def insertReportIntoDb(lineId: Int, waitTime: Int) {

    val recorded = ReportRecord(currentReportId,
                                lineId,
                                waitTime,
                                (System.currentTimeMillis() / 1000).asInstanceOf[Int])

    reports = recorded :: reports
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
    writer.name("estimatedTime").value(
      if (shortList.length == 0) 0
      else shortList(0).waitTime
    )

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
  case class SubmitTime(lineId: Int, waitTime: Int)
  case class SummaryFor(lineId: Int)
  case class RawFor(lineId: Int)
}
