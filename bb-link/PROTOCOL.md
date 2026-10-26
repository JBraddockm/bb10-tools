# bb-link protocol

bb-link is a daemon that manages BlackBerry 10 devices through the web interface that BlackBerry
Link uses (documented by the open source application Sachesi). It speaks the same line-delimited
JSON protocol as bb-deploy: the envelope, ids, events, sessions and ordering rules are those of
`bb-deploy/PROTOCOL.md` (protocol version 1). This document describes what is specific to bb-link.

Status tags: **[verified]** was checked against a real device (a BlackBerry 10 device with a
password, reached over USB networking), **[UNVERIFIED]** follows the PRD or the original tool's
source and still has to be captured. The login, `getDeviceProperties`, the structure of `getPin` and
the error mapping for a wrong password and an unreachable host are verified. The fixtures named
`*--real-*` come from captures; the others are synthetic and say so in a comment.

## 1. Running

```
bb-link -daemon [-trace <directory>]     serve the protocol on stdin/stdout
bb-link -parse <method>                  replay recorded answers from stdin (see section 8)
```

`hello` reports `"tool":"bb-link"`; clients tell the two daemons apart by that field and by the
`methods` list. Everything printed through `System.out` by a library goes to standard error.

## Connection paths

A device serves the same management interface over more than one network path, and the path decides
which of its programs answer. bb-link works out the path from the host address at `connect` and
refuses, with a clear message, a command whose program the path cannot reach — before it dials, not
after a dead connection times out.

