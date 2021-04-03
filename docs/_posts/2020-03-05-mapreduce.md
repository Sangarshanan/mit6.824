---
title: "Simplified Data Processing on Large Clusters: Mapreduce"
layout: post
---

In this lab you'll build a MapReduce system. You'll implement a worker process that calls application Map and Reduce functions and handles reading and writing files, and a master process that hands out tasks to workers and copes with failed workers. For the [Assignment](http://nil.csail.mit.edu/6.824/2020/labs/lab-mr.html) we will be building something similar to what is described in the [MapReduce paper](http://nil.csail.mit.edu/6.824/2020/papers/mapreduce.pdf).

**Solution in Scala:** https://github.com/Sangarshanan/mit6.824/tree/master/src/main/scala/mapreduce>

### My notes

MapReduce is just a framework that enables dumbos like me to write distributed applications without having to worry about the underlying distributed computing infrastructure which meant one could transparently scale out the cluster by adding nodes and enable automatic failover of both data storage and data processing subsystems with zero impact on the underlying application.

It's a very simple idea really, You essentially just split your data, process them in chunks and then in the end you combine the results obtained from these chunks. In the process, you end up with the parallelization

 of your workload. This happens in two or sometimes three steps

1. **Map:** extract information on each split i.e each worker node applies the `map` function to the local data and writes the output to temporary storage. A master node ensures that only one copy of the redundant input data is processed.
2. **Shuffle:** Bring the partitions to the same reducer i.e worker nodes redistribute data based on the output keys (produced by the `map` function), such that all data belonging to one key is located on the same worker node.
3. **Reduce:** aggregate, summarize, filter or transform i.e worker nodes now process each group of output data, per key, in parallel.

In cases of significant repetition we use a **Combiner**, For example in word counts since word frequencies tend to follow a Zipf distribution, each map task will produce hundreds or thousands of records of the form `<the, 1>`. All of these counts will be sent over the network to a single reduce task and then added together by the Reduce function to produce one number. We allow the user to specify an optional Combiner function that does partial merging of this data before it is sent over the network. The Combiner function is executed on each machine that performs a map task. Typically the same code is used to implement both the combiner and the reduce functions. The only difference between a reduce function and a combiner function is how the MapReduce library handles the output of the function. The output of a reduce function is written to the final output file. The output of a combiner function is written to an intermediate file that will be sent to a reduce task.

**This makes it really easy to write distributed batch workflows, Map → Reduce → Repeat**

This two-stage paradigm which makes MapReduce easy to abstract is also its pitfall, especially since the amount of data written by the Map function can have a large impact on performance and scalability. A lot of MapReduce implementations are designed to write all communication to distributed storage for crash recovery and this makes repeated access to the same data a really IO heavy operation. So it's hard to have streaming applications run over MapReduce and even when we have an iterative batch program that needs to access the same data again and again with low-latency, MapReduce becomes kinda inefficient

The suboptimal nature of Traditional MapReduce applications arise from the fact that they are based on an **acyclic data flow**: an application has to run as a series of distinct jobs, each of which reads data from stable storage (e.g. a distributed file system) and writes it back to stable storage. They incur significant cost loading the data on each step and writing it back to replicated storage.

### Execution Overview 

The Map invocations are distributed across multiple workers/machines by automatically partitioning the input data into a set of M splits. The input splits can be processed in parallel by different machines. Reduce invocations are distributed by partitioning the intermediate key space into R pieces using a partitioning function (e.g. `hash(key) mod R` ). The number of partitions (R) and the partitioning function are specified by the user.

- Split the input file into M pieces with 16 - 64 MB each tuned via a parameter. We then start up many copies of the program on the cluster of machines

- One of the copies of the program is special – **the master**. The rest are workers that are assigned work by the master. There are M map tasks and R reduce tasks to assign. The master picks idle workers and assigns each one a map task or a reduce task.

- A worker who is assigned a map task reads the contents of the corresponding input split. It parses
key/value pairs out of the input data and passes each pair to the user-defined Map function. The intermediate key/value pairs produced by the Map function are buffered in memory.

- Periodically, the buffered pairs are written to local disk, partitioned into R regions by the partitioning function. The locations of these buffered pairs on the local disk are passed back to the master, who is responsible for forwarding these locations to the reduce workers

- When a reduce worker is notified by the master about these locations, it uses remote procedure calls to read the buffered data from the local disks of the map workers. When a reduce worker has read all intermediate data, it sorts it by the intermediate keys so that all occurrences of the same key are grouped together. The sorting is needed because typically many different keys map to the same reduce task. If the amount of intermediate data is too large to fit in memory, an external sort is used (eg: A K-way merge sort)

- The reduce worker iterates over the sorted intermediate data and for each unique intermediate key encountered, it passes the key and the corresponding set of intermediate values to the user’s Reduce function. The output of the Reduce function is appended to a final output file for this reduce partition

- When all map tasks and reduce tasks have been completed, the master wakes up the user program.
At this point, the MapReduce call in the user program returns back to the user code.

After successful completion, the output of the MapReduce execution is available in the R output files (one per reduce task, with file names as specified by the user). Typically, users do not need to combine these R output files into one file – they often pass these files as input to another MapReduce call, or use them from another distributed application that is able to deal with input that is partitioned into multiple files.


### Backup Tasks

One of the common causes that lengthens the total time taken for a MapReduce operation is a **“straggler”**: a machine that takes an unusually long time to complete one of the last few map or reduce tasks in the computation. Stragglers can arise for a whole host of reasons. For example, a machine with a bad disk may experience frequent correctable errors that slow its read performance
from 30 MB/s to 1 MB/s. The cluster scheduling system may have scheduled other tasks on the machine, causing it to execute the MapReduce code more slowly due to competition for CPU, memory, local disk, or network bandwidth. A recent problem we experienced was a bug in machine initialization code that caused processor caches to be disabled: computations on affected machines slowed down by over a factor of one hundred. We have a general mechanism to alleviate the problem of stragglers. When a MapReduce operation is close to completion, the master schedules backup executions of the remaining in-progress tasks. The task is marked
as completed whenever either the primary or the backup execution completes. We have tuned this mechanism so that it typically increases the computational resources used by the operation by no more than a few percent. We have found that this significantly reduces the time to complete large MapReduce operations


### The Goal

Your job is to implement a distributed MapReduce, consisting of two programs, the master and the worker. There will be just one master process, and one or more worker processes executing in parallel. In a real system the workers would run on a bunch of different machines, but for this lab you'll run them all on a single machine. The workers will talk to the master via RPC. Each worker process will ask the master for a task, read the task's input from one or more files, execute the task, and write the task's output to one or more files. The master should notice if a worker hasn't completed its task in a reasonable amount of time (for this lab, use ten seconds), and give the same task to a different worker.
