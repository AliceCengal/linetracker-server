package edu.vanderbilt.linetracker

import com.google.gson.stream.JsonWriter

/**
 * Created by athran on 2/24/14.
 */
case class LineInfo(lineId:    Int,
                    latitude:  Double,
                    longitude: Double,
                    tag:       String
                       ) {

  def writeToJson(writer: JsonWriter) {

    import LineInfo._

    writer.
        beginObject().
        name(ID). value(lineId.toString).
        name(LAT).value(latitude).
        name(LON).value(longitude).
        name(TAG).value(tag).
        endObject()

  }

}

object LineInfo {
  val ID  = "lineId"
  val LAT = "latitude"
  val LON = "longitude"
  val TAG = "lineTag"
}