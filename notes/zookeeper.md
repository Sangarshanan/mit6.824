# Zookeeper

- **Paper** http://nil.csail.mit.edu/6.824/2020/papers/zookeeper.pdf
- **Notes** https://timilearning.com/posts/mit-6.824/lecture-8-zookeeper/

ZooKeeper is a service for coordinating processes of distributed applications, it is built for maintaining configuration information, naming, providing distributed synchronization, and providing group services.

Compared with [Chubby](https://static.googleusercontent.com/media/research.google.com/en//archive/chubby-osdi06.pdf) in a lot of places, chubby is a lock service for loosely-coupled distributed systems built by Google

**Group membership, Configuration and leader elections** are common forms of coordination in distributed systems, we also want our processes to know which other processes are alive and what those processes are in charge of.

Locks constitute a powerful coordination primitive that implement mutually exclusive access to critical resources but Zookeeper moved away from locks cause its **blocking** and hence can cause slow or faulty clients to negatively impact the performance of faster clients. 

Zookeeper has the following properties

- Wait Free
- FIFO Client Ordering
- Linearizable writes

Guaranteeing FIFO client order enables clients to submit operations asynchronously and when this happens we also have to make sure they are Linearizable which has a performance cost

![stuff](https://dl.acm.org/cms/attachment/286aad7d-9771-4848-9f01-040caeca59c7/ins03.gif)


For this we implement a **leader-based atomic broadcast protocol called ZAB (Zookeeper Atomic Broadcast)** which is a consensus protocol similar to Raft or Paxos. [More Info](https://distributedalgorithm.wordpress.com/2015/06/20/architecture-of-zab-zookeeper-atomic-broadcast-protocol/)

Zookeeper is read heavy so client side caching is important so it allows clients to cache and watch for updates to the cached object and receive a notification. Chubby uses leases to avoid blocking from faulty clients

By implementing these watches zookeeper allow clients to
receive timely notifications of changes without requiring polling.

- **Coordination Kernel** proposes a wait-free coordination service with relaxed consistency guarantees for use in distributed systems.
- Use **Coordination recipes** to build higher level coordination primitives, even blocking and strongly consistent primitives that are often used in distributed applications

### Ayo Jargon Check

We use **client to denote a user of the ZooKeeper service**, server to denote a process providing the ZooKeeper service, and **znode to denote an in-memory data node** in the ZooKeeper data, which is organized in a hierarchical namespace referred to as the data tree. We also use the terms update and write to refer to any operation that modifies the state of the data tree. Clients establish a session when they connect to ZooKeeper and obtain a session handle through which they issue requests.

The znodes in this hierarchy are data objects that clients manipulate through the ZooKeeper API. Hierarchical name spaces are commonly used in filesystems. It is a desirable way of organizing data objects since users are used to this abstraction and it enables better organization of application metadata.

we use the standard UNIX notation for file system paths. For example, we use /A/B/C to denote the path to znode C, where C has B as its parent and B has A as its parent.

![zookeeper-hierarchy](https://timilearning.com/uploads/zookeeper-hierarchy.png)

znodes map to abstractions of the client application, typically corresponding to meta-data used for coordination purposes.

we have two subtrees, one for Application 1 (/app1) and another for Application 2 (/app2). The subtree for Application 1 implements a simple **group membership protocol:** each client process p i creates a znode p i under /app1, which persists as long as the process is running.

Note that although znodes are not designed for general data storage, they allow clients to store specific metadata or configuration. For example, it is useful for a new server in a **leader-based system to learn about which other server is the current leader.** To achieve this, the current leader can be configured to write this information in a known znode space. Any new servers can then read from that znode space.

There is also some metadata associated with znodes by default like timestamps and version counters, with which clients can execute conditional updates based on the version of the znode

A client can create two types of znodes:

- **Regular**: Regular znodes are created and deleted explicitly.
- **Ephemeral**: Ephemeral znodes can either be deleted explicitly or are automatically removed by the system when the session that created them is terminated.

All znodes store data and all znodes except for ephemeral znodes can have children and Additionally, when creating a new znode, a client can **set a sequential flag.** Nodes created with the sequential flag set have the value of a **monotonically increasing counter appended to its name.** If n is the new znode and p is the parent znode, then the sequence value of n is never smaller than the value in the name of any other sequential znode ever created under p.

When a client issues a read operation with a watch flag set, the operation completes as normal except that the server promises to notify the client when the information returned has changed. Watches are one-time triggers associated with a session; they are unregistered once triggered or the session closes. Watches indicate that a change has happened, but do not provide the change which means Session events, such as connection loss events, are also sent to watch callbacks so that clients know that watch events
may be delayed.

**Sessions**. A client connects to ZooKeeper and initiates a session. Sessions have an associated timeout. ZooKeeper considers a client faulty if it does not receive anything from its session for more than that timeout.

### Client API

The ZooKeeper client API exposes a number of methods.

- **create(path, data, flags):** Creates a znode with pathname path, stores data[] in it, and returns the name of the new znode. flags enables a client to select the type of znode;regular, ephemeral, and set the sequential flag;

- **delete(path, version):** Deletes the znode path if that znode is at the expected version;

- **exists(path, watch):** Returns true if the znode with path name path exists, and returns false otherwise. The watch flag enables a client to set a watch on the znode;

- **getData(path, watch):** Returns the data and meta-data, such as version information, associated with the znode. The watch flag works in the same way as it does for exists(), except that ZooKeeper does not set the watch if the znode does not exist;

- **setData(path, data, version):** Writes data[] to znode path if the version number is the current version of the znode;

- **getChildren(path, watch):** Returns the set of names of the children of a znode;

- **sync(path):** Waits for all updates pending at the start of the operation to propagate to the server that the client is connected to.

All methods have both a synchronous and an asynchronous version available through the API. An application uses the synchronous API when it needs to execute a single ZooKeeper operation and it has no concurrent tasks to execute, so it makes the necessary ZooKeeper call and blocks. The asynchronous API, however, enables an application to have both multiple outstanding ZooKeeper operations and other tasks executed in parallel. The ZooKeeper client guarantees that the corresponding callbacks for each operation are invoked in order.

ZooKeeper does not use handles to access znodes. Each request instead includes the full path of the znode being operated on. Not only does this choice simplifies the API (no open() or close() methods), but it also eliminates extra state that the server would
need to maintain.

Each of the update methods take an expected version number, which enables the implementation of conditional updates. If the actual version number of the znode does not match the expected version number the update fails with an unexpected version error. If the version number is 1 it does not perform version checking.


