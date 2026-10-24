# certs/

The tunnel's **client identity** — the certificate + private key paired (enrolled) with the target
BlackBerry 10 device — lives here and is loaded from the classpath by
`TunnelConfig.fromClasspath(...)`:

| File               | What it is                                                              |
|--------------------|-------------------------------------------------------------------------|
| `blend_chain.pem`  | the enrolled client leaf certificate (`CN=blend_client_9665`, EC P-521) |
| `blend_key_p8.pem` | its private key, in PKCS#8                                              |

**These files are deliberately *not* committed** — key material never goes to the repo (see the
root `.gitignore`). Only this README is tracked, so the layout is visible.

To build/run against your own paired device, drop your enrolled identity in here with those two
names, or point the code at files elsewhere with
`TunnelConfig.fromFiles(host, chainPem, keyPem)`. See the top-level `README.md` for how the identity
is created and enrolled.
