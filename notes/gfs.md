## Google File System

FAQ: http://nil.csail.mit.edu/6.824/2021/papers/gfs-faq.txt

Lecture: https://www.youtube.com/watch?v=EpIgvowZr00

GFS is scalable distributed file system developed at google for large distributed data-intensive applications. [This paper](http://nil.csail.mit.edu/6.824/2020/papers/gfs.pdf) discusses some really cool aspects of the design

The paper starts by laying out some assumptions which are the core to the design and is said to reflect a marked departure from some earlier file system design assumptions.

1) Failures are normal and not an exception, for a filesystem of distributed scale there will always be problems caused by application bugs, operating system bugs, human errors, and the failures of disks, memory, connectors, networking, and power sup-
plies. Therefore, constant monitoring, error detection, fault tolerance, and automatic recovery must be integral to the system.

2) Files are huge by traditional standards and since it's weird to manage billions of approximately KB-sized, the design assumptions, the IO operations and the block sizes would have to be revisited.

4) Two kinds of reads: large streaming and small random reads. In large streaming often read
through a contiguous region of a file and a small random read occur at a particular offset. Performance-conscious applications often batch and sort their small reads.

5) Large, sequential writes that append data to and once written, files are seldom modified again. Small writes at arbitrary positions in a file are supported but do not have to be efficient.

6) Based on the access patterns in (4,5) Appending is favored to Overwriting. Random writes within a file are practically non-existent. Once written, the files are only read, and often only sequentially.

7) Concurrent append to the same file by multiple clients should be possible. **Atomicity with minimal synchronization overhead is essential.**

8) High sustained bandwidth is more important than low latency. (Processing bulk data >> Response time on read/write)


This file system can perform two operations

- **Snapshot** creates a copy of a file or a directory tree at low cost
- **Record Append** allows atomic appends concurrently. It is useful for implementing multi-way merge results and producerconsumer queues that many clients can simultaneously append to without additional locking


GFS consist of a single **Masters** and multiple **Chunkservers** of a fixed size

Each chunk is identified by an immutable and globally unique 64 bit chunk handle assigned by the master at the time of chunk creation and is replicated on multiple Chunkservers while master maintains all file system metadata i.e the namespace, access control information, the mapping from files to chunks, and the current locations of chunks. It also controls system-wide activities such as chunk lease management, garbage collection of orphaned chunks, and chunk migration between chunkservers. The master periodically communicates with each chunkserver in HeartBeat messages to give it instructions and collect its state. Data accessed is often huge streams so no cache is present and since chunks are stored as local files Linux's buffer cache already keeps frequently accessed data in memory.

> Note: Not using a cache based on access patterns to avoid complexity is pretty neat !

However the metadata is cached and for access a client asks the master which chunkservers it should contact for the data

Size of the Chunkserver is fixed at 64MB which is pretty huge for a typical file system block size. It uses **Lazy space allocation** to avoid internal fragmentation i.e the physical allocation of space is delayed as long as possible and the data is kept in a buffer until it reaches chunksize (64MB)

> https://stackoverflow.com/questions/18109582/what-is-lazy-space-allocation-in-google-file-system

Larger chunk size has its pros and cons

- Reduces clients need to interact with the master because reads and writes on the same chunk require only
one initial request to the master for chunk location information.
- Reduces the network overhead by keeping a persistent TCP connection to the chunkserver over an extended
period of time
- Reduces the size of the metadata stored on the master

One major disadvantage here are the **Hotspots** which can happen when there are too many clients are accessing a small file of maybe just one chunk (64MB) and if multiple client access the same chunk you can overload the chunkserver so we in such cases we have to increase the replication factor

> https://stackoverflow.com/questions/46577706/why-do-small-files-create-hot-spots-in-the-google-file-system

**Metadata** All metadata is kept in the master’s memory. Master stores the namespaces and file-to-chunk mapping in an operational log persistently in disk whereas chunk location information is recreated by talking to chunkservers at master startup and this eliminated the problem of keeping the master and chunkservers in sync as chunkservers join and leave the cluster, change names, fail, restart, and so on.

Issues with memory-only approach is the number of chunks and is limited by how much memory the master has. This is not a big issue cause master maintains less than 64 bytes of metadata for each 64 MB chunk and most chunks are full because most files contain many
chunks, only the last of which may be partially filled. Similarly, the file namespace data typically requires less then
64 bytes per file because it stores file names compactly using prefix compression.


**Operation Log** contains a historical record of critical metadata changes and so we must store it reliably and not make changes visible to clients until metadata changes are made persistent. The master also recovers its file system state by replaying the
operation log and hence it as to be small which is done with Checkpoints which is a **compact BTree** like form.

