## Google File System

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

