---
title: "Wait-free coordination: Zookeeper"
layout: post
---

- **Paper** <http://nil.csail.mit.edu/6.824/2020/papers/zookeeper.pdf>
- **Notes** <https://timilearning.com/posts/mit-6.824/lecture-8-zookeeper/>

ZooKeeper is a service for coordinating processes of distributed applications, it is built for maintaining configuration information, naming, providing distributed synchronization, and providing group services.

Compared with [Chubby](https://static.googleusercontent.com/media/research.google.com/en//archive/chubby-osdi06.pdf) in a lot of places, chubby is a lock service for loosely-coupled distributed systems built by Google

**Group membership, Configuration and leader elections** are common forms of coordination in distributed systems, we also want our processes to know which other processes are alive and what those processes are in charge of.

Locks constitute a powerful coordination primitive that implement mutually exclusive access to critical resources but Zookeeper moved away from locks cause its **blocking** and hence can cause slow or faulty clients to negatively impact the performance of faster clients. 

Zookeeper has the following properties

- Wait Free (Performance ++)
- FIFO Client Ordering (all requests from a given client are executed in the order that they were sent by the client.)
- Linearizable writes (all requests that update the state of ZooKeeper are serializable and respect precedence)

Guaranteeing FIFO client order enables clients to submit operations asynchronously and when this happens we also have to make sure they are Linearizable which has a performance cost

![stuff](https://dl.acm.org/cms/attachment/286aad7d-9771-4848-9f01-040caeca59c7/ins03.gif)


For this we implement a **leader-based atomic broadcast protocol called ZAB (Zookeeper Atomic Broadcast)** which is a consensus protocol similar to Raft or Paxos. [More Info](https://distributedalgorithm.wordpress.com/2015/06/20/architecture-of-zab-zookeeper-atomic-broadcast-protocol/)

Zookeeper is read heavy so client side caching is important so it allows clients to cache and watch for updates to the cached object and receive a notification. Chubby uses leases to avoid blocking from faulty clients

By implementing these watches zookeeper allow clients to
receive timely notifications of changes without requiring polling.

- **Coordination Kernel** proposes a wait-free coordination service with relaxed consistency guarantees for use in distributed systems.
- Use **Coordination recipes** to build higher level coordination primitives, even blocking and strongly consistent primitives that are often used in distributed applications

### Terminology

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


### Client API

The ZooKeeper client API exposes a number of methods.

- **create(path, data, flags):** Creates a znode with pathname path, stores data[] in it, and returns the name of the new znode. flags enables a client to select the type of znode: regular, ephemeral and set the sequential flag

- **delete(path, version):** Deletes the znode path if that znode is at the expected version;

- **exists(path, watch):** Returns true if the znode with path name path exists, and returns false otherwise. The watch flag enables a client to set a watch on the znode;

- **getData(path, watch):** Returns the data and meta-data, such as version information, associated with the znode. The watch flag works in the same way as it does for exists(), except that ZooKeeper does not set the watch if the znode does not exist;

- **setData(path, data, version):** Writes data[] to znode path if the version number is the current version of the znode;

- **getChildren(path, watch):** Returns the set of names of the children of a znode;

- **sync(path):** Waits for all updates pending at the start of the operation to propagate to the server that the client is connected to. Sync can ensure that the read for a znode is linearizable though it comes at a performance cost. It forces a server to apply all its pending write requests before processing a read. **Similar to FLUSH primitive**

All methods have both a synchronous and an asynchronous version available through the API. An application uses the synchronous API when it needs to execute a single ZooKeeper operation and it has no concurrent tasks to execute, so it makes the necessary ZooKeeper call and blocks. The asynchronous API, however, enables an application to have both multiple outstanding ZooKeeper operations and other tasks executed in parallel. The ZooKeeper client guarantees that the corresponding callbacks for each operation are invoked in order.

ZooKeeper does not use handles to access znodes. Each request instead includes the full path of the znode being operated on. Not only does this choice simplifies the API (no open() or close() methods), but it also eliminates extra state that the server would need to maintain.

Each of the update methods take an expected version number, which enables the implementation of conditional updates. If the actual version number of the znode does not match the expected version number the update fails with an unexpected version error. If the version number is 1 it does not perform version checking.

**Sessions**. A client connects to ZooKeeper and initiates a session. Sessions have an associated timeout. ZooKeeper considers a client faulty if it does not receive anything from its session for more than that timeout. It is through this session that ZooKeeper can identify clients in fulfilling the FIFO order guarantee


With leader managing processes and configuration has two important requirements
- As the new leader starts making changes we do not want other processes to start using the configuration that is being changed;
• If the new leader dies before the configuration has been updated we do not want the processes to use this partial configuration.

With ZooKeeper, the new leader can designate a path as the ready znode and other processes will only use the configuration when that znode exists. The new leader makes the configuration change by deleting ready, updating the various configuration znodes, and creating ready. All of these changes can be pipelined and issued asynchronously to quickly update the configuration state.

Because of the ordering guarantees, if a process sees the ready znode, it must also see all the configuration changes made by the new leader. If the new leader dies before the ready znode is created then the other processes know that the configuration has not been finalized and do not use it.

Zookeeper can be used to implement powerful primitives without knowing about them by clients using the ZooKeeper client API. Some common primitives such as group membership and configuration management are also wait-free. For others, such as rendezvous, clients need to wait for an event. Even though ZooKeeper is wait-free, we can implement efficient blocking primitives with ZooKeeper. ZooKeeper’s ordering guarantees allow efficient reasoning about system state, and watches allow for efficient waiting.

### Configuration management

To implement dynamic configuration management with ZooKeeper, we can store configuration in a znode, zc. When a process is started, it starts up with the full pathname of zc. The process can get its required configuration by reading zc and setting the watch flag to true. It will get notified whenever the configuration changes, and can then read the new configuration with the watch flag set to true again.

### Rendezvous

[rendezvous meaning stackoverflow](https://cs.stackexchange.com/questions/105330/how-is-the-literal-meaning-of-rendezvous-related-to-its-usage-in-distributed-c/105332#105332)

Let's consider a scenario where a client wants to start a master process and many worker processes, with the job of starting the processes being handled by a scheduler. The worker processes will need information about the address and port of the master to connect to it. However, because a scheduler starts the processes, the client will not know the master's port and address ahead of time for it to give to the workers.

We handle this scenario with ZooKeeper using a rendezvous znode, `Zr` , which is an node created by the client. The client passes the full pathname of `Zr` as a startup parameter of the master and worker processes. When the master starts it fills in `Zr` with information about addresses and ports it is using. When workers start, they read `Zr` with watch set to true. If `Zr`
has not been filled in yet, the worker waits to be notified when `Zr` is updated. If `Zr` is an ephemeral node, master
and worker processes can watch for `Zr` to be deleted and clean themselves up when the client ends.

### Group Membership

As described in an earlier section, we can use ZooKeeper to implement group membership by creating a znode zg to represent the group. Any process member of the group can create an ephemeral child znode with a unique name under zg using the SEQUENTIAL flag. The znode representing a process will be automatically removed when the process fails or ends.

A process can obtain other information about the other members of its group by listing the children under zg. It can then monitor changes in group membership by setting the watch flag to true.


### Simple Locks

The simplest lock implementation uses **lock files**. The lock is represented by a znode. To acquire a lock,
a client tries to create the designated znode with the **EPHEMERAL flag**. If the create succeeds, the client
holds the lock. Otherwise, the client can read the znode with the watch flag set to be notified if the current
leader dies. A client releases the lock when it dies or explicitly deletes the znode. Other clients that are waiting
for a lock try again to acquire a lock once they observe the znode being deleted.

This has two problems.
- It suffers from the **herd effect.** If will be many clients waiting to acquire a lock even tho one client can acquire the lock. 
- It only implements exclusive locking or **write lock.**


### Footer

- [Zookeeper vs etcd](https://imesha.me/apache-curator-vs-etcd3-9c1362600b26)

- [On eventually consistent service discovery](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/upstream/service_discovery)

One use of Zookeeper is as a fault-tolerant lock service. Why isn't possible for two clients to acquire the same lock? In particular, how does Zookeeper decide if a client has failed and it can give the client's locks to other clients?

- [zookeeper-architecture](https://data-flair.training/blogs/zookeeper-architecture/)

### FAQ

**Q: Why are only update requests A-linearizable?**

A: Because the authors want reads to scale with the number of servers,
so they want to them to execute a server without requiring any
interaction with other servers. This comes at the cost of
consistency: they are allowed to return stale data.

**Q: How does linearizability differ from serializability?**

A: Serializability is a correctness condition that is typically used for
systems that provide transactions; that is, systems that support
grouping multiple operations into an atomic operations.

Linearizability is typically used for systems without transactions.
When the Zookeeper paper refers to "serializable" in their definition
of linearizability, they just mean a serial order.

We talk about serializability in subsequent papers. Here is a blog
post that explains the difference, if you are curious:
http://www.bailis.org/blog/linearizability-versus-serializability/

Although the blog post gives precise definitions, designers are not
that precise when using those terms when they describe their
system, so often you have to glean from the context what
correctness condition the designers are shooting for.

**Q: What is pipelining?**

Zookeeper "pipelines" the operations in the client API (create,
delete, exists, etc). What pipelining means here is that these
operations are executed asynchronously by clients. The client calls
create, delete, sends the operation to Zookeeper and then returns.
At some point later, Zookeeper invokes a callback on the client that
tells the client that the operation has been completed and what the
results are. This asynchronous interface allow a client to pipeline
many operations: after it issues the first, it can immediately issue a
second one, and so on. This allows the client to achieve high
throughput; it doesn't have to wait for each operation to complete
before starting a second one.

A worry with pipelining is that operations that are in flight might be
re-ordered, which would cause the problem that the authors to talk
about in 2.3. If a the leader has many write operations in flight
followed by write to ready, you don't want those operations to be
re-ordered, because then other clients may observe ready before the
preceding writes have been applied. To ensure that this cannot
happen, Zookeeper guarantees FIFO for client operations; that is the
client operations are applied in the order they have been issued.

**Q: What about Zookeeper's use case makes wait-free better than locking?**

I think you mean "blocking" -- locking (as in, using locks) and blocking (as
in, waiting for a request to return before issuing another one) are very
different concepts.

Many RPC APIs are blocking: consider, for example, clients in Lab 2/3 -- they
only ever issue one request, patiently wait for it to either return or time out,
and only then send the next one. This makes for an easy API to use and reason
about, but doesn't offer great performance. For example, imagine you wanted to
change 1,000 keys in Zookeeper -- by doing it one at a time, you'll spend most
of your time waiting for the network to transmit requests and responses (this is
what labs 2/3 do!). If you could instead have *several* requests in flight at
the same time, you can amortize some of this cost. Zookeeper's wait-free API
makes this possible, and allows for higher performance -- a key goal of the
authors' use case.

**Q: What does wait-free mean?**

A: The precise definition is as follows: A wait-free implementation of
a concurrent data object is one that guarantees that any process can
complete any operation in a finite number of steps, regardless of the
execution speeds of the other processes. This definition was
introduced in the following paper by Herlihy:
https://cs.brown.edu/~mph/Herlihy91/p124-herlihy.pdf

The implementation of Zookeeper API is wait-free because requests return
to clients without waiting for other, slow clients or servers. Specifically,
when a write is processed, the server handling the client write returns as
soon as it receives the state change (搂4.2). Likewise, clients' watches fire
as a znode is modified, and the server does *not* wait for the clients to
acknowledge that they've received the notification before returning to the
writing client.

Some of the primitives that client can implement with Zookeeper APIs are
wait-free too (e.g., group membership), but others are not (e.g., locks,
barrier).

**Q: What is the reason for implementing 'fuzzy snapshots'? How can**
state changes be idempotent?

A: If the authors had to decided to go for consistent snapshots,
Zookeeper would have to stop all writes will making a snapshot for
the in-memory database. You might remember that GFS went for
this plan, but for large database, this could hurt the performance of
Zookeeper seriously. Instead the authors go for a fuzzy snapshot
scheme that doesn't require blocking all writes while the snapshot is
made. After reboot, they construct a consistent snapshot by
replaying the messages that were sent after the checkpoint started.
Because all updates in Zookeeper are idempotent and delivered in
the same order, the application-state will be correct after reboot and
replay---some messages may be applied twice (once to the state
before recovery and once after recovery) but that is ok, because they
are idempotent. The replay fixes the fuzzy snapshot to be a consistent
snapshot of the application state.

Zookeeper turns the operations in the client API into something that
it calls a transaction in a way that the transaction is idempotent. For
example, if a client issues a conditional setData and the version
number in the request matches, Zookeeper creates a setDataTXN
that contains the new data, the new version number, and updated
time stamps. This transaction (TXN) is idempotent: Zookeeper can
execute it twice and it will result in the same state.

**Q: How does ZooKeeper choose leaders?**

A: Zookeeper uses ZAB, an atomic broadcast system, which has leader
election build in, much like Raft. If you are curious about the
details, you can find a paper about ZAB here:
http://dl.acm.org/citation.cfm?id=2056409

**Q: How does Zookeeper's performance compare to other systems**
such as Paxos?

A: It has impressive performance (in particular throughput); Zookeeper
would beat the pants of your implementation of Raft. 3 zookeeper
server process 21,000 writes per second. Your raft with 3 servers
commits on the order of tens of operations per second (assuming a
magnetic disk for storage) and maybe hundreds per second with
SSDs.

**Q: How does the ordering guarantee solve the race conditions in**
Section 2.3?

If a client issues many write operations to a z-node, and then the
write to Ready, then Zookeeper will guarantee that all the writes
will be applied to the z-node before the write to Ready. Thus, if
another client observes Ready, then the all preceding writes must
have been applied and thus it is ok for other clients to read the info
in the z-node.

**Q: How big is the ZooKeeper database? It seems like the server must**
have a lot of memory.

It depends on the application, and, unfortunately, the paper doesn't
report on it for the different application they have used Zookeeper
with. Since Zookeeper is intended for configuration services/master
services (and not for a general-purpose data store), however, an
in-memory database seems reasonable. For example, you could
imagine using Zookeeper for GFS's master and that amount of data
should fit in the memory of a well-equipped server, as it did for GFS.

**Q: What's a universal object?**

A: It is a theoretical statement of how good the API of Zookeeper is
based on a theory of concurrent objects that Herlihy introduced:
https://cs.brown.edu/~mph/Herlihy91/p124-herlihy.pdf. We won't
spend any time on this statement and theory, but if you care there is
a gentle introduction on this wikipedia page:
https://en.wikipedia.org/wiki/Non-blocking_algorithm.

The reason that authors appeal to this concurrent-object theory is
that they claim that Zookeeper provides a good coordination kernel
that enables new primitives without changing the service. By
pointing out that Zookeeper is an universal object in this
concurrent-object theory, they support this claim.

**Q: How does a client know when to leave a barrier (top of page 7)?**

A: Leaving the barrier involves each client watching the znodes for
all other clients participating in the barrier. Each client waits for
all of them to be gone. If they are all gone, they leave the barrier
and continue computing.

**Q: Is it possible to add more servers into an existing ZooKeeper without taking **the service down for a period of time?

It is -- although when the original paper was published, cluster
membership was static. Nowadays, ZooKeeper supports "dynamic
reconfiguration":

https://zookeeper.apache.org/doc/r3.5.3-beta/zookeeperReconfig.html

How do you think this compares to Raft's dynamic configuration change
via overlapping consensus, which appeared two years later?

**Q: How are watches implemented in the client library?**

It depends on the implementation. In most cases, the client library
probably registers a callback function that will be invoked when the
watch triggers.

For example, a Go client for ZooKeeper implements it by passing a
channel into "GetW()" (get with watch); when the watch triggers, an
"Event" structure is sent through the channel. The application can
check the channel in a select clause.
