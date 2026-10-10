# bb-deploy

A native build of BlackBerry's `blackberry-deploy` tool for BlackBerry 10 devices, compiled with GraalVM
Native Image, with an added daemon mode for building device-management applications.

## What it is

- **Built on BlackBerry's tool.** It wraps the original `blackberry-deploy` classes (`com.qnx.bbt.deploy`).
  Neither `blackberry-deploy` nor any other BlackBerry tool is modified.
- **Same tool by default.** Without the new flags, it has the original feature set and command-line usage.
- **Adds a daemon mode.** `bb-deploy -daemon` keeps device sessions open and accepts commands one after
  another, so the slow login happens once per device rather than once per command. It is meant for developers
  building applications that manage BlackBerry 10 devices.

## Daemon mode

The daemon reads requests from stdin and writes structured output to stdout, as one JSON object per line
(newline-delimited JSON). Each request is answered with `log` and `progress` events, a `result` with the parsed
data and the tool's raw output, and a final `done`:

```
{"id":0,"type":"log","level":"info","message":"Application-Development-Mode is false, sync disabled"}
{"id":0,"type":"log","level":"info","message":"Sending request: INSTALL_AND_LAUNCH"}
{"id":0,"type":"log","level":"info","message":"Action: Install and Launch"}
{"id":0,"type":"log","level":"info","message":"File size: 1816710"}
{"id":0,"type":"log","level":"info","message":"Installing com.example.TLSDeBug.testRel_le_TLSDeBugc09806e9..."}
{"id":0,"type":"log","level":"info","message":"Processing 1816710 bytes"}
{"id":0,"type":"progress","percent":100,"phase":"install"}
{"id":0,"type":"log","level":"info","message":"Launching com.example.TLSDeBug.testRel_le_TLSDeBugc09806e9..."}
{"id":0,"type":"log","level":"info","message":"done"}
{"id":0,"type":"result","command":"installApp","data":{"success":true,"fullName":"com.example.TLSDeBug.testRel_le_TLSDeBugc09806e9","name":"com.example.TLSDeBug","id":"testRel_le_TLSDeBugc09806e9","version":"1.0.0.1","strategy":"full","nothingToSync":false,"launched":true,"launchResult":"188989588","fellBackToFull":false,"syncDisabledReason":"Application-Development-Mode is false, sync disabled"},"raw":["Info: Application-Development-Mode is false, sync disabled","Info: Sending request: INSTALL_AND_LAUNCH","Info: Action: Install and Launch","Info: File size: 1816710","Info: Installing com.example.TLSDeBug.testRel_le_TLSDeBugc09806e9...","Info: Processing 1816710 bytes","Info: Progress 100%...","actual_dname::com.example.TLSDeBug.testRel_le_TLSDeBugc09806e9","actual_id::testRel_le_TLSDeBugc09806e9","actual_version::1.0.0.1","result::success","Info: Launching com.example.TLSDeBug.testRel_le_TLSDeBugc09806e9...","result::188989588","Info: done"]}
{"id":0,"type":"done","ok":true}
```

### Trying it out

List the devices on the network (no login needed):

```sh
printf '%s\n' '{"id":1,"method":"listDevices"}' | ./target/bb-deploy -daemon
```

Connect to a device, then list its installed apps over the same session. The first `connect` creates session `s1`,
and it must finish before the session can be used, so wait between the two requests (adjust the sleep to your
network):

```sh
( printf '%s\n' '{"id":1,"method":"connect","params":{"host":"192.168.1.82","password":"12345"}}'
  sleep 8
  printf '%s\n' '{"id":2,"method":"listInstalledApps","params":{"session":"s1"}}'
) | ./target/bb-deploy -daemon
```

When stdin closes, the daemon finishes the requests it has already accepted and then exits.

The output of `connect` includes the device's PIN, serial number and IMEI, so scrub it before sharing.

## Status

The daemon protocol is a draft. Detailed protocol documentation is in progress and will be published later.