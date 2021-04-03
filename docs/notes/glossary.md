## Ayo Jargon Check

### Linearizability vs Serializability

Distributed systems uses serializability as the basic correctness condition for concurrent computations. In this model, a transaction is a thread of control that applies a finite sequence of primitive operations to a set of objects shared with other transactions. A history is serializable if it is equivalent to one in which transactions appear to execute sequentially, i.e., without interleaving. A (partial) precedence order can be defined on non-overlapping pairs of transactions in the obvious way. A history is strictly serializable if the transactions’ order in the sequential history is compatible with their precedence order

Linearizability can be viewed as a special case of strict serializability where transactions are restricted to consist of a single operation applied to a single object. Nevertheless, this single-operation restriction has far-reaching practical and formal consequences, giving linearizable computations a different flavor from their serializable counterparts. An immediate practical consequence is that concurrency control mechanisms appropriate for serializability are typically inappropriate for linearizability because they introduce unnecessary overhead and place unnecessary restrictions on concurrency

**Serializability is a guarantee about transactions whereas Linearizability is the ability to re-arrange those transactions in a sequential fashion**

[Link](https://accelazh.github.io/storage/Linearizability-Vs-Serializability-And-Distributed-Transactions-Copy)


### Leases

Caching introduces the overhead and complexity of ensuring consistency, reducing some of its performance benefits. In a distributed system, caching must deal with the additional complications of communication and host failures.

Leases are proposed as a time-based mechanism that provides efficient consistent access to cached data in distributed systems

Lease is a contract that gives its holder specified rights to some resource for a limited period. Because it is time-limited, a lease is an alternative to a lock for resource serialization.

- Permission to serve data for some time period
- Wait until lease expires before applying updates
- Depends a lot on well-behaved clocks.


[Link](https://blog.acolyer.org/2014/10/31/leases-an-efficient-fault-tolerant-mechanism-for-distributed-file-cache-consistency/)

[Link](https://zhu45.org/posts/2018/Mar/07/cache-lease-consistency-invalidation/)


What is the difference between using a lease rather a cache with a randomized TTL ?