Incoming requests to mutate files should not be blocked while the master is creating a checkpoint. To tackle this the master switches to a new log file in a separate thread. Creating such a checkpoint takes a minute or so for a cluster with a few million lines. Older checkpoints and log files can be freely deleted, though it is keep a few around to guard against catastrophes.

### Consistency Model

File namespace mutations i,e file creation is atomic. They are handled exclusively by the master and with namespace locking can guarantee atomicity and correctness even then this consistency is weak and is with distributed system there are several client reading and master threads writing to chunksize in parallel.

**Consistent** A file region is consistent if all the clients see the same data no matter which chunkserver they read from

**Defined** It is a region after a file mutation that is consistent and the client will see the mutation writes in it’s entirety. Say one client wants to append data and there is no other client appending to that chunk, it is defined. [A Defined region by implication is consistent]

**Undefined** When many clients mutate a region successfully, it is not possible to see what one particular client has written. It however is consistent.

- Concurrent successful mutations => Consistent but Undefined cause all clients see the same data but it may not reflect what any one mutation has written cause there are concurrent mutations happening.
- Serial success => Defined 
- Concurrent/ Serial Failure => Inconsistent and hence also undefined

### Mutations

- Mutation can be writes or record appends
- Writes happens at an application specified offset 
- Append happens atomically (at least once) at an offset of GFS chooses, the offset is then marked as defined 
- GFS may also insert padding or record duplicates and mark the areas inconsistent (concurrent writes)

**How GFS file regions maintain guarantees and consistency**

- Mutations are applied in the same order to all the replicas.
- Chunk versions are used to detect Stale replicas which do not participate in mutations and are often garbage collected 


**Describe a sequence of events that would result in a client reading stale data from the Google File System**

Since clients cache chunk locations, they may read from a
stale replica before that information is refreshed. This window is limited by the cache entry’s timeout and the next
open of the file, which purges from the cache all chunk information for that file. Moreover, as most of our files are
append-only, a stale replica usually returns a premature
end of chunk rather than outdated data. When a reader
retries and contacts the master, it will immediately get current chunk location

GFS identifies failed
chunkservers by regular handshakes between master and all
chunkservers and detects data corruption by checksumming, it is then brought back from the replicas


**Clients side Checkpointing** allows writers to restart incrementally and keeps readers from
processing successfully written file data that is still incomplete from the applications perspective.

Clients Readers also deal with duplicates and padding using extra information like checksums 

### System Interactions

How the client, master, and chunkservers interact to implement data mutations, atomic record append, and snapshot.

- Mutations happens to a chunkserver and its replicas by **Leasing** a chunkserver as primary and all the other replicas as secondary this information is cached in the client to minimize masters involvement. 
- Client then pushed the data to all the replicas which is stored in an internal LRU cache until its times out. So with this there is a **Decoupling of the data flow from the control flow** 
- Once data is written to replicas and acknowledged the primary assigns consecutive serial numbers to all the mutations it receives and applies them to its own state
- This is then forwarded to secondary replicas which use the same order to apply mutations
- Primary then replies to the client, client maintains a lease using heartbeats to master and if not received a new replica is given the lease. Mutations failures mark the regions as inconsistent and is retried before returning the error to client

To avoid network bottlenecks each machine forwards to its "closest" using IP distances and connections between machines minimize latency by **pipelining the data transfer over TCP connections**

### Atomic Appends

- When appending record to Chunkserver the primary check is to see if appending the record
to the current chunk would cause the chunk to exceed the maximum size (64 MB). If so, it pads the chunk to the maximum size, tells secondaries to do the same, and replies to the client indicating that the operation should be retried on the next chunk.
- If an append to any replica fails the client retries so replicas of the same chunk may contain different data and GFS does not guarantee identical data but that the data is written at least once as an atomic unit. We mark these inconsistent areas as undefined.

### Snapshot

Makes a copy of a file/ directory using **Copy-On-Write** techniques.

- The fundamental idea is that if multiple callers ask for resources which are initially indistinguishable, you can give them pointers to the same resource. This function can be maintained until a caller tries to modify its "copy" of the resource, at which point a true private copy is created to prevent the changes becoming visible to everyone else. All of this happens transparently to the callers. The primary advantage is that if a caller never makes any modifications, no private copy need ever be created.

- On the first write operation to that chunk post the snapshot, the master notices that it has a reference count greater than one. Reference count is used to keep track of the number of shadow copies. On write to C a new chunk is created on a chunkserver that has a replica of C so the data is copied over locally. From this point the lease of chunk is granted by master and the client server requests without knowing that it has just been created from an existing chunk.



### Master Operations

