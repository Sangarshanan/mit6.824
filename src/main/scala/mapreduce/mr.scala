// A simple mapreduce implementation

package mapreduce

import java.io._
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}

object Utils {
  def flush_file(file_object: String, filename: String): Unit = {
      val writer=new PrintWriter(new File(filename))
      writer.write(file_object)
      writer.close()
  }
}

class MapReduce(M: Int, R: Int) {

  def mapper(filename: String): Unit = {
    implicit val formats = Serialization.formats(NoTypeHints)
    val map = scala.io.Source.fromFile("src/main/scala/mapreduce/input")
    .getLines
    .flatMap(_.split("\\W+"))
    .toList
    .groupBy(identity)
    .view
    .mapValues(_.length)
    .toMap
    val json_object = write(map)
    Utils.flush_file(json_object, "/tmp/demo.json")
  }
}

object Main extends App {
  val mr = new MapReduce(5, 10)
  mr.mapper(filename="src/main/scala/mapreduce/input")
}


// scala.io.Source.fromFile("demo.json")

// import org.json4s._
// import org.json4s.native.JsonMethods._
// val json = parse(""" { "a": 10, "b": 20, "a": 20} """).values.asInstanceOf[Map[String, Any]]

// json.foreach {case(key, value) =>
//   val filename = s"$key".hashCode());
//   println(s"$value")
// }

// val key = "a"
// val value = 50
// val hashcode = key.hashCode()
// val fw = new FileWriter("test.txt", true)
// fw.write(s"$key $value\n")
// fw.close()
