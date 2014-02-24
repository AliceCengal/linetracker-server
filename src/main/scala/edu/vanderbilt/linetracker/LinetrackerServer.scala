package edu.vanderbilt.linetracker

import akka.actor.{ActorLogging, Actor}
import java.io._
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

/**
 * Created by athran on 2/24/14.
 */
class LinetrackerServer extends Actor with ActorLogging {

  import scala.collection.JavaConverters._
  import LinetrackerServer._
  import DataServer._

  private val parser     = new JsonParser()
  private var lines      = List.empty[LineInfo]
  implicit val timeout   = Timeout(5 seconds)
  private val dataServer = context.actorOf(DataServer.props, "DataServer")

  override def preStart(): Unit = {
    loadLinesInfo()
  }

  override def receive = {
    case GetAllLines        => allLines()
    case msg: SubmitTime    => dataServer ! msg
    case SummaryFor(lineId) => summary(lineId)
    case RawFor(lineId)     => raw(lineId)
    case a: Any             => log.warning("Message not understood: " + a)
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

    val requester = sender

    for (
      cursor <- (dataServer ? GetReportsFor(lineId)).mapTo[Iterator[ReportRecord]];
      shortList = cursor.
          toList.
          take(10).
          sortWith(_.waitTime > _.waitTime)
    ) {

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

      requester ! buffer.toString
    }

  }

  private def raw(lineId: Int) {
    val buffer = new StringWriter()
    val writer = new JsonWriter(buffer)
    writer.beginArray()

    val requester = sender

    for (
      cursor <- (dataServer ? GetReportsFor(lineId)).mapTo[Iterator[ReportRecord]];
      report <- cursor.toList.sortWith(_.waitTime > _.waitTime)
    ) {
      writer.
          beginObject().
          name("waitTime").value(report.waitTime).
          name("timeStamp").value(report.timeStamp).
          endObject()
    }

    writer.endArray()

    requester ! buffer.toString
  }

  def walkingDead: Receive = {
    case _ => log.error("I'm dead, please don't bother me.")
  }

}

object LinetrackerServer {
  case object GetAllLines
  case class SubmitTime(lineId: Int, waitTime: Int)
  case class SummaryFor(lineId: Int)
  case class RawFor(lineId: Int)
}
