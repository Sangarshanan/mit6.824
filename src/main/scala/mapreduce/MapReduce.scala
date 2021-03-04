// A simple mapreduce implementation

package mapreduce

import java.io._
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}

object Utils {
  def to_file(file_object: String, filename: String, append: Boolean)= {
      val writer = new FileWriter(filename, append)
      writer.write(file_object)
      writer.close()
  }
}

class MapReduce(M: Int, R: Int) {

  def execute(filename: String): Unit = {
    implicit val formats = Serialization.formats(NoTypeHints)
    val map = scala.io.Source.fromFile("src/main/scala/mapreduce/input")
    .getLines
    .flatMap(_.split("\\W+"))
    .toList
    .groupBy(identity)
    .view
    .mapValues(_.length)
    .toMap
  }
}

object Main extends App {
  val mr = new MapReduce(5, 10)
  mr.execute(filename="src/main/scala/mapreduce/input")
}


// Reduce invocations are distributed by partitioning the intermediate key space into R pieces
// using a partitioning function (e.g. `hash(key) mod R` ) where R is user specified.

    // for((key,value) <- map){
    //   var key_hash = math.abs(key.hashCode() % R)
    //   var filename = s"/tmp/reduce_$key_hash"
    //   var json = s""""$key" : $value,"""
    //   Utils.to_file(json, filename, True)
    // }


// scala.io.Source.fromFile("/tmp/demo.json")

// import org.json4s._
// import org.json4s.native.JsonMethods._
// val json = parse(""" { "a": 10, "b": 20, "a": 20} """).values.asInstanceOf[Map[String, Any]]
