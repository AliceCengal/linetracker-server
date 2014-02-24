package edu.vanderbilt.linetracker

import com.google.gson.stream.JsonWriter

/**
 * Created by athran on 2/24/14.
 */
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