// A simple mapreduce implementation

package mapreduce

import java.io._
import org.json4s._
import sys.process._
import scala.language.postfixOps
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}

/*
Steps

- Splitting the Input file to M parts
- Spin up a process to find word count in each file
- Use the process to Write each work count to another file based on hash(KEY) to group the words
- Wait for the above process to be done
- Once done Spin up R processes that read the R files from shuffler
- Combine the grouped words and find the sum of the word count
- Write this to a R reduce files/ a single reduce file in parallel
*/


object Utils {
  // Write to a file
  def to_file(file_object: String, filename: String, append: Boolean)= {
      val writer = new FileWriter(filename, append)
      writer.write(file_object)
      writer.close()
  }
}

class MapReduce(M: Int, R: Int) {

  def execute(filename: String): Unit = {
    // Splits the Input File in M parts.
    s"split -d -n $M -a 1 $filename /tmp/input" !

    // Read Input files and Find word count using `Map`
    // Hash the key and write it to a Shuffler so that same
    // keys are in the same file
    for (f_num <- 0 to M-1) {
      // MAPPER + SHUFFLER
      implicit val formats = Serialization.formats(NoTypeHints)
      val map = scala.io.Source.fromFile(s"/tmp/input$f_num")
      .getLines
      .flatMap(_.split("\\W+"))
      .toList
      .groupBy(identity)
      .view
      .mapValues(_.length)
      .toMap
      for((key,value) <- map){
        var key_hash = math.abs(key.hashCode() % R)
        var filename = s"/tmp/mapper_$key_hash"
        var json = s""""$key":$value:"""
        Utils.to_file(json, filename, true)
      }
    }

    // Reduce Step read map files and group by key
    for (m_num <- 0 to R-1) {
      // REDUCER
      val reduce_chars = scala.io.Source.fromFile(s"/tmp/mapper_$m_num")
      .getLines
      .flatMap(_.split(":"))
      .toList
      val reduce_grouped  = reduce_chars
      .grouped(2)
      .collect { case List(a, b) => a -> b }
      .toList
      val reduce_counts = reduce_grouped
      .groupBy(_._1).view
      .mapValues(_.map(_._2).map(_.toInt).sum)
      .toList
      .mkString
      Utils.to_file(reduce_counts, "reduce", true)
    }
  }
}

object Main extends App {
  val mr = new MapReduce(5, 3)
  mr.execute(filename="src/main/scala/mapreduce/input")
}