The master executes all namespace operations. In addition, it manages chunk replicas throughout the system: it
makes placement decisions, creates new chunks and hence replicas, and coordinates various system-wide activities to
keep chunks fully replicated, to balance load across all the chunkservers, and to reclaim unused storage.

- Master's long running operations require us to take locks so that concurrent operations can be performed without harm
- Writers Lock: Allows writes and Readers Lock: Prevents deletes, renames and snapshots
- Multiple write requests can happen concurrently by acquiring write lock and serializing file creation
- GFS has no folder structure only key value pairs so a write lock on key a/b/c/hello will acquire read locks on `a`, `a/b`, `a/b/c` and `a/b/c/hello`

### Replica Management

When the master creates a chunk, it chooses where to place the initially empty replicas. It considers several factors.

- We want to place new replicas on chunkservers with below-average disk space utilization **Equalizing disk space utilization**
- We want to limit the number of “recent” creations on each chunkserver cause these recent areas will mostly have traffic and even though writes are cheap they are accessed by many clients simultaneously even while being written can cause **hotspots**
- We want to spread replicas of a chunk across racks.


The master re-replicates a chunk as soon as the number of available replicas falls below a user-specified goals and A chunk which is two short of desired replicas is given precedence over a chunk which is short by one.

### Rebalancing 

Master will Re-balance replicas periodically: it examines the current replica distribution and moves replicas
for better disk space and load balancing.

**Garbage Collection** Any file deletion is logged immediately but the resource isn’t deleted immediately. It is renamed to some hidden folder. The master regularly scans the filesystem and deletes such files if they are older than 3 days. It also deletes metadata of chunks that have no files (it can check its file to chunk mapping and find out chunkservers that are not used) or only contains deleted files. Also during exchange of HeartBeat messages the master can instruct other chunkservers to delete replicas of the type of chunks described above. 

This process offers several advantages over **Eager deletion**

- Simple and Reliable in a distributed setup
- In eager deletion Replica deletion messages may be lost, and the master has to remember to resend them across failures
- Garbage collection can run async in the background when the master is free

The delay in deletion can also be a safety net but sometimes this can also hinders user effort to fine tune usage when storage is tight.

Applications that frequently create and delete files might put the system under stress since garbage collection happens every 3 days or so but writes are too frequent. To overcome all this the clients can specify directories that are to be stored without replication. They can also specify directories where deletion takes place immediately.

**Stale Replica Detection** is done with help from master cause it maintains a chunk version number
to distinguish between up-to-date and stale replicas which can happen if a chunkserver fails and misses mutations. The master removes stale replicas in its regular garbage collection.

### High Availability 

Among hundreds of servers in a GFS cluster, some are
bound to be unavailable at any given time. We keep the
overall system highly available with two simple yet effective
strategies: fast recovery and replication.

- Both the master and the chunkserver are designed to restore their state and start in seconds and servers are routinely shut down by killing the process.
- Chunk is replicated on multiple chunkservers on different racks.
- The master state is replicated for reliability. Its operation
log and checkpoints are replicated on multiple machines and a mutation to the state is considered committed only after
its log record has been flushed to disk locally and on all master replicas.
- “Shadow” masters provide read-only access to the file system even when the primary master is down and can function completely on its own and depends on the primary master only for replica location updates.

Each chunkserver can independently verify the integrity of its own copy by maintaining checksums and like other metadata, checksums
are kept in memory and stored persistently with logging separate from user data.


### Conclusion 

In GFS we treat component failures as the norm rather than the
exception, optimize for huge files that are mostly appended
to (perhaps concurrently) and then read (usually sequentially), and both extend and relax the standard file system
interface to improve the overall system.

Our system provides fault tolerance by constant monitoring, replicating crucial data, and fast and automatic recovery. Chunk replication allows us to tolerate chunkserver failures.


The frequency of these failures motivated a novel
online repair mechanism that regularly and transparently repairs the damage and compensates for lost replicas as soon
as possible. Additionally, we use checksumming to detect
data corruption at the disk or IDE subsystem level, which
becomes all too common given the number of disks in the system.


Our design delivers high aggregate throughput to many
concurrent readers and writers performing a variety of tasks.
We achieve this by separating file system control, which
passes through the master, from data transfer, which passes
directly between chunkservers and clients. Master involvement in common operations is minimized by a large chunk
size and by chunk leases, which delegates authority to pri-
mary replicas in data mutations. This makes possible a sim-
ple, centralized master that does not become a bottleneck.
We believe that improvements in our networking stack will
lift the current limitation on the write throughput seen by
an individual client.

This paper was a lil heavy and Here are some of the notes I found useful

- https://thetechangle.github.io/GFS/
- https://csjourney.com/google-file-system-paper-explained-summary-part-1/
- https://www.synergylabs.org/courses/15-440/lectures/15-gfs.pdf

