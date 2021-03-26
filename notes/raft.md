## Raft

Material

- Paper: http://nil.csail.mit.edu/6.824/2020/papers/raft-extended.pdf
- FAQ: http://nil.csail.mit.edu/6.824/2020/papers/raft-faq.txt & http://nil.csail.mit.edu/6.824/2020/papers/raft2-faq.txt
- Visual Aid: http://thesecretlivesofdata.com/raft/
- Site: https://raft.github.io/
- Videos: https://www.youtube.com/watch?v=64Zp3tzNbpE & https://www.youtube.com/watch?v=4r8Mz3MMivY
- RC notes: https://docs.google.com/document/d/1ElFUjssB2sp6vlzoakO9QQEbev_Bl2_XfmrM_uK2WHI


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

**In a RAFT system of 2K+1 machines we can tolerate K failures eg: 3 machines we can tolerate 1 failure**

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
heartbeats i.e `AppendEntries` RPCs that carry no log entries from a leader then the follower assumed no leader exists and can become a candidate (Election timeout)
- The candidate then requests votes from other nodes in parallel using the RequestVote RPCs

Now based on the votes 3 things can happen

**Candidate Wins Election** If it receives votes from
a majority of the servers and on winning sends heartbeat messages to all of
the other servers to establish its authority and prevent new elections.

**Another Server Claims** leadership when the candidate receives an heartbeat RPC from the so called "leader" now it checks if the leader’s term (counter) is at least as large as the candidate’s current term and if it is then it recognizes the leader and goes back to follower else it rejects the RPC and continues the election.

**No Winner** happens incases of Split votes and no majorities, when a lot of followers become candidates at the same time. When this happens, each candidate will time out and start a new election by incrementing its term and initiating another round of Request-
Vote RPCs. However, without extra measures split votes could repeat indefinitely

Raft uses **randomized election timeouts** to ensure that split votes are rare and that they are resolved quickly

This election term will continue until a follower stops receiving heartbeats and becomes a candidate.

Suppose the randomized election timeouts lie between `Tmin` and `Tmax` then Tmin must be atleast double of heartbeat team cause we don't want hella elections to be happening even before a heartbeats go out. Tmax is the max time we can afford our system to be down cause when a leader is down and the next election hasn't happened the whole system is frozen so Tmax or the max value of the randomized election timeout cannot be too high, depends on how often failures happen and how available we want the system to be. It also cannot be too low cause between on a random election timeout servers need time to cast votes and elect a leader before the next timeout.


### Log Replication

Once a leader has been elected, it begins servicing client requests. Each client request contains a command to be executed by the replicated state machines.

The leader appends the command to its log as a new entry, then issues `AppendEntries` RPCs in parallel to each of the other servers to replicate the entry. When the entry has been safely replicated the leader applies the entry to its state machine and returns the result of that execution to the client. If followers crash or run slowly or if network packets are lost, the leader retries `AppendEntries` RPCs indefinitely even after it has responded to the client until all followers eventually store consistent log entries.

The leader decides when it is safe to apply a log entry to the state machines; such an entry is called committed. Raft guarantees that committed entries are durable and will eventually be executed by all of the available state machines. The leader keeps track of the highest index it knows to be committed, and it includes that index in future `AppendEntries` RPCs (including heartbeats) so that the other servers eventually find out.

Raft maintains the following properties to maintain coherency between the logs on different servers.

- If two entries in different logs have the same index and term, then they store the same command
- If two entries in different logs have the same index and term, then the logs are identical in all preceding entries

This **consistency check acts as an induction step:** the initial empty state of the logs satisfies the Log Matching Property, and the consistency check preserves this property whenever logs are extended. As a result, whenever `AppendEntries` returns successfully, the leader knows that the follower’s log is identical to its own log up through the new entries.

In Raft, the leader handles inconsistencies by forcing the followers logs to duplicate its own. This means that
conflicting entries in follower logs will be overwritten with entries from the leaders log.

The leader maintains a `nextIndex` for each follower, which is the index of the next log entry the leader will send to that follower. When a leader first comes to power it initializes all `nextIndex` values to the index just after the last one in its log.If a follower’s log is inconsistent with the leaders the `AppendEntries` consistency check will fail. After a rejection, the leader decrements `nextIndex` and retries `AppendEntries` RPC. Eventually `nextIndex` will reach a point where the leader and follower logs match. When this happens, `AppendEntries` will succeed, which removes conflicting entries in the followers log and appends entries from the leaders log. Once `AppendEntries` succeeds, the follower’s log is consistent with the leaders for the rest of the term.

Once AppendEntries succeeds, the follower’s log is consistent with the leader’s, and it will remain that way for the rest of the term.

### Safety

**Ensures that the state machine executes exactly the same commands in the same order.**

This rule might be broken if a follower might goes down while the leader commits several log entries, then it could be elected leader and overwrite these entries with new ones, as a result different state machines might execute different command sequences. This can be avoided with a simple rule called **Leader Completeness** which ensures that the leader for any given term con-
tains all of the entries committed in previous terms

**The leader for any given term contains all of the entries committed in previous terms**

Raft guarantees that all the committed entries from previous terms are present on each new leader from the moment of its election, without the need to transfer those entries to the leader. This means that log entries only flow in one direction, from leaders to followers, and leaders never overwrite existing entries in their logs.

Raft prevent a candidate from winning an election unless its log contains all committed entries. The Candidates log has to be atleast as **up-to-date** with any other log in the majority **where up-to-date:** If the logs have last entries with different terms, then the log with the later term is more **up-to-date**. If the logs end with the same term, then whichever log is longer is more **up-to-date**.



### End notes

RAFT can fail when a leader can send out outgoing requests i.e heartbeats but cannot accept incoming requests i.e from the client. In these case even tho leader fails to serve the client the followers get the heartbeat and assume everting is ok. A way to solve this is to also send a response from the follower to the leader heartbeat which needs to be acknowledged, this way we can catch situations where leader doesn't respond to incoming requests and re-trigger an election.

