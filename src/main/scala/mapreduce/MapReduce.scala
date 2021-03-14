// A simple parallel Mapreduce implementation using Scala Futures

package mapreduce

import java.io._
import sys.process._

import scala.language.postfixOps
import scala.collection.mutable.ListBuffer

import org.json4s._
import org.json4s.native.Serialization._
import org.json4s.native.Serialization

import scala.concurrent._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


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

  implicit val formats = Serialization.formats(NoTypeHints)

  // MAPPER + SHUFFLER
  def mapper(filename: String) = {
    // Read Input files and Find word count using `Map`
    // Hash the key and write it to a Shuffler so that same
    // keys are in the same file
    println(s"Started mapper for $filename ...")
    val map = scala.io.Source.fromFile(filename)
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
    println(s"Completed mapper for $filename ...")
  }

  def reducer(filename: String) = {
    // Read map file and group by key
    println(s"Started Reducer for $filename")
    val reduce_chars = scala.io.Source.fromFile(filename)
    .getLines
    .flatMap(_.split(":"))
    .toList
    val reduce_grouped  = reduce_chars
    .grouped(2)
    .collect { case List(a, b) => a -> b }
    .toList
    val reduce_map = reduce_grouped
    .groupBy(_._1).view
    .mapValues(_.map(_._2).map(_.toInt).sum)
    .toMap
    Utils.to_file(write(reduce_map), "src/main/scala/mapreduce/reduce.json", false)
    println(s"Completed Reducer for $filename")
  }

  def execute(filename: String): Unit = {
    // Splits the Input File in M parts.
    s"split -d -n $M -a 1 $filename /tmp/input" !

    val map_tasks = new ListBuffer[scala.concurrent.Future[Unit]]()
    val reduce_tasks = new ListBuffer[scala.concurrent.Future[Unit]]()

    for (f_num <- 0 to M-1) {
      // MAPPER (read from input files)
      val map_future = Future { mapper(s"/tmp/input$f_num") }
      map_tasks += map_future
    }

    val all_map_tasks = Future.sequence(map_tasks)
    // Parallel Mappers
    all_map_tasks.onComplete {
      case Success(x) => println("Completed Map Tasks...")
      case Failure(e) => e.printStackTrace
    }

    // Waits till mappers are Done (maybe don't wait for infinity dumbass)
    Await.ready(all_map_tasks, Duration.Inf)

    // Parallel Reducers ()
    for (m_num <- 0 to R-1) {
      // REDUCER (read from mapper files)
      val reduce_future = Future { reducer(s"/tmp/mapper_$m_num") }
      reduce_tasks += reduce_future
    }
    val all_reduce_tasks = Future.sequence(reduce_tasks)
    all_reduce_tasks.onComplete {
      case Success(x) => println("Completed Reduce Tasks...")
      case Failure(e) => e.printStackTrace
    }
  }
}

object Main extends App {
  val mr = new MapReduce(5, 3)
  mr.execute(filename="src/main/scala/mapreduce/input")
}
