## Raft

http://nil.csail.mit.edu/6.824/2020/papers/raft-extended.pdf

FAQ: http://nil.csail.mit.edu/6.824/2020/papers/raft-faq.txt

Visual Aid: http://thesecretlivesofdata.com/raft/

Site: https://raft.github.io/

So far with systems like GFS we had one master and it handled operations across distributed systems but as more complicates systems rose having one master became the bottleneck and even when we have multiple masters we need a **good consensus algorithm that allows a collection of machines to work as a coherent group that can survive the failures of some of its members.**

Paxos was the earliest and a very popular algorithm for consensus. This paper paints Paxos as a scary algorithm that is very very hard to understand, work with and build practical implementations. To mitigate this **Raft was developed with a focus on understandability** rather than anything else

- **Strong Leader** i.e Logs only flow from leader to other servers simplifying replicated log management
- **Random Timers to Elect Leaders** small mechanism on top of existing heartbeats helps resolving conflicts simply and rapidly
- **Joint Consensus for Membership Changes** allows majority of the two different configurations to overlap during transitions. This allows the cluster to continue operating normally during configuration changes.


### Replicated state machines

A collection of servers compute identical copies of the same state and can continue operating even if some of the servers are down to provide fault tolerance.

Replicated state machines are typically implemented using a replicated log containing a series of commands, which its state machine executes in order.

**Keeping the replicated log consistent is the job of the consensus algorithm.**

The consensus module on a server
receives commands from clients and adds them to its log and also ensures that every server has the same logs in the same order even if some servers fail. Some practical rules followed by consensus algorithms.

- They never return incorrect results even in bad conditions like network delays, packet loss, duplication and reordering
- They are fully functional as long as any majority of the servers are operational and can communicate with each other and with clients (eg: 3 out of 5)
- Independent of timing for consistency so as to tolerate faulty clocks and extreme message
delays.

### The Algorithm

Raft implements consensus by first electing a distinguished leader, then giving the leader complete responsibility for managing the replicated log. The leader accepts
log entries from clients, replicates them on other servers, and tells servers when it is safe to apply log entries to their state machines. Having a leader simplifies the management of the replicated log. For example, the leader can decide where to place new entries in the log without consulting other servers, and data flows in a simple fashion from the leader to other servers. A leader can fail or be-come disconnected from the other servers, in which case
a new leader is elected.

**The leader waits until a majority of nodes have written the entry to the log before making changes to the state machine**

A Raft cluster contains several servers; five is a typical number, which allows the system to tolerate two failures. At any given time each server is in one of three states:
**leader, follower, or candidate** 

- In normal operation there is exactly one leader and all of the other servers are followers. 
- Followers are passive: they issue no requests on their own but simply respond to requests from leaders and candidates.
- The leader handles all client requests so if
a client contacts a follower, the follower redirects it to the leader
- The third state candidate is used to elect a new leader

Raft divides time into terms of arbitrary length called terms, each term begins with an election where candidates attempt to become leader for the rest of the term, In a split vote no leader is elected and a new term will start shortly.

![image info](https://miro.medium.com/max/580/1*TO9R_SS5Tfn07b0sObfhgg.png)

- Different servers may observe the transitions between terms at different times, and in some situations a server may not observe an election or even entire terms.
- Terms act as a **logical clock** in Raft, and they allow servers to detect obsolete information such as **stale leaders.**
- Each server stores a current term number, which increases monotonically over time. 
- Current terms are exchanged whenever servers communicate; if one server’s current
term is smaller than the other’s, then it updates its current term to the larger value. If a candidate or leader discovers
that its term is out of date, it immediately reverts to follower state. If a server receives a request with a stale term
number, it rejects the request.

Raft servers communicate using remote procedure calls (RPCs) and the basic consensus algorithm requires only
two types of RPCs. RequestVote RPCs are initiated by candidates during elections and Append Entries RPCs are initiated by leaders to replicate log entries and to provide a form of heartbeat

### Raft Guarantees

- **Election Safety:** at most one leader can be elected in a given term.
- **Leader Append-Only:** a leader never overwrites or deletes entries in its log; it only appends new entries
- **Log Matching:** if two logs contain an entry with the same index and term, then the logs are identical in all entries
up through the given index.
- **Leader Completeness:** if a log entry is committed in a given term, then that entry will be present in the logs of the leaders for all higher-numbered terms.
- **State Machine Safety:** if a server has applied a log entry at a given index to its state machine, no other server will ever apply a different log entry for the same index.


### Leader Election

- All our nodes start in the follower state
- If followers don't hear the periodic
heartbeats i.e AppendEntries RPCs that carry no log entries from a leader then the follower assumed no leader exists and can become a candidate (Election timeout)
- The candidate then requests votes from other nodes in parallel using the RequestVote RPCs

Now based on the votes 3 things can happen

**Candidate Wins Election** If it receives votes from
a majority of the servers and on winning sends heartbeat messages to all of
the other servers to establish its authority and prevent new elections.

**Another Server Claims** leadership when the candidate receives an heartbeat RPC from the so called "leader" now it checks if the leader’s term (counter) is at least
as large as the candidate’s current term and if it is then it recognizes the leader and goes back to follower else it rejects the RPC and continues the election.

**No Winner** happens incases of Split votes and no majorities, when a lot of followers become candidates at the same time. When this happens, each candidate will time out and start a new election by incrementing its term and initiating another round of Request-
Vote RPCs. However, without extra measures split votes could repeat indefinitely

Raft uses **randomized election timeouts** to ensure that split votes are rare and that they are resolved quickly

This election term will continue until a follower stops receiving heartbeats and becomes a candidate.

### Log Replication

Once a leader has been elected, it begins servicing client requests. Each client request contains a command to be executed by the replicated state machines.

The leader appends the command to its log as a new entry, then issues AppendEntries RPCs in parallel to each of the other servers to replicate the entry. When the entry has been safely replicated the leader applies the entry to its state machine and returns the result of that execution to the client. If followers crash or run slowly or if network packets are lost, the leader retries AppendEntries RPCs indefinitely even after it has responded to the client until all followers eventually store consistent log entries.






