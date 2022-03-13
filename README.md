# mid

makes easier to manage application's network communication

## Why it matters

Networking is a crucial part of software development.
Applications or more precisely services forms a network which can be named as a system.
Every participant could process an incoming transaction or/and make an outgoing call.
Understanding communication on this level could not be underestimated in terms of ownership either part or whole system.

With development oriented network app you can:
- troubleshoot
- modify
- record/play
communication.

## What is network from developer perspective

For me the most important part is a set of tcp, http, websockets protocols:
1. tcp is a protocol from transport layer. It handles raw byte arrays communication within OS. For user-space only send, read, connect, bind and accept system calls could be interested.
2. http is a protocol from application layer. The most popular use cases are: get static or dynamic resource, post or put data, send or receive data stream (chunks)
3. websockets is a protocol from application layer. It's async by nature, therefore nothing like request-response pattern could be applied. The most popular use cases is to create your own protocol with id or UUID for mapping response on sent requests.

## Tools for testing network
- browser
- curl
- netcat
- tcpdump
- webpack-dev-server
- spring-boot sample
- nodejs sample

## Feature 1: client subcommand
```
I> mid curl --help
Usage: mid curl [-hV] [-d=<data>] [-m=<method>] [-H=<headers>]... <uri>
      <uri>
  -d=<data>
  -h, --help      Show this help message and exit.
  -H=<headers>
  -m=<method>
  -V, --version   Print version information and exit.
```

Let's start with public http services like: `https://httpbin.org/`
- default:
- method, header and data:

## Feature 3: trace network like simple proxy
## Feature 3: trace network like simple proxy


## Development:
- source script for direct access to mid: `souce source sourceIt.sh`