| Path      | Address                                                        | Reachable programs                            |
|-----------|----------------------------------------------------------------|-----------------------------------------------|
| USB       | IPv4 link-local, `169.254.x.x`                                 | the whole interface                           |
| Tunnel    | IPv6 unique-local, `fd00::/8` or `fc00::/8` (the device's ULA) | the whole interface                           |
| Wi-Fi LAN | a private IPv4 range (`10/8`, `172.16/12`, `192.168/16`)       | only `login.cgi`                              |
| Other     | anything else (loopback, an unknown shape)                     | not refused by shape — left to the connection |

Over USB and the tunnel every method works. Over a Wi-Fi LAN address only the login is reachable, so
a `connect` with `deviceInfo: false` (login only) and `getSmbCredentials` and
`getAuthenticationCertificate` are allowed, while every method that reads or changes the device —
`getDeviceProperties`, `getPin`, the PIM methods, `pairDevice`, `installPackages`,
`uninstallPackages` — is refused as `COMMAND_FAILED` naming the paths that would reach it. A full
`connect` (the default, which reads the device properties) is refused over a Wi-Fi LAN address for
the same reason — unless the connect sets `tunnel: true`, which has bb-link bring a Wi-Fi tunnel up
through the broker and run the whole connect over the device's ULA instead (see `tunnel` below).

The tunnel's port, like USB, is `8443` and is gated by the client certificate: a tunnel connection
without the paired certificate fails the TLS handshake, and the error says so. What a reachable
program returns may still differ between paths — SMB credentials, for one, come back over the tunnel
but are empty over Wi-Fi — and those differences are reported as the device gives them, not guessed
in advance. One confirmed difference: `wipe.cgi` (the standalone `getPin`) closes the connection over
the tunnel without responding; `connect` no longer uses it — it reads the PIN from `discovery.cgi`
(`getDeviceCharacteristics`), which answers over USB and the tunnel alike, so the PIN is present over
the tunnel.

## 2. Methods

`session` is required wherever it is listed. `raw: true` adds the device output (one entry per
exchange) to the result, with cookies, passwords and identifying values (PIN, hardware ID, device
name, serial number, IMEI) masked.

| Method                         | Params                                                                                                       | Result `data`                                                                                                                                                                                                     | Notes                                                                                                                                                                                                               |
|--------------------------------|--------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `connect`                      | `host`, `port` (8443), `authentication`, `deviceInfo?`, `smbHost?`, `tunnel?`, `brokerSocket?`, `stableSmb?` | `session`, `host`, `port`, `authentication`, `device?`, `smb?`                                                                                                                                                    | logs in, then reads `dynamicProperties.cgi` and `discovery.cgi` and merges them into `device` (the PIN comes from `discovery.cgi`, over USB and the tunnel); with `deviceInfo` `false` it only logs in, no `device` |
| `disconnect`                   | `session`                                                                                                    | none                                                                                                                                                                                                              | idempotent                                                                                                                                                                                                          |
| `shutdown`                     | none                                                                                                         | none                                                                                                                                                                                                              |                                                                                                                                                                                                                     |
| `getDeviceProperties`          | `session`, `raw?`                                                                                            | `device`, `packages[]`                                                                                                                                                                                            | `dynamicProperties.cgi`                                                                                                                                                                                             |
| `getDeviceCharacteristics`     | `session`, `raw?`                                                                                            | `pin`, `guid`, `modelName`, `osType`, `platformVersion`, `imei`, `batteryLevel`, `developerMode`, `tunnelUp`, `screenWidth`, `screenHeight`, `flashSize`, `sdCard`, `blendPolicy`, `fuseEnabled`, `fuseInstalled` | `discovery.cgi` (GET); identity + status; the only PIN source over the tunnel; reachable over USB and the tunnel, not a plain Wi-Fi LAN                                                                             |
| `getPin`                       | `session`, `raw?`                                                                                            | `pin`                                                                                                                                                                                                             | read-only; `wipe.cgi`, which does not answer over the tunnel                                                                                                                                                        |
| `getSmbCredentials`            | `session`                                                                                                    | `user`, `password`, `workgroup`, `netBios`                                                                                                                                                                        | asks the device nothing; the credentials of the latest login                                                                                                                                                        |
| `listPimAccounts`              | `session`, `raw?`                                                                                            | `command`, `httpStatus`, `contentType`, `code`, `message`, `bodyFormat`, `body`                                                                                                                                   | lists the accounts of the personal information sync (section 9)                                                                                                                                                     |
| `findPimAccount`               | `session`, `workstationId`                                                                                   | same                                                                                                                                                                                                              | looks up the account of a workstation                                                                                                                                                                               |
| `getPimItems`                  | `session`, `workstationId`, `service`, filters                                                               | same, plus `service`                                                                                                                                                                                              | reads contacts, events or calendars                                                                                                                                                                                 |
| `pairDevice`                   | `session`, `confirmPin`, `devicesDirectory`, `replace?`, `raw?`                                              | `pin`, `deviceId`, `commonName`, `directory`, `authorityFile`, `keyFile`, `deviceAuthorityFile`, `authority`, `deviceAuthority`, `pairedAt`                                                                       | pairs this computer with the device: the device lists it in Device Connections; changes the device (section 11)                                                                                                     |
| `getAuthenticationCertificate` | `session`, `confirmPin`, `devicesDirectory`, `hostName?`, `replace?`, `verify?`, `verifyPort?`, `raw?`       | `pin`, `commonName`, `directory`, `certificateFile`, `keyFile`, `certificate`, `verification`, `issuedAt`                                                                                                         | has the device sign a certificate for the login on port 8443, through `login.cgi`; changes the device (section 13)                                                                                                  |
| `inspectCertificate`           | `file`                                                                                                       | the certificate's subject, issuer, serial number, validity, `daysLeft`, `expired`, fingerprint, key and signature                                                                                                 | reads a certificate file; no session, no device (section 13)                                                                                                                                                        |
| `listDevices`                  | none                                                                                                         | `devices[]`                                                                                                                                                                                                       | no session; streams `device` events                                                                                                                                                                                 |
| `inspectPackage`               | `file`, `validate?`                                                                                          | `file`, `sizeBytes`, `kind`, `packageType`, `packageId`, `packageName`, `packageVersion`, `architecture`, `manifestVersion`, `developmentMode`, `signed`, `validation?`                                           | no session, no device; reads the manifest of a bar (section 12)                                                                                                                                                     |
| `installPackages`              | `session`, `files`, `raw?`                                                                                   | `installed[]`, `bodyFormat`, `body`                                                                                                                                                                               | installs applications from bars; changes the device; progress events (section 12.7)                                                                                                                                 |
| `uninstallPackages`            | `session`, `packageIds`, `check?`, `raw?`                                                                    | `removed[]`, `bodyFormat`, `body`                                                                                                                                                                                 | removes applications by package id; changes the device and removes their data; progress events (section 12.9)                                                                                                       |

Every request is announced with a `log` event that says what it does (`Reading the device properties`,
`Reading the device characteristics`, `Reading the device PIN`), never the name of the device's CGI
program: the device offers the PIN on a program called `wipe.cgi`, and a message naming it would read
like a wipe.

**The PIN over the tunnel.** The standalone `getPin` goes through `wipe.cgi`, which closes the
connection without responding over the tunnel (an active close — the server accepts the TCP
connection and sends no bytes, `NoHttpResponseException`, not a timeout) **[verified over tunnel,
2026-10-26]**, so `getPin` still fails there. `connect` no longer depends on it: it reads the PIN from
`discovery.cgi` (`getDeviceCharacteristics`), which answers over USB **and** the tunnel, so the
`connect` result carries the PIN on both paths. If `discovery.cgi` cannot be read, `connect` still
succeeds, with a `warn` and no PIN or characteristics.

**Authentication.** `connect` requires an `authentication` object. Its `type` says how the client
proves who it is, and it carries exactly the fields of that type. Nothing is assumed: a request without
`authentication` is refused, and so is a field that does not belong to the type (a `password` sent
with the type `none` is an error, not a login without a password).

| `type`                   | Other fields                                 | What happens                                                                                                                                                                                                                                                                                                                                                                           |
|--------------------------|----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `none`                   | none                                         | the device has no password; the client logs in without one. If the device asks for a password after all, the login stops at once with `PASSWORD_REQUIRED`: **no answer is sent, so no failed attempt is counted**                                                                                                                                                                      |
| `password`               | `password` (not blank)                       | the login; the session carries the `dtmauth` cookie                                                                                                                                                                                                                                                                                                                                    |
| `certificate`            | `clientCertificate`, `clientKey`             | the certificate is offered on every connection and the login request is sent **without a password**. A device that accepts the certificate answers `Success` at once, without a challenge and without a cookie, and returns the share credentials. If it asks for a password instead, bb-link never answers: it logs a `warn` and carries on with the certificate alone, without `smb` |
| `certificateAndPassword` | `clientCertificate`, `clientKey`, `password` | the certificate is offered on every connection, and the login is done as well. A device that accepts the certificate logs the client in without asking for the password, and without a cookie; the password is only used if the device asks for it                                                                                                                                     |

```
{"id":1,"method":"connect","params":{"host":"169.254.159.149","authentication":{"type":"password","password":"…"}}}
{"id":1,"method":"connect","params":{"host":"169.254.159.149","port":8443,"authentication":{"type":"certificate","clientCertificate":"client_cert.pem","clientKey":"client_key.pem"}}}
```

The result names the type that was used (`"authentication":"certificate"`), so a client can tell that
a missing `smb` is expected. A password hash is only ever sent when the caller supplied a password.

Errors name the field as `authentication.<field>` (`authentication.type`, `authentication.password`,
`authentication.clientKey`, …), or `authentication` itself when the object is missing or is not an
object. The parameters `password`, `clientCertificate` and `clientKey` used to sit beside `host`;
sent there now they are refused with an error that says they belong inside `authentication`.

**The client key is a credential.** With the certificate and its key, anyone with access to the
device's USB address can log in and obtain the share credentials without the device password. The key
file must be kept private (`chmod 600`), and it must not be committed or shared. A certificate lasts
about 37 days.

`clientCertificate` is the path of a PEM file with the certificate (and, after it, a chain if there is
one), `clientKey` the path of the PEM file with its private key. The key
must be unencrypted and either `PRIVATE KEY` (PKCS #8) or `RSA PRIVATE KEY` (PKCS #1); an encrypted
key, or an EC key in the older SEC1 form, is refused with the `openssl pkcs8 -topk8 -nocrypt` command
that converts it. A key that does not belong to the certificate is refused too. The files are read
each time a connection is set up, so a certificate that is replaced on disk is used from then on. A
key is never logged, and a trace shows no more than that a certificate was presented.

The certificate is offered **whatever list of authorities the device sends**: the standard Java
behaviour offers a certificate only if its issuer is on that list, and the leaf of BlackBerry Link
is issued by an intermediate that only the device holds. With the type `certificate`, a device that logs
the certificate holder in returns the share credentials, so `connect` has `smb` in that mode too (over
USB; over Wi-Fi the login answer has none). If the device asks for a password at the login, the
session has no share credentials, `connect` has no `smb`, and `getSmbCredentials` fails. If the device
does not accept the certificate as proof, the first command ends with `AUTH_FAILED` ("The device did
not accept the client certificate as authentication"); no password is ever sent. If the handshake
itself is refused, the error is `CONNECTION_FAILED` and says that a certificate was offered.

`device` in the `connect` result merges two reads. From `dynamicProperties.cgi`: `name`, `hardwareId`,
`serialNumber`, `osVersion`, `radioVersion`, `freeApplicationSpace`, `hasPower`, `batteryLevel`,
`batteryStateOfHealth`. From `discovery.cgi`: `pin`, `guid`, `modelName`, `tunnelUp`, `developerMode`,
`flashSize`, `sdCard`, `blendPolicy`, `fuseEnabled`, `fuseInstalled` — enough for a client to act on
the device's state (for instance, warn when Blend is off or the file-share fuse is not enabled).
`osVersion` is the OS package version (the same value `discovery.cgi` calls `PlatformVersion`; the
name `osVersion` is kept). The PIN and the `discovery.cgi` fields are absent, with a `warn`, when
`discovery.cgi` could not be read; absent values are omitted.

`deviceInfo` (boolean, default `true`): with `false`, `connect` logs in and asks the device for nothing else, so
there is no `device` in the result. It is for a device that answers the login but not the programs that
describe it, as over Wi-Fi (below); the session then works for whatever the device does answer. The
script has it as `--login-only`.

`smbHost` (optional): the device's inner IPv4 to report as the SMB host over the Wi-Fi tunnel, instead
of the default `169.254.1.1`. The tunnel is opened outside bb-link — by `bb-tunnel`'s `TunnelManager`,
which allocates each device a distinct inner IPv4 from a pool — and whoever opens it passes that
address here, so concurrent devices each get the correct, non-colliding SMB host. Over USB the SMB
host is the connection address and `smbHost` is ignored. The `bblink` script passes it as
`--smb-host`.

`tunnel` (boolean, default `false`): opt a **Wi-Fi LAN** connect into a broker-managed tunnel. bb-link
asks the tunnel-broker daemon to bring up (or join) the device's tunnel, then runs the whole connect
over the device's ULA — so the user connects with the Wi-Fi LAN address and never handles the ULA,
and the SMB host comes from the tunnel's lease (no `smbHost` needed). The session holds the tunnel for
its lifetime and releases it on `disconnect`; if the tunnel drops, further commands fail with a clear
"tunnel has dropped" rather than hanging. It is ignored for a USB or a direct-ULA connect (neither
needs a tunnel), and a plain Wi-Fi LAN login or pairing stays tunnel-free, since bringing a tunnel up
needs an already-enrolled certificate. `brokerSocket` (optional) overrides the daemon's Unix-domain
socket (default `/tmp/bb-tunneld.sock`); `stableSmb` (boolean, default `false`) pins the constant
`169.254.1.1` SMB address instead of a pool-allocated one. The tunnel protocol is in
`../bb-tunnel/PROTOCOL.md` §7.

`smb` in the `connect` result and the result of `getSmbCredentials` **[verified]** carry the
credentials of the device's file share, which the device hands out with every successful login:

```json
{"user":"dtm","password":"…","workgroup":"WORKGROUP","netBios":"%h"}
```

They give access to the contents of the device, even when neither Wi-Fi storage nor USB mass storage
is switched on. The user name is fixed and **the password changes with every login**, including the
automatic logins that replace a session the device has forgotten. So `smb` in the `connect` result may
go out of date; `getSmbCredentials` always has the latest. `netBios` is passed on exactly as the
device writes it (the placeholder `%h`, the host name). `smb` is omitted, and `getSmbCredentials`
fails with `COMMAND_FAILED`, if the device did not hand out credentials. The password is returned to
the client that logged in on purpose; traces and `raw` output mask it, and nothing logs it.

`getDeviceProperties` data **[verified]**:

```json
{"device":{"name":"…","model":"BlackBerry","hardwareId":"0x0a0b0c0d","imei":"…","serialNumber":"…",
 "batteryLevel":11,"batteryHealth":88,"hasPower":true,"freeApplicationSpace":26503909376,
 "refurbDate":"0","policyRestrictions":"…",
 "os":{"name":"…","version":"10.3.3.3216"},"radio":{"name":"…","version":"10.3.3.3217"}},
 "perimeters":[{"kind":"personal","bbid":"…"}],
 "packages":[{"name":"…","type":"application","packageId":"…","versionId":"…","version":"…","fingerprint":"…"}],
 "removedPackages":[{"name":"…","type":"unclassified","packageId":"…","version":"…"}]}
```

The device nests its answer: packages under `Applications`, packages that were removed but whose data
is kept under `RemovedApplications` (they have no type, version id or fingerprint), the battery,
free space and serial number under `DeviceStatus`, the hardware id under `DeviceHardware`, name and
model under `DeviceInformation`, the IMEI under `Radio` and the BlackBerry IDs under `Perimeters`.
The PRD puts the packages under `DeviceSoftware`; real devices do not. `kind` is `personal` for
`Personal` and for `Default` (the default perimeter of a device without an enterprise one),
`enterprise` for `Enterprise`, and `unknown` for anything else; these are the names of the platform's
perimeter types. How a device writes an enterprise perimeter in this answer is **[UNVERIFIED]**.

`type` is one of `application`, `operatingSystem`, `radio`, `unclassified` (empty type) or
`unknown`; a capture of 232 packages contained only `application`, `os` and `radio`. `bbid`,
`serialNumber`, `imei`, `pin` and `fingerprint` identify the device or its owner.

`getDeviceCharacteristics` data (from `discovery.cgi`, a GET; reachable over USB and the tunnel, not a
plain Wi-Fi LAN) **[verified]**:

```json
{"pin":"2C1E7583","guid":"EDunxOMuoNpdOlvSIBPJD1dfcMs=","modelName":"STR100-1","osType":"BlackBerry 10",
 "platformVersion":"10.3.3.3216","imei":"354359061740215","batteryLevel":100,"developerMode":true,
 "tunnelUp":true,"screenWidth":720,"screenHeight":1280,"flashSize":14157856768,"sdCard":0,
 "blendPolicy":1,"fuseEnabled":true,"fuseInstalled":true}
```

The device numbers the PIN in decimal (`BbPin` `740193667`); bb-link formats it as the 8-digit
uppercase hexadecimal PIN (`2C1E7583`). `developerMode`, `tunnelUp`, `fuseEnabled` and `fuseInstalled`
are the device's `0`/`1` flags as booleans; `sdCard` and `blendPolicy` are the raw device values.
Fields this version does not read are ignored, and absent ones are omitted. This is the only source of
the PIN over the tunnel (`getPin`/`wipe.cgi` does not answer there), and `connect` merges it into
`device`.

### Bodies

The commands that return what a service answered (`listPimAccounts`, `findPimAccount`,
`getPimItems`) put the answer under `body`, and say in `bodyFormat` what it is. The
content type of the answer is not what decides this; the content is.

| `bodyFormat` | When                                                                                                                       | `body`                                                                           |
|--------------|----------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| `json`       | the answer starts with an opening brace or bracket and parses as JSON                                                      | the JSON itself, not a string; integers stay exact, decimals are read as doubles |
| `xml`        | the answer starts with `<` and is well-formed XML                                                                          | the XML as JSON, by the mapping below                                            |
| `text`       | anything else: not JSON or XML, **or only looking like it** (the answer of pimsync to a missing session is not valid JSON) | the text exactly as the device sent it                                           |

A body that cannot be read is never approximated: it stays the text it was. A client therefore checks
`bodyFormat` before it reads `body`.

The mapping of XML to JSON is deliberately plain, because XML has no types and no arrays:

- An element with neither attributes nor children is its text, a string (empty if there is none).
  Text is never turned into a number or a boolean: `"ErrorIdentifier":"7"`.
- An element with children is an object with a member per child, in document order.
- A name that occurs more than once among the children is an array of the values; a name that occurs
  once is the value itself, so one child cannot be told from a list of one.
- An attribute is a member whose name starts with `@`. If an element with attributes or children also
  has text, the text is the member `#text`.
- The name of the root element (`RimTabletResponse`) is dropped, so `{"Pairing":{…}}`.
- Comments are not part of the result.

The mapping loses the order of children with different names only where an array gathers equal ones.
`"raw": true` adds the answer as the device sent it (scrubbed of cookies, passwords and identifying
values) next to the result, and a trace has it too.

## 3. Events beyond the common ones

`{"id":1,"type":"device","hostname":"…","address":"…","connection":"WiFi","version":"…"}` — sent
by `listDevices` the moment a device is found. `connection` is `WiFi`, `USB` or `Simulator`.
`hostname` is what the detector reports: for a device without a password it is the operating system
name and version, not the network name **[UNVERIFIED]**. `version` is omitted when unknown. Clients
ignore event types they do not know.

## 4. Errors

Codes shared with bb-deploy: `AUTH_FAILED`, `AUTH_LOCKED`, `CLOCK_OUT_OF_SYNC`, `DEVICE_ERROR`,
`CONNECTION_FAILED`, `CONNECTION_LOST`, `COMMAND_FAILED`, `INVALID_ARGUMENT`, `IO_ERROR`,
`INVALID_REQUEST`, `UNKNOWN_METHOD`, `UNKNOWN_SESSION`, `INTERNAL`. Details are the same as there:
`failedAttempts`, `retriesRemaining`, `maxAttempts` for `AUTH_FAILED`; `failedAttempts` for
`AUTH_LOCKED`; `cause` (`refused`, `timeout`, `unknown_host`, `tls`, `reset`) for connection errors. A failed TLS
handshake while a client certificate was offered says so in its message and has `"certificate":true`
in the details: the device may have refused the certificate.
`DEVMODE_DISABLED` does not exist in bb-link.

Added by bb-link:

| Code                  | Meaning                                                                                                                                                                                              | Details                                                           |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| `UNEXPECTED_RESPONSE` | the device answered something this version cannot read                                                                                                                                               | `expected`, `received` (element names) or `status`, `contentType` |
| `PIN_MISMATCH`        | the `confirmPin` of a command that changes the device does not match the device's PIN; nothing was sent                                                                                              | none                                                              |
| `PASSWORD_REQUIRED`   | the device asked for a password during the login and none was given (`authentication` `none`); no answer was sent and no attempt was counted, so a client can ask for the password and connect again | none                                                              |

`COMMAND_FAILED` carries the device's own `ErrorDescription` as `message`, the element name as
`details.element` and, if the device gave one, its `ErrorIdentifier` as `details.errorIdentifier`.
Both fields are read from the answer's `Error` element, where devices put them, and from the answer
itself. Without a description the message is "The device reported an error". Any echo of the
request's cookie header is removed from it first.
Login failures are told apart by type, not by message text: `AUTH_FAILED` (rejected, or no cookie
issued), `AUTH_LOCKED`, `CLOCK_OUT_OF_SYNC` and `DEVICE_ERROR` are four different outcomes.

## 5. Sessions and retries

Login is the original tool's challenge login **[verified]**: `GET login.cgi?request_version=1`
answers with an `AuthChallenge` element (`Status` `PasswdChallenge`, `Challenge`, `Algorithm` 2,
`Salt`, `ICount` 6000, `FailedAttempts`, `RetriesRemaining`) and a `loginsession` cookie; the client
answers with `challenge_data=` from `PwCrypto.hashAndEncode`; the device replies with an `Auth`
element whose `Status` is `Success` (and a `dtmauth` cookie) or `Denied` (with `FailedAttempts` and
`RetriesRemaining`, and no cookie). The cookie is kept in memory only. The legacy login for algorithm
0 is not supported and reported as `DEVICE_ERROR`.

The success answer also contains the user name, the **password of the device's file share** and
the workgroup (`Smb`, `Credentials`, `User`, `Pwd`, `Workgroup`, `NetBios`). bb-link keeps them in
the session and returns them as described in section 2.

| Situation                                                                    | Behaviour                                     |
|------------------------------------------------------------------------------|-----------------------------------------------|
| wrong password, locked device                                                | never repeated                                |
| device rejects the session on the first request of a command (see section 6) | log in again once, send the command again     |
| device rejects the session later in a command                                | `AUTH_FAILED`; the next command logs in again |
| connection dropped, command only reads                                       | one new connection, one more try              |
| connection dropped, command changes the device                               | no retry; `CONNECTION_LOST`                   |

The HTTP client's own retry is switched off, because older versions repeat a POST by themselves.
Timeouts: 10 s to connect, 30 s without data **[UNVERIFIED for long operations]**.

## 6. Open points

Verified by a capture:

- The challenge login, the denial with its counters, and the `dtmauth` cookie.
- Cookie dates are in the Netscape format (`Wed, 14-Oct-2026 13:37:32 GMT`), so the clock-skew
  workaround of the original login can work.
- The envelope root is `RimTabletResponse` (some firmware uses `RIM_tablet_response`); a `Rev`
  element, if present, is skipped, and the payload is the first element that is neither wrapper.
  `Status` `Success` appears inside that payload element.
- The CGI answers also set `dtmauth`, with an expiry that is **30 minutes earlier** than the one
  from the login: the login's cookie expires 30 minutes after the device's "now", the CGI answers'
  cookie expires at the device's "now". On the captured device, whose clock is about five hours
  ahead of the host's, the date was still in the host's future, so the cookie stayed: every later
  request carried `dtmauth` and `loginsession` (**[verified]** in a trace). If the host's clock is
  ahead of the device's, a client that honours dates drops the cookie after the first command, and
  bb-link then logs in again for every command (checked only against the fake device). **[to watch]**
- An unreachable host gives `CONNECTION_FAILED` with `cause` `timeout` after about ten seconds.
- **A session with only the client certificate works on port 8443.** `connect` with
  `authentication` of type `certificate` read the properties and the PIN, and
  `listPimAccounts` and `findPimAccount` answered with status 0 (in the version that sent no login
  request in this mode). The trace shows no login request, no
  `Cookie` header on any request and no `Set-Cookie` in any answer: whatever authenticated these
  requests was not a cookie. The controls in section 10 show that it was the certificate.
- **Port 8443 accepts the password login without a client certificate.** `connect` with `"port":8443`
  succeeded, and so did the reads that `connect` makes (`getDeviceProperties` and `getPin`), over the
  USB address. So both ports serve the same programs, and a valid `dtmauth` session is enough on
  either.
- **The file share can be used without the device password.** The credentials returned by a
  certificate-only `connect` (user `dtm`, the password of that login) were used to mount the device's
  share over the USB address, with no device password involved anywhere **[verified by hand]**.
- **A login with the client certificate offered needs no password and issues no cookie.**
  `GET login.cgi?request_version=1` over port 8443 with the certificate was answered `Success` at once:
  no challenge, no `Set-Cookie`, and the share credentials in the answer. bb-link accepts a cookie-less
  success only when a certificate was offered, since a success without a cookie and without a
  certificate is not a login. The password of `certificateAndPassword` is therefore never consulted
  when the device accepts the certificate.
- **`authentication` `none` against a device that has a password stops at the challenge**
  (`PASSWORD_REQUIRED`): the trace holds only the first login request, and the device's counter still
  reads `FailedAttempts` 0, `RetriesRemaining` 5, so no attempt was counted.
- **A refused certificate** (an unrelated one) ends the handshake and is reported as
  `CONNECTION_FAILED` with `cause` `tls`, `certificate` `true` and the message that a certificate was
  offered, as designed.
- **Over Wi-Fi the password login works, but the programs behind it do not answer.** On
  `192.168.1.82`, port 443: the login (challenge, answer, `Success` with a `dtmauth` cookie) succeeds,
  and the answer contains **no share credentials** (the `Smb` block of the USB login is missing).
  The first command then fails: the POST to `dynamicProperties.cgi`, sent with the session cookie, is **closed without
  any HTTP answer** (`CONNECTION_LOST`, "The target server failed to respond"), and
  again after the one retry. So `connect` cannot be established over Wi-Fi unless `deviceInfo` is `false`: its first
  step reads the
  device properties. Port 8443 **refuses the connection** on the Wi-Fi address. A read-only map of
  the programs (section 10) shows that only `login.cgi` and `appInstaller.cgi` answer over Wi-Fi.
- **The certificate changes nothing over Wi-Fi.** `connect` with the type `certificate` to port 443 of
  the Wi-Fi address: the login is answered with a **password challenge** (over USB on port 8443 the
  same request is answered `Success`), bb-link warns that the device asked for a password and carries
  on, and the properties request is closed without an answer, twice. No password was sent. The TLS
  handshake of port 443 on the Wi-Fi address shows no request for a client certificate, whereas
  port 8443 asks for one, so the certificate seems to be a property of the 8443 listener, which
  Wi-Fi does not have (it refuses connections). Port 443 over USB with a certificate has not been tried.
- **Port 443 over USB does not honour the certificate either.** `connect` with the type
  `certificate` to port 443 of the USB address: the login is answered with a password challenge (bb-link
  warns and carries on), and the properties request is then answered with an **error element**
  (`COMMAND_FAILED`, element `DynamicProperties`), not with the empty body that a request with a
  false cookie gets. The certificate is a property of the 8443 listener. What the error said was not
  shown, because the description was not read from the nested `Error` element (fixed); the trace of
  that run has it.
- **A successful login resets the counter of failed attempts**: the challenge showed
  `FailedAttempts` 1 and `RetriesRemaining` 4 before a login, and 0 and 5 after one.
- **A rejected session**: a request with a made-up cookie to `dynamicProperties.cgi` is answered with
  status 200 and an empty body (0 bytes; the response has no other headers worth noting). bb-link
  treats status 200 with an empty body as a lost session. A request can declare that an empty answer
  is its normal success (`expectingNoBody`), which a command that changes the device and is answered
  with nothing, such as a reboot, must do; otherwise it would be sent twice. Status 401/403 and the
  login elements `AuthChallenge`/`Auth` are kept as additional signs **[UNVERIFIED]**.
- **The authorization error is a lost session, with or without the full stop.** A request that has
  only the `loginsession` cookie (no `dtmauth`) is answered by `dynamicProperties.cgi` with
  `Status` `Error`, `ErrorIdentifier` 1011 and `ErrorDescription` `Error - authorization`;
  `pairing.cgi` answers `Error - authorization.` with number 6. bb-link compares the description
  without a final full stop and without regard to case, so both are a lost session: a password
  session is renewed once on the first request of a command, and a session that offered only a
  certificate ends with `AUTH_FAILED` ("The device did not accept the client certificate").

Still unverified:

1. **The text of the PIN.** The PIN is taken as the text after the last `X` in `<PIN>`. The result (eight hexadecimal
   digits) looks right on a real device, but the capture masked the raw text, so
   it is not known whether an `X` is there at all.
2. **Error shape.** `<Status>Error</Status>` with `<ErrorDescription>` inside the payload element.
3. **`wipe=getpin` is read-only.** It answered with `Status` `Success`, the PIN and an empty `Log`,
   and changed nothing visible; do not take that as proof for the other `wipe.cgi` commands.
4. **A device without a password**, and what `listDevices` reports as `hostname` for it.

## 7. Planned, not implemented

`setDeviceProperty`, `getBackupInventory`, `backup`, `restore`, `reboot`, `dumpLogs`, `startRtas`,
`setPin`, `reSign`, `wipe`, `factoryWipe`.

Installing (`installPackages`) is done and verified on a device; removing (`uninstallPackages`) is
written and waits for its first capture (section 12.9). Reading a bar (`inspectPackage`) is done.

Not a priority (section 9 and section 10 say what is known):

- **pimsync**: writing (`create_account`, `update_account`, `delete_account`, `put`, `delete`,
  `summary`, `done`) and photos, which the device returns as image bytes.
- **enterprise** and **metadata**: the parameters are unknown.
- **`backup.cgi`**, **`appInstaller.cgi`** (named by the research, not seen). `discovery.cgi` is
  implemented as `getDeviceCharacteristics` (section 2).
- Fields of `DeviceSoftware` that are not parsed yet: `ApplicationFlashSize`, `DataFlashSize`,
  `ReservedFlashSize`, `HasPassword`, `DisplayLocale`, `LegalLocale`, `BackupCapability` (2 on the
  captured device; probably relevant to the backup work).

## 8. Fixtures and traces

`./run-fixtures.sh` replays `fixtures/<method>--<case>.in` through `bb-link -parse <method>` and
compares with `<method>--<case>.expected` (`--update` rewrites them). A method with an argument is
written with a dot (`getPimItems.contacts`, whose argument is the service). A recording with several answers separates
them with a line
containing only `---`; a recording without one is a single answer, byte for byte. A recording does
not keep the content type, so replayed results have none.

`-trace <directory>` writes one file per exchange (`0001.txt`, …). Cookies, the password hash, PIN,
serial numbers, BlackBerry ID and fingerprints are replaced by `***`. A trace can be shared and
turned into a fixture by copying the response body.

Use one directory per scenario (`-trace captures/01-connect-ok`), because every run numbers its
files from `0001` again. The request lines show which cookies were in the jar (`>>> Cookie:
dtmauth=***`), never their values. `listDevices` is not traced: the scan is done by the original
tool's own HTTP code.

## 9. Personal information sync (pimsync.cgi)

`listPimAccounts`, `findPimAccount` and `getPimItems` send one GET to `pimsync.cgi` and return what
the device answered. Only reading commands exist; none of them changes anything as far as the
research says, so they are retried once after a dropped connection like the other reads.

```
{"id":2,"method":"getPimItems","params":{"session":"s1","workstationId":"…","service":"contacts",
 "updatedTime":"…","limit":50,"dirtyOnly":true,"localOnly":true,"offset":100}}
```

| Method            | Parameters                                                                                                                                                                                        | Query sent                                                                                                                                                                        |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `listPimAccounts` | none                                                                                                                                                                                              | `command=list_accounts`                                                                                                                                                           |
| `findPimAccount`  | `workstationId`                                                                                                                                                                                   | `command=find_accountId&workstation_id=…`                                                                                                                                         |
| `getPimItems`     | `workstationId`, `service` (`contacts`, `events` or `calendars`), optional `updatedTime`, `limit` (at least 1), `dirtyOnly`, `localOnly`, `offset` (not negative; only together with `localOnly`) | `command=get&workstation_id=…&service=…`, then the filters that are set, in the order `updated_time`, `limit`, `dirty_only`, `local_only`, `offset`; the flags are sent as `true` |

Parameters are checked before anything is sent, and the values are percent-encoded. The names of
the parameters and commands come from the research **[RESEARCH]**; the format of `updatedTime` and
of an `workstationId` is not known, so both are sent as given.

The result:

```json
{"command":"listAccounts","httpStatus":200,"contentType":"application/json","code":0,"message":"Success","bodyFormat":"json","body":{"version":"1.0","status":{"code":0,"msg":"Success"},"data":[]}}
```

- `contentType` is the `Content-Type` header of the answer (omitted if the device sent none), so a
  client can tell what `body` is: pimsync answers JSON-like text, whereas pairing answers XML.
- `body` is the answer as JSON when the device's text is JSON, embedded directly, and the text as
  sent when it is not (see "Bodies" in section 2). The shape of the data inside is not known; a
  client reads it itself. It can contain personal data.
- `code` and `message` are the status that every answer starts with (`"status":{"code":N,"msg":"…"}`),
  read from the body; they are omitted if the body has none. `service` is omitted for the commands
  that have none.
- A status of 1001 (invalid parameters), 1013 (unauthorised) or 1015 (unsupported) means the device
  said no. The command then ends with `COMMAND_FAILED` (the device's message, `details.code`), and
  the result with the body is still sent **before** the final event, so a client can read what the
  device said. 1013 has been seen **[verified]**; 1001 and 1015 are **[RESEARCH]**. Any other
  status is a result with `"ok":true`.
- An empty body is an answer: a request without a `command` is answered with status 200 and nothing **[verified]**. A
  lost session is recognised by the text `DTM NOT LOGGED IN` **[verified without
  a cookie, not with an expired one]**, after which bb-link logs in again and sends the request
  once more.

Answers with a session **[verified]**, from a device that has no PIM accounts, using a workstation
id that the device does not know:

| Method                                               | Answer                                                                                                                                                    |
|------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `listPimAccounts`                                    | `{"version":"1.0","status":{"code":0,"msg":"Success"},"data":[]}`                                                                                         |
| `findPimAccount`                                     | `{"version":"1.0","status":{"code":0,"msg":"Success"}}`: a success without a `data` member                                                                |
| `getPimItems` (contacts, events and calendars alike) | `{"version":"1.0","status":{"code":1013,"msg":"received HTTP 401/403 from server"},"service":"contacts"}`: status 1013, no `data`, and the service echoed |

So `Success` is status 0. Status 1013 here is **not** a lost session: the session works (the other
two calls succeeded on it), and the text `DTM NOT LOGGED IN` is missing. It reads as the device
refusing the request itself, most likely because the workstation is not registered; the account
list is empty. That is a guess, **[UNVERIFIED]**: it would be settled by registering a workstation,
which needs `create_account`, a command that writes to the device and that bb-link does not have.

Answers to a request without a session **[verified]**:

```
{"version":"1.0","status":{"code":1013,"msg":"received HTTP 401/403 from server"},"data":DTM NOT LOGGED IN}
```

That is not valid JSON (the value of `data` is not quoted), which is why the status is also read from
the text when the body does not parse.

### The other three services

`enterprise.cgi`, `metadata.cgi` and `pairing.cgi` have no command yet. What they answer, all with
HTTP 200 and `Status` `Error` **[verified]**:

| Service    | Without a session                              | With a session, GET without parameters                               | It wants       |
|------------|------------------------------------------------|----------------------------------------------------------------------|----------------|
| enterprise | `ErrorIdentifier` 30, `Exception: HTTP_COOKIE` | 30, `Exception: QUERY_STRING`                                        | a query string |
| metadata   | 9, `Error - authorization.`                    | 1, `Error - invalid request, no parameter.`                          | a parameter    |
| pairing    | 6, `Error - authorization.`                    | 3, `Error - HTTP GET requests have been disabled. ` (trailing space) | a POST         |

The parameters of enterprise and metadata are not known, so there is nothing to type yet.

## 10. Research notes (not verified by bb-link)

**Where a later section says otherwise, the later section is right:** section 11 for pairing and section 13 for
certificates.

Two research documents were supplied: an analysis of the decompiled handler of `pimsync.cgi`, and a
requirements document for `pairing.cgi`. Everything here is **[RESEARCH]**: it has not been checked
against a device by bb-link, except where a capture is named.

### pimsync.cgi

The handler checks the `dtmauth` cookie (or a client certificate) and otherwise answers error 1013
with `DTM NOT LOGGED IN` **[matches the capture in section 9]**. Parameters come from the query
string or from form data:

| Parameter                                                     | Meaning                                       |
|---------------------------------------------------------------|-----------------------------------------------|
| `command`                                                     | required: the operation                       |
| `service`                                                     | `contacts`, `events`, `calendars` or `photos` |
| `workstation_id`                                              | the client's identifier                       |
| `updated_time`, `limit`, `offset`, `dirty_only`, `local_only` | filters and paging for `get`                  |
| `path`                                                        | the asset to serve for `photos`               |
| `debug`                                                       | more logging                                  |

Commands that look read-only: `list_accounts`, `find_accountId` (needs `workstation_id`), and `get`
(needs `workstation_id` and `service`); these three are implemented (section 9). Commands that change data on the
device: `create_account`,
`update_account`, `delete_account`, `put`, `delete`, `summary`, `done`. A request without `command`
falls to a default branch, which is consistent with the empty answer in section 9. Error codes:
1001 invalid parameters, 1013 not logged in (seen both with and without a session, see section 9),
1015 unsupported service or operation.

`get` with `service=photos` answers with the image itself (`Content-Type: image/jpeg`, with a
`Content-Length`). The body of a bb-link result is UTF-8 text, which cannot carry an image, so
`getPimItems` does not accept `photos`; a command for photos needs a binary transfer.

### pairing.cgi

Verified by a capture: a GET is refused (section 9). The research says the endpoint accepts a single
POST, behind a session cookie or a mutual-TLS client certificate (port 8443), with form fields
`certfile` or `certdata` (the client's certificate; one is required), `deviceid` (required, stored in
capitals), and optionally `ecoid`, `devicename`, `devicetype`, `btmac` and `flags`. The device
parses the certificate as X.509 and stores it; no chain, CA or expiry check is made. Sending the
same certificate again is a success (`Device already paired`); anything else that is not a success
is reported as HTTP error 7.

This **changes the device**: the certificate is written to its certificate store and to a database,
and it decides which clients the device accepts later. `pairDevice` sends it (section 11).

### Other statements of the research

- The CGI programs (`discovery.cgi`, `login.cgi`, `backup.cgi`, `metadata.cgi`, `appInstaller.cgi`,
  `update.cgi`, `pimsync.cgi`, `pairing.cgi` and others) are described as served on the
  USB-reachable listeners only (ports 80 and 8443), and as answering 404 on port 443 of the Wi-Fi
  address. What was found: port 8443 is closed on the Wi-Fi address (**matches**); port 443 there
  serves `login.cgi` (also with a password login, which succeeds) but closes a POST to
  `dynamicProperties.cgi` without answering, so the claim that the programs are USB-only is **at least
  partly right**, and the claim of a 404 is not what happens (the connection is closed). No share
  credentials are handed out over Wi-Fi, in line with the file share being bound to the USB address.
- The file share (SMB, ports 445 and NetBIOS) listens on the USB address only, so the `smb`
  credentials of a `connect` are only useful against the link-local address.
- Content transfer over Wi-Fi is a different protocol (a multiplexing protocol inside DTLS on a
  dynamic UDP port, found by mDNS), not these CGI programs. It is out of scope for bb-link.
- `discovery.cgi` returns device characteristics (model, operating system, IMEI, partition map). It
  is now implemented as `getDeviceCharacteristics` (section 2), and `connect` merges it into `device`.

### The client certificate (research, and what the laptop's certificate shows)

The device **issues** the client certificate; the client does not bring one. The research describes a
chain of three: a root `CN=BlackBerry [<tag>]` (self-signed, held by the host, in the Windows store
`RIMP2PSTORE`), a device CA `CN=BB10/<hash>` (held by the device, which signs the leaves) and the leaf
`CN=BBCGIAUTH [<tag>] <HOSTNAME>, serialNumber=<n>`, valid about 37 days. The device generates the key
pair and signs the leaf in one Thrift call, `GenerateP2PCertificate` (TunnelManager service), which
takes only a common name and returns the private key and the certificate. bb-link has no Thrift
channel, so **it cannot obtain a certificate; it uses one exported from a laptop that BlackBerry
Link paired with the device.** **[RESEARCH]**

**Superseded by section 13 [verified]:** the device signs a certificate request that is posted to `login.cgi`,
so bb-link makes its own, and needs neither Thrift nor a laptop.

What the exported certificate shows **[verified]**: subject `CN=BBCGIAUTH [2A3B4C5D]
<computer name>, serialNumber=29717` (the eight digits in brackets equal this device's PIN), issuer
`O=Research In Motion, OU=BlackBerry, CN=BB10/<hash>` (not self-signed), RSA 2048, signed with
SHA-1, valid for 37 days (it has to be exported again after that), and as PEM longer than the 1024
bytes that `certdata` is said to take. Verifying it against the root from `RIMP2PSTORE` fails
(`unable to get local issuer certificate`): the issuer is the device's own CA, which the host does not
keep.

### Port 8443 (verified with curl and bb-link)

On the USB address, port 8443 uses the same old TLS as port 443 (TLSv1, `AES256-SHA`) and the same
self-signed server certificate. During the handshake the server **asks for a client certificate**
(`Request CERT`), but the handshake completes when the client sends none. A GET without a cookie
and without a certificate was answered `200 OK` with no body at all, unlike port 443, where
`pairing.cgi` answers a request without a session with an XML `Error - authorization.`.

So a client certificate is requested, not required, at the TLS layer. Whether a certificate that is
presented but not trusted is refused, and which CA names the server lists, has **not** been checked.

### Controls for the certificate-only session (verified)

Three clients were compared on port 8443 of the USB address:

| Client                                                                                       | Result                                                                                                                                                |
|----------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| none: no certificate, no cookie                                                              | the handshake completes; requests are not authenticated (a GET to `pairing.cgi` was answered `200 OK` with no body)                                   |
| the certificate exported from the laptop                                                     | requests are authenticated (the session above)                                                                                                        |
| an **unrelated** certificate, and a **self-signed** one with a `BBCGIAUTH [<PIN>] …` subject | the handshake is **refused**: `CONNECTION_FAILED`, `peer not authenticated`, `cause` `tls` (the old HttpClient reports any failed handshake that way) |

So the device does tell certificates apart: it accepts a client that presents none (and answers it as
unauthenticated), it refuses a certificate that it does not trust, and it authenticates the one that
its own authority issued. The subject alone is not enough: a self-signed certificate with the right
subject was refused. (It also differed from the real one in its signature algorithm and extensions,
so the issuer is the most likely reason, not a separately proven one.)

**[Superseded by section 13]** The consequence was believed to be that **bb-link cannot make a certificate of its own**;
the client-side research shows that it can, with a certificate request. A replacement client would
have to obtain one from the device, through the Thrift call `GenerateP2PCertificate` (section 10),
for which bb-link has no channel. Until then it uses the exported certificate, which expires on
2026-11-13 and has to be exported again from the laptop after that.

An `openssl s_client` run with the real certificate was answered like one without it. The most likely
reason is that `s_client` sends a certificate only when its issuer is on the list of authorities the
server names, and bb-link sends it regardless; this is an inference, and the list itself has not been
read (`openssl s_client … | sed -n '/Acceptable client certificate CA names/,/Client Certificate
Types/p'`).

### What answers over Wi-Fi (verified)

A GET to a program that is served is answered even without a session (with an error or an empty
body); a program that is not served closes the connection or answers 404. This loop shows the
difference, and was run against the Wi-Fi address (port 443):

```
for program in login dynamicProperties pimsync pairing metadata enterprise appInstaller discovery update backup wipe reset support; do
  printf '%-18s' "$program"
  curl -sk -o /dev/null -w '%{http_code} %{size_download} bytes\n' --max-time 6 "https://192.168.1.82/cgi-bin/$program.cgi" || echo "no answer"
done
```

| Program                                                                                                                          | Over Wi-Fi (443)                                |
|----------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------|
| `login`, `appInstaller`                                                                                                          | answered (`200`, empty body without parameters) |
| `dynamicProperties`, `pimsync`, `pairing`, `metadata`, `enterprise`, `discovery`, `update`, `backup`, `wipe`, `reset`, `support` | the connection is closed without any answer     |

So the programs that bb-link manages the device with are served over USB only; over Wi-Fi the device
answers the login (used by discovery and by password logins) and `appInstaller.cgi` (the program of
bb-deploy's installs). A POST with a valid session cookie to `dynamicProperties.cgi` was closed as
well, so this is not a matter of authentication.

BlackBerry Link does work with the device over Wi-Fi using the same certificate, but not through these
programs. A Wireshark capture of Link shows the host announcing itself with ARP and moving files over
DTLS, on ports that change from one transfer to the next, with the device properties present in that
traffic **[observed]**. The version the capture names for DTLS needs checking: DTLS has versions 1.0,
1.2 and 1.3, and no 1.1. bb-link has no implementation of that protocol.

### Open questions

- **What works over Wi-Fi?** All captures used the USB address (`169.254.x.x`). `connect` to the
  Wi-Fi address has not been tried. The first test is `connect`, then `getDeviceProperties`,
  `getPin` and `listPimAccounts` to that address, and a comparison of the answers with the USB ones.
- **What is pairing for, if the password login works everywhere?** Both ports accept the password
  login, and, as far as the first test shows, the certificate alone works on 8443 as well. Pairing is
  therefore not needed for what bb-link does now. The research describes pairing as what
  lets a client be recognised later by its certificate, which suggests it is for logging in
  without the password, or for what the device allows over Wi-Fi. That is an inference, not a
  finding.
- **Does pairing grant permissions over Wi-Fi?** BlackBerry Link works over Wi-Fi after a device is
  paired, but nothing says whether a paired device accepts more over Wi-Fi than a client with only a
  `dtmauth` session. If the programs answer differently before and after pairing, that is the
  evidence; the research suggests that acceptance depends on a client certificate that is presented
  with every connection (mutual TLS), which a password login does not use.

## 11. Pairing through pairing.cgi (pairDevice)

Pairing is what makes the phone list the computer in Device Connections. It is the second of the two steps that
BlackBerry Link takes, and **it is separate from the certificate** of section 13 **[verified]**: a phone that
signed a certificate for us, and logged us in with it, did not list the computer until it was paired. The two
sessions that asked for the certificate, before and after the pairing code was removed from bb-link, sent the same
requests and neither called `pairing.cgi`. The order in which Link does them is certificate first, pairing a
second later (a certificate made at 14:25:08 UTC, and the trusted authority written at 14:25:09), and `bblink pair`
does them in that order.

**The request.** `POST /cgi-bin/pairing.cgi` over a session, a form with two fields:

| Field      | Value                                                                                                                                                                                                                 |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `deviceid` | the identifier that the computer gives itself: `01` and 32 hexadecimal digits in capitals, as Link's is; a new one for each pairing                                                                                   |
| `certdata` | the computer's authority as PEM: self-signed, `CN=PC issuer <16 hex digits>`, EC P-521, signed `ecdsa-with-SHA1`, critical `CA:TRUE`, valid ten years from seven days before, 822 bytes, made like the one Link makes |

The binary of `pairing.cgi` reads these two fields and no others **[RESEARCH]**, and a pairing with only these two
fields was seen to work **[verified]**: `type` is not needed. Updates 36 to 39 also sent `type=insert`, and update 39
sent `devicename` when asked.

**Open issue: the name in Device Connections.** The phone lists the computer as "Desktop-" and the last four hexadecimal
digits of the `deviceid`, and what decides that name is not known. The common name of the certificate in the layout of
the Windows laptop (`BBCGIAUTH [PIN] <host>`, update 40, section 13.1) **did not change it [verified]**. The
`devicename`
field was sent in update 39 when asked; whether it was used in the runs that kept the name is not recorded, so its
effect is not known. Nothing more is to be tried until the binary of the device has been decompiled; what to look for
there is where `pairing.cgi` takes the name it stores, whether it reads `devicename` at all, and where Device
Connections reads the name from.

**The answer.** HTTP 200, and **the device's own authority** (`CN=BlackBerry [<tag>]`, a different one on each phone)
as the text of `<Pairing>`, in a CDATA section, with **no `Status`**. bb-link takes that as the success, and keeps the
authority. The device stores the computer's authority in an OpenSSL hashed directory, `/var/bp2p/trustedpeers/` (a
file named by the SHA-1 of the certificate, and a link named by its subject hash). Pairing is an exchange of
authorities, for the trust of the tunnel between a computer and a phone.

**What bb-link keeps**, in `devices/<PIN>/`: `authority_cert.pem`, `authority_key.pem` (a credential: owner only, never
in a result, a log or a trace), `device_authority.pem` and `pairing.json` (`deviceId`, `commonName`,
`createdAt`). Nothing is written unless the device has answered with its authority, and the key is moved into place
first. A pairing that is on disk is not replaced without `replace`; a replaced pairing has a new identifier, and the
old one stays on the phone.

**What it does not do.**

- It gives no certificate for port 8443. That server is started with `-N /var/cgiauth/cgiauth_cert.crt`, the phone's own
  authority `BB10/<hash>`, and a certificate of ours that did not chain to it was refused in the TLS handshake
  (`peer not authenticated`).
- `type=query` and `type=delete` answer error 7, `insert certificate failed`, as an insert without a certificate does.
  A pairing can be removed only on the phone, in Device Connections, so bb-link has no `unpair`.

**The error numbers of `pairing.cgi`**, from an analysis of its binary; the ones marked were seen on a device:

| Number | Meaning                            | Seen                                      |
|--------|------------------------------------|-------------------------------------------|
| 1      | invalid request, no parameter      |                                           |
| 2      | invalid request, unknown parameter |                                           |
| 3      | HTTP GET disabled                  | the text, in answer to a GET              |
| 4      | get certificate failed             |                                           |
| 5      | get result failed                  |                                           |
| 6      | authorization                      | yes: a request without a session          |
| 7      | insert certificate failed          | yes: a refused request, `query`, `delete` |
| 8      | delete certificate failed          |                                           |
| other  | unknown error                      |                                           |

Both answers are fixtures: `pairDevice--synthetic-authority-accepted`, which has the shape of the real answer but a
made-up
authority (the tag of a real one identifies the phone), and `pairDevice--real-insert-failed`.

**One thing seen on the way.** A request whose whole POST body was 1129 bytes got an empty answer and left no line in
the
phone's log, while bodies up to 1125 bytes were answered. It looks like a limit near 1128 bytes on a request body,
**from two samples**; the request for the pairing is about 950 bytes.

**Not known.** What the phone does with a second pairing of the same computer, which the script does not make (every
pairing has a new
identifier); what else the authority in the trust store is used for; and whether a pairing can be removed from the
computer's side at all: `type=delete` answers error 7, and there is nothing else to go on. bb-link has no `unpair` until
there is.

## 12. Installing and removing packages (update.cgi)

### 12.1 State

| Piece                                                        | State                                                                                                                                                                            |
|--------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `inspectPackage` (read a bar, optionally check its manifest) | **done**; in `bb-core` as `com.jbraddockm.core.bar`; **verified in the native image**, reading and checking, on `app.bar`                                                        |
| the lost-session rule for `Error - authorization` (1011)     | **done** (section 6)                                                                                                                                                             |
| `installPackages`                                            | **verified on a device**: `app.bar` was installed through a certificate-only session (section 12.8); error answers, a password session and several packages are not yet captured |
| `uninstallPackages`                                          | **verified on a device**: the start, the removal of one and of two packages, and the end (section 12.9); an error answer of a removal has not been provoked                      |
| OS and radio installs                                        | later; read and classified now, refused when installing                                                                                                                          |

### 12.2 What the research says **[RESEARCH]**

Two documents: the Sachesi API reference (Oct 13, unchanged since) and `UpdateCGI.md` (Oct 16, a
second revision with the response element names). They agree on the protocol; `UpdateCGI.md`
supersedes where they differ.

- `update.cgi` runs one stateful conversation, said to be cookie-tracked by an `update_cookie` that
  the client must echo. The device sets a cookie named `update` instead, and nothing needed it
  echoed (section 12.8). Everything is a form POST except
  the file, which is a raw `application/octet-stream` body with `Content-Length`, never multipart.
- **Install:** `mode=bar|os&size=<total bytes>` gives `UpdateStart`; each file is `POST
  update.cgi?type=bar|radio` and gives `UpdateSend`, then an empty form POST is repeated while
  `UpdateProgress` says `InProgress` (with `Progress` 0 to 100); `Success` moves on to the next file
  or to `status=success`, which gives `UpdateEnd`. A failure is `Status` `Error` with
  `ErrorIdentifier` and `ErrorDescription` in whichever element the stage was.
- **Uninstall:** `mode=app&size=0` gives `DeleteRequest` (or `UpdateStart`); each package is `type=bar&packageid=<id>`
  and gives `DeleteProgress`; `type=bar&status=success` ends it. Applications only.
- An OS install ends with a device-driven switch and reset.
- Authentication: the research says `update.cgi` reads the `loginsession` cookie plus the client
  certificate's subject, not `dtmauth`, and so should work over Wi-Fi; header `User-Agent:
  QNXWebClient/1.0`; no development mode needed.

### 12.3 What we know on the device

- Over Wi-Fi (port 443), `update.cgi` answers **nothing** to GET and HEAD, exactly like a path that
  does not exist, while `login.cgi` answers. This **contradicts** the research's hope that a Wi-Fi
  session drives it (section 10). Over USB it has not been probed (no baseline yet).
- The test bar `app.bar`: kind `application`, id `testDev_kberryworld76ddd87b`, version `0.1.0.188`,
  `armle-v7`, 201 entries, 5.3 MB. Its manifest (38 KB) has an `Archive` section first and the
  `Package-*` attributes in the **second** section; `Application-Development-Mode: true`; **no
  signature**: `META-INF` holds only the manifest, and the manifest lacks `Package-Author-Certificate-Hash` and
  `Package-Issue-Date`. BlackBerry's library accepts it under its unsigned rules and refuses it
  under its signed rules for exactly those two attributes. It was **not** refused:
  the device installed it (section 12.8).
- Reading the manifest with the library loads no resources; checking it loads the library's XML rule sets
  (`com/blackberry/tools/bar/rulesets/*.xml` and `regex.properties`) through a DOM parser. `bb-core`
  carries the resource entry for the native image (`reachability-metadata.json`), and that entry
  was enough: `inspect` and `inspect --validate` both work in the native build **[verified]**. The
  packager's extra native-image arguments belong to the QNX packager classes, which are not used here.

### 12.4 Decisions taken

1. The bar logic is in `bb-core` (`com.jbraddockm.core.bar`), so other modules can use it. BlackBerry's
   types stay package-private; the public types are `BarInspector`, `BarInfo`, `BarKind`,
   `BarValidation` and `InvalidBarException`.
2. Only `META-INF/MANIFEST.MF` is read from a bar, at most 8 MiB of it, whatever the size of the bar.
3. Classification: `application` is an application; `system`, or a `patch` whose
   `Patch-Package-Type` is `system`, is firmware, which is the radio if `System-Type` is `radio` (or
   the file name contains `radio`) and the operating system otherwise; every other package type is
   `other`.
4. A manifest is `signed` if it carries `Package-Author-Certificate-Hash`; the signature itself is
   never checked. The rules for a signed or an unsigned package follow from that.
5. A conversation stays a pure state machine. What it wants done around a request (report progress,
   log a note, wait between two polls) travels with the request as `StepEffect`s, and the runner
   does it. A file is sent by a new request type, `Upload`: the raw body streamed from disk with its
   length, never held in memory, and its trace line shows the name and the size, never the content.
   Each request can ask for its own read timeout (10 minutes for the steps of an installation,
   30 seconds elsewhere). A request that repeats, such as a poll, is logged once.
6. Types ahead of the work: the update mode (`bar`, `os`, `app`), the upload kind (`bar`, `radio`), the
   status (`InProgress`, `Success`, `Complete`, `Error`) and the response elements are enums, and
   the install and uninstall conversations are state machines like the existing ones.
   OS and radio images are read now and refused with a clear message until they are supported.

### 12.5 Open questions

| #  | Question                                                                                                                        | Why it matters                                                                  | My recommendation                                                                                                                                                                                          | Status                                                                                                                                                                        |
|----|---------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | Is there a bar that can be installed successfully (signed, or accepted from a development build)?                               | `app.bar` may be refused; without a success we cannot capture the whole install | take the refusal as the first capture, then use a signed bar                                                                                                                                               | **answered**: `app.bar`, an unsigned development build, was installed; no signed bar is needed (on this device)                                                               |
| 2  | The exact XML of `UpdateStart`, `UpdateSend`, `UpdateProgress`, `UpdateEnd`, `DeleteRequest`, `DeleteProgress` and their errors | ordering, and whether `UpdateEnd` carries a `Status`, change the parsers        | capture each (section 12.6); until then parse leniently and keep the whole answer as JSON                                                                                                                  | **partly answered**: the answers of a successful installation are captured (section 12.8); the answers to refusals are not                                                    |
| 3  | How does the device recover after a failed or interrupted install?                                                              | a new start may be refused while the old conversation lives                     | capture scenario 8; no abort is sent until one is known                                                                                                                                                    | open                                                                                                                                                                          |
| 4  | The body of the "empty form" poll and the polling interval                                                                      | the conversation may hang or be refused without them                            | an empty form POST (a body of zero bytes), every second; adjust from captures. The agent needs no decision: the transport sends `User-Agent: QNXWebClient/1.0` on every request, as the original tool does | **answered**: an empty form POST every second works; the answers came after about 2 s and 7 s                                                                                 |
| 5  | Which cookie does `update.cgi` need, and does a certificate-only session work?                                                  | on port 8443 the certificate login issues no cookie at all                      | test password on 443 first, then each other authentication type                                                                                                                                            | **partly answered**: a certificate-only session on port 8443 works, with no cookie of any kind; a password session is untried, and the device's cookie `update` is not needed |
| 6  | Can `update.cgi` be reached over Wi-Fi?                                                                                         | the research says yes, our probes say it is closed                              | probe USB first; if Wi-Fi is wanted, `connect` must not require the properties request there (it fails on Wi-Fi)                                                                                           | open                                                                                                                                                                          |
| 7  | Should installing or uninstalling need `confirmPin`?                                                                            | the guard protects commands that change who the device trusts                   | no; it stays for the destructive commands                                                                                                                                                                  | **decided**: no, neither for installing nor for uninstalling                                                                                                                  |
| 8  | Is cancelling a running install needed?                                                                                         | there is no cancel in the daemon; closing the connection aborts                 | no, not in the first version; progress events yes                                                                                                                                                          | open                                                                                                                                                                          |
| 9  | What happens to a package id on uninstall that the device does not list?                                                        | a wrong id may end the conversation with an error                               | refuse it before sending, naming the parameter; refuse OS and radio packages                                                                                                                               | **decided**: yes; `check: false` skips the check, for diagnostics                                                                                                             |
| 10 | Is BlackBerry's validation only information?                                                                                    | its rules may differ from the device's                                          | yes: opt-in with `validate`, never a reason to refuse an install until compared with the device's answers                                                                                                  | assumed                                                                                                                                                                       |
| 11 | Which name decides that firmware is the radio, and how are patches installed?                                                   | the rule about the name is the research's and unclear                           | keep it for firmware only; settle with real firmware bars when OS and radio are supported                                                                                                                  | open                                                                                                                                                                          |
| 12 | Is a lack of free space a refusal? (Sachesi aborts if `total x 1.5 > FreeApplicationSpace`)                                     | the rule may be too strict                                                      | refuse with the figures in the error; skip the check with a warning if the device gave no figure                                                                                                           | open                                                                                                                                                                          |
| 13 | Several bars in one request: all or nothing?                                                                                    | the first can be installed and the second fail                                  | install in order, stop at the first failure, and report what was installed in the error                                                                                                                    | open                                                                                                                                                                          |
| 14 | How long may a step take?                                                                                                       | the device processes after the upload; the transport times out after 30 s       | per-request timeouts: long after an upload, shorter for polls; set from captures                                                                                                                           | **partly answered**: 5.3 MB uploaded in about 2 s and was installed in about 10 s; no step came near its limit                                                                |
| 15 | Should an install read the properties again to confirm the package is there?                                                    | confidence against an extra request                                             | no; `getDeviceProperties` does it                                                                                                                                                                          | **decided**: no                                                                                                                                                               |

### 12.6 Captures needed before the conversations are written

Each is a trace (`captures/`) of one scenario, with a note of the device before and after.

1. USB baseline: GET `update.cgi` with and without a session (answers at all? which element?).
2. A successful install of a small application. **Done** with `app.bar` (unsigned, development build) through a
   certificate-only session on port 8443 (section 12.8); a password login on port 443 is still to do.
3. The same bar installed again (same version).
4. A higher and a lower version of the same package.
5. A bar that the device refuses, with its `ErrorIdentifier` and text. **Not obtained**: `app.bar` (unsigned,
   development build) and `TLSDeBug-1_0_0_1.bar` (unsigned, release build) were both accepted.
6. A start with `size` larger than the free space: the refusal.
7. A start with an invalid `mode`: the shape of a request-validation error.
8. An upload cut off in the middle, then a new start: does the device recover, and after how long?
9. A successful uninstall of a test application. **Done**, with one package and with two, including the end (section
   12.9).
10. An uninstall of a package id that does not exist. **Done**: the device answers `DeleteRequest` with `Success`.
11. The answer to the start of an uninstall. **Done**: `UpdateStart` with `Ready`.
12. A second conversation started while one is running.
13. The same with a certificate-only session on port 8443. **Done**, as capture 2.
14. Over Wi-Fi, once `connect` can be established there: the answer to a start request.

### 12.7 installPackages

```
{"id":7,"method":"installPackages","params":{"session":"s1","files":["/path/app.bar"]}}
```

Installs applications. The bars are read first (section 12.4), and the command is refused before
anything is sent to the device if a file cannot be read, is not a usable bar, is not an application,
or is the same package as another in the list.

**The conversation** (the layout of the answers is from research and has not been seen on a device):

1. read the properties, for the free space; the device needs one and a half times the size of the
   packages (the research's rule). If the device does not say how much is free, the check is skipped
   with a `warn` log;
2. `mode=bar&size=<sum of the sizes>` gives `UpdateStart` with `Status` `Ready`;
3. for each bar: the raw file (`application/octet-stream`, `type=bar` in the query) gives
   `UpdateSend`; then an empty form is posted, every second, while `UpdateProgress` says
   `InProgress`, up to thirty minutes; `Success` or `Complete` ends the package. A device says
   `InProgress` with 100 once before it says `Success`, so 100 is not the end;
4. `status=success` gives `UpdateEnd` with `Status` `Success`. That status is checked: any other
   ends the command with `UNEXPECTED_RESPONSE`, and an end without a status is accepted.

**Events.** Log lines say what is done ("Starting the installation", "Sending package 1 of 2",
"Waiting for the device to install the package", once). Progress events: phase `uploading` with
`bytesDone`, `bytesTotal`, `percent` and the file name as `item`, about once for each percent of the
file; phase `installing` with the device's own percentage and the file name.

**Result:** `installed[]` (`file`, `packageId`, `packageName`, `packageVersion`, `sizeBytes`) in the
order sent, and the device's last answer as `bodyFormat` and `body` (section 2): what the research
calls `UpdateEnd`.

**Failures.**

| Code                  | When                                                                                                                                         |
|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `INVALID_ARGUMENT`    | `files` missing, empty, or a file that is not usable: named as `files` or `files[n]`                                                         |
| `IO_ERROR`            | a file that cannot be read (`details.param` names it)                                                                                        |
| `NOT_ENOUGH_SPACE`    | `details.requiredBytes`, `freeBytes`, `packageBytes`; nothing was sent                                                                       |
| `COMMAND_FAILED`      | the device reports `Status` `Error`: its `ErrorDescription` as the message, its `ErrorIdentifier` in `details`; or it did not finish in time |
| `UNEXPECTED_RESPONSE` | an answer that is not the element expected, not XML, or with a status that is not known                                                      |
| `CONNECTION_LOST`     | the connection dropped; **never repeated**, because the device may have installed the package                                                |
| `AUTH_FAILED`         | the session was lost in the middle ("ended the session while the command was running"); not repeated either                                  |

After a failure the result event is still sent before `done`: it has what had been installed until
then and the answer that caused the failure, whole, so that a first run against a device shows what
it really says. `raw: true` adds the scrubbed answers of every request.

Not done yet: firmware (an operating system or radio bar is refused, naming the file, with
"installing firmware is not supported yet").

### 12.8 The first installation on a device **[verified]**

`bblink install app.bar` (5,291,534 bytes; unsigned; `Application-Development-Mode: true`), in a
session that offered the client certificate alone on port 8443. No cookie of any kind was sent with
these requests. The six exchanges after the properties were read:

| # | Request                                                                 | Answer                                                           |
|---|-------------------------------------------------------------------------|------------------------------------------------------------------|
| 1 | `POST update.cgi`, `mode=bar&size=5291534`                              | `UpdateStart`, `Status` `Ready`; `Set-Cookie: update=…`          |
| 2 | `POST update.cgi?type=bar`, `application/octet-stream`, 5,291,534 bytes | `UpdateSend`, `Status` `Success`                                 |
| 3 | `POST update.cgi`, empty body                                           | `UpdateProgress`, `Status` `InProgress`, `Progress` 3            |
| 4 | `POST update.cgi`, empty body                                           | `UpdateProgress`, `Status` `InProgress`, `Progress` **100**      |
| 5 | `POST update.cgi`, empty body                                           | `UpdateProgress`, `Status` `Success`, `Progress` 100             |
| 6 | `POST update.cgi`, `status=success`                                     | `UpdateEnd`, `Status` `Success`; `Set-Cookie: update=` (cleared) |

The recording is the fixture `installPackages--real-install`.

What it settles, and where it differs from the research:

- An **unsigned development build installs** through `update.cgi`; the device did not ask for a
  signature or for development mode.
- The **client certificate alone authenticates `update.cgi`** on port 8443: no `dtmauth`, no
  `loginsession`. A password session on port 443 has not been tried.
- The conversation's cookie is called **`update`**, not `update_cookie`. It arrives with an expiry
  that has already passed, so the HTTP client throws it away, and the conversation worked without it
  ever being sent back.
- **100 is not the end:** the device answers `InProgress` with 100 first, and `Success` on the next
  poll.
- `UpdateStart` has a `Status` (`Ready`), and **`UpdateEnd` has a `Status`** (`Success`); the
  research left both open.
- Answers contain XML **comments**: `Percentage of Install` in a progress answer, and `Update ID:`
  (empty here) and `DTM status: success` in the end. They are not read; the parser ignores them.
- The poll is a POST with an empty body. The time taken: about 2 s for the upload, then about 10 s
  until `Success`, with the device slow to answer the second poll (7 s).

Not captured yet: any refusal or error answer (the `ErrorIdentifier` and text of a refused start, send
or progress), a package that is already installed, several packages in one conversation, and a
password session. Whether the package is now listed by `getDeviceProperties` was not looked at.

### 12.9 uninstallPackages

```json
{"id":9,"method":"uninstallPackages","params":{"session":"s1","packageIds":["testRel_le_TLSDeBugc09806e9"]}}
```

Parameters: `session`; `packageIds` (strings, at least one, each made of letters, digits, dots, dashes
and underscores, at most 128, none twice); `check` (boolean, default `true`); `raw`.

Result: `removed` (the ids that the device reported removed, in the order asked), `bodyFormat` and
`body` (the last answer of the device; it is empty text if the device answered nothing at the end).

What it does, and what has been seen on a device (a Passport, 10.3.3.3216, certificate-only session):

1. Unless `check` is `false`, it reads the device's package list first, and **refuses before
   anything is sent to remove** an id that is not installed or that is firmware (`INVALID_ARGUMENT`, `details.param` is
   `packageIds[n]`). The list cannot tell a built-in application from
   one that was installed, so the device itself decides about those.
2. `mode=app&size=0` is answered with `UpdateStart` and `Status` `Ready` **[verified]**. The research
   named `DeleteRequest`; that is accepted too.
3. For each package, `type=bar&packageid=<id>` is answered with `Status` `Success` **[verified]**, in
   an element that depends on the package's place: **`DeleteRequest` for the first removal of the
   conversation and `DeleteProgress` for the others**. Either name is accepted for any of them. The
   removal takes effect at its own request: the package was gone from the device's list even in runs
   where the end of the conversation was not sent. A progress event with the phase `removing` and
   the package id as `item` goes out before each removal, with the percentage of the packages that
   are done.
4. `type=bar&status=success` ends it, and is answered with **`UpdateEnd` and `Status` `Success`**
   **[verified]**, with the two comments that follow an install (`Update ID:`, `DTM status: success`) and
   the `update` cookie cleared. An answer without a status is accepted, and so is an empty one;
   an answer with another status is `UNEXPECTED_RESPONSE`, and tells what had been removed.

What the captures of 2026-10-16 showed:

- **The device does not check the id.** `some.package.that.is.not.installed` was answered
  `DeleteRequest` and `Success`. So the check against the package list is the only protection against a
  mistyped id, and with `check: false` the result's `removed` says only what the device said.
- **A fault of mine, found by these captures.** The first version expected `DeleteProgress` for a
  removal, took `DeleteRequest` for `UNEXPECTED_RESPONSE`, and stopped after the first package: with two ids
  the first was removed (the device had done it already) and the second was never sent. It is fixed, and
  there is a check with two packages.
- An unsigned **release** build (`TLSDeBug`, `Application-Development-Mode: false`) was installed without
  complaint, twice. A device accepts unsigned bars, development mode or not. After a removal, a new
  install of the same package answered `Success` with `100` at the first poll, with no `InProgress` before.

Errors: `INVALID_ARGUMENT` (the ids; `check` that is not a boolean), `COMMAND_FAILED` (the device
reports an error, with its `errorIdentifier`; the `result` event still goes out first, with what had been
removed), `UNEXPECTED_RESPONSE` (including a removal whose `Status` is not a success), `CONNECTION_LOST`
(never repeated: the device may have removed the package), `AUTH_FAILED`.

The captures of 2026-10-17 (update 29, a removal of two packages) finished the picture: the start
answered `UpdateStart`, the first removal `DeleteRequest`, the second `DeleteProgress`, and the end
`UpdateEnd`, all with `Success`; the conversation sent every request in order. They showed the
asymmetry between the first removal and the others, which the research had not described.

The fixtures: `uninstallPackages.2--real-two-packages` is that capture, as recorded (the argument `2` of the
name is the number of packages that the replay removes); `uninstallPackages--real-removal` is the same
conversation with one package, whose end is the one captured; `uninstallPackages--synthetic-*` cover the end
without comments, an error answer and the layout of the research.

Still to capture: an error answer of a removal, which has not been provoked. A device does not refuse an
unknown id, so a refusal would have to come from something else (a built-in application, which should
not be tried). **Do not use `check: false` with the id of firmware or of a built-in application.**

## 13. Client certificates: the device signs one for us **[verified]**

`bblink pair <PIN>` (its first step) and `bblink renew <PIN>` (`getAuthenticationCertificate`) make a key and a
certificate request, have the device sign it, and keep both in `devices/<PIN>/`. It was run on a phone that had no
pairing, over a password session on USB; the certificate
it got logged in on port 8443, reading the properties and the PIN.

### 13.1 The request

|              |                                                                                                                                                          |
|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| URL          | `POST /cgi-bin/login.cgi?request_version=3&cgiauth=1`                                                                                                    |
| session      | the one that the password login made (`dtmauth`, `loginsession`)                                                                                         |
| content type | **`application/octet-stream`**; any other is refused (section 13.2)                                                                                      |
| body         | the request as PEM, unchanged: `-----BEGIN CERTIFICATE REQUEST-----` …                                                                                   |
| key          | RSA 2048                                                                                                                                                 |
| signature    | SHA-1 with RSA                                                                                                                                           |
| subject      | the common name `BBCGIAUTH [PIN] <host>` and nothing else: the PIN as hexadecimal digits in brackets, then the name of the computer cut to 19 characters |

This is the layout of the certificate that BlackBerry Link for Windows got for a laptop: `BBCGIAUTH [2A3B4C5D]
WORKSTATION-M`, with no description. Updates 37 to 39 sent the layout of the research, `BBCGIAUTH <host>-<login> [PIN]`
and a description entry, and the device signed that too; the phone then listed the computer as "Desktop-" and four
digits.
The layout did not change the name, which stays "Desktop-" and four digits **[verified]**: see the open issue in section

11. What the `-M` at the
    end of the laptop's name is, a login or part of the computer's name, is not known; bb-link writes the host name
    alone.
    The request is under 1 KB of PEM. It is never repeated after a dropped connection.

### 13.2 The answers

- **A success is the certificate as PEM, and nothing else**: HTTP 200, `Content-Type: application/octet-stream`, no XML.
- **An error is XML in a `CgiAuth` element**, with `Status` `Error` and an `Error` that has an `ErrorIdentifier` and an
  `ErrorDescription`. The one seen: error **260**, `Expected ContentType of application/octet-stream`, for the same
  request sent as `application/x-www-form-urlencoded`. The device checks the content type before it reads anything
  else.

Both answers are fixtures (`getAuthenticationCertificate--synthetic-issued`, which has the shape of the real answer but
a
certificate that is made up, with a dummy PIN, and `getAuthenticationCertificate--real-content-type`). An answer
that has no certificate is `UNEXPECTED_RESPONSE`, and shows what it was.

### 13.3 The certificate

The device **keeps the subject as it was sent**, whatever its layout, and adds `serialNumber`, which is the serial
number of the
certificate and a counter of the device's (28837, then 28838). The issuer is the phone's own authority, `BB10/<hash>`,
the one in `/var/cgiauth/cgiauth_cert.crt`, whose authority key identifier carries serial `2A`. It is not an authority
itself, has the key usage of signature, non-repudiation and key encipherment, is signed with SHA-1 and RSA, and is valid
**37 days**: from seven days before it was made to 30 days after. The laptop's certificate is the same in all of this;
only the layout of its name differs, because the Windows client writes `BBCGIAUTH [PIN] <computer>`.

### 13.4 What is kept, and how it renews

`devices/<PIN>/` has `client_cert.pem`, `client_key.pem` (a credential: owner only, never in a result, a log or a
trace) and `identity.json` (`commonName` and `createdAt`; an older file with `deviceId` in it is still read). Nothing is
written unless the certificate is X.509 and for our key; only then are the files moved into place, the key first.
Without `--no-verify` the certificate is tried straight away by a login with it alone on port 8443, and the result
says `verified` or `failed`.

**Renewal is `bblink renew <PIN>`**, before the 37 days are over; it leaves the pairing as it is. `status` and `connect`
warn within seven days and say how. Each renewal makes one more certificate on the device; an older one stays valid
until
it expires.

### 13.5 How the code is cut

`pairing.pki` (DER, requests, certificates and authorities, PEM, certificate details) uses the JDK and nothing else;
`pairing.store` (the folders of a certificate and of a pairing) uses `pki` and the core's JSON. A check reads their
imports and fails if they reach into the CGI layer,
the sessions or the daemon, so that both can move to a library of their own, for other tools that pair with a device,
without a change.

### 13.6 Not known

- What decides the name in Device Connections: an open issue, section 11.
- How renewal should go: whether a certificate that is about to expire can be replaced at any time, and what the
  device does with the old one, which stays valid until it expires. `bblink renew` makes a new one and leaves the
  pairing.
- Whether it works on a phone that Windows has paired (the old phone has not been tried), and on other OS versions.
- Whether the certificate is accepted over Wi-Fi: port 8443 was refused there, for every kind of login.
- What the limits on the names are, and whether the device counts or limits the certificates it has signed.
