# CS2SNS Secure Chat Service

Multi-user group chat over Java sockets, built up across the five coursework levels.

| Level | Status |
|---|---|
| 1. Basic chat functionality | Done |
| 2. Secure communication (SSL/TLS) | To do (`--secure` flag reserved) |
| 3. Additional security control | To do |
| 4. Cross-machine deployment | To do |
| 5. Wireshark traffic analysis | To do |

## Requirements

JDK 17 or newer (developed on JDK 21). No external libraries.

## Build

```bash
javac -d out $(find src -name "*.java")
```

On Windows PowerShell:

```powershell
javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
```

## Run

Start the server (one terminal):

```bash
java -cp out server.ChatServer --port 8888
```

Start as many clients as you like (one terminal each):

```bash
java -cp out client.ChatClient --host localhost --port 8888
```

For another machine on the same network, replace `localhost` with the server's IP
(`ipconfig` on Windows, `ip a` on Linux).

Options: `--port` (default 8888), `--host` (client only, default localhost),
`--secure` (TLS, from Level 2). Without `--secure` traffic is plaintext; this mode is
kept on purpose for the Level 5 Wireshark comparison.

## Chat commands

| Command | Effect |
|---|---|
| `/users` | List who is online |
| `/help` | Show commands |
| `/quit` | Leave the chat |

## Project layout

```
src/
  common/   Protocol rules, socket creation (plain/TLS switch), argument parsing
  server/   ChatServer (accept loop), ClientHandler (one per client), ClientRegistry
  client/   ChatClient (keyboard thread + listener thread)
certs/      Keystores for Level 2 (git-ignored)
docs/       Screenshots and report material
```

## Level 1 design notes

- **One thread per client.** The server's main thread only accepts connections and hands
  each one to a `ClientHandler` on a thread pool, so a slow client never blocks others.
- **Thread-safe membership.** `ClientRegistry` uses a `ConcurrentHashMap`; `putIfAbsent`
  makes "is the name free?" and "claim it" one atomic step. `send()` is synchronised so
  concurrent broadcasts can't interleave on one client's stream.
- **Identity is server-side.** The sender's name comes from the server's record of that
  connection, not from the message text, so a client can't post as someone else.
- **Input checks.** Usernames are validated (unique ignoring case, letters/digits/`_`/`-`,
  max 20 chars); messages have control characters stripped and are capped at 500 chars.
- **Graceful disconnects.** Whether a client types `/quit` or its process is killed, the
  handler's `finally` block removes it and announces the leave; the server keeps running.

### Level 1 test log

| Test | Expected | Result |
|---|---|---|
| 3 clients join and chat | Every message reaches all clients with sender name | Pass |
| Duplicate name (`ALICE` when `alice` online) | Rejected, prompted again | Pass |
| Invalid name (`bad name!`) | Rejected with reason, prompted again | Pass |
| `/users` | Lists online users | Pass |
| Unknown command | Error shown only to sender | Pass |
| Client process killed mid-chat | Others told it left; server keeps running | Pass |
| `/quit` | Client exits cleanly, others notified | Pass |
