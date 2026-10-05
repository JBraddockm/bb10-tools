# BlackBerry 10 Native Tools

This repository builds Java-based tools from the **BlackBerry 10 NDK** into standalone native executables using
**GraalVM Native Image**.

The project uses the **Community Edition of GraalVM 25**.

> **Important:** This repository does not contain any proprietary BlackBerry tools or libraries. You must provide the
> required libraries from your own BlackBerry 10 NDK installation.

## Requirements

- Java / GraalVM Community Edition 25
- Maven
- BlackBerry 10 NDK

## Setup

### 1. Install Maven

Apache Maven is required to build the project.

Maven can be installed using your operating system's package manager or manually. See the official Maven installation
documentation:

[Apache Maven Installation Guide](https://maven.apache.org/install.html)

### 2. Install GraalVM 25

The project requires **GraalVM Community Edition 25**.

GraalVM must be available on your `PATH`, and `JAVA_HOME` must point to the GraalVM installation.

Verify the installation with:

```bash
java -version
```

You should see GraalVM 25 in the output.

Also verify that `JAVA_HOME` points to the same installation:

```bash
echo "$JAVA_HOME"
"$JAVA_HOME/bin/java" -version
```

For example:

```text
openjdk 25.0.1 2025-10-21
OpenJDK Runtime Environment GraalVM CE 25.0.1+8.1 (build 25.0.1+8-jvmci-b01)
OpenJDK 64-Bit Server VM GraalVM CE 25.0.1+8.1 (build 25.0.1+8-jvmci-b01, mixed mode, sharing)
```

You can also verify that the native-image tool is available:

```bash
native-image --version
```

If `native-image` is not found, make sure the GraalVM `bin` directory is included in your `PATH`.

---

### 2. Provide the BlackBerry 10 libraries

The proprietary BlackBerry libraries are not included in this repository. Copy the required `.jar` files from your
BlackBerry 10 NDK into the repository's `/libs` directory.

The required libraries are:

```text
apache-mime4j-0.6.7.jar
BarAir.jar
BarChecker.jar
BarDeploy.jar
BarLibrary.jar
BarPackager.jar
BarSigner.jar
commons-codec-1.4.jar
commons-logging-1.1.1.jar
Connect.jar
DebugTokenRequest.jar
EccpressoAlt.jar
EccpressoJDK15ECC.jar
httpclient-4.0.3.jar
httpcore-4.0.1.jar
httpmime-4.0.3.jar
jqconnDoor.jar
KeyTool.jar
TrustpointAll.jar
TrustpointJDK15.jar
TrustpointProviders.jar
```

The exact location of these files depends on your BlackBerry 10 NDK installation. On Linux, it is
`/bbndk/host_10_3_1_12/linux/x86/usr/lib`.

The `/libs` directory is intentionally not populated by this repository because these files are proprietary BlackBerry
components.

---

### 3. Install the BlackBerry libraries into Maven

Once the required JARs have been copied into `/libs`, run:

```bash
bash lib-install.sh
```

This installs the supplied BlackBerry libraries into the local Maven repository, and places them under `libs-repo`
directory so that the Maven modules can resolve them as dependencies.

You only need to run this again if the libraries change or your local Maven repository is removed.

## Project Structure

The repository contains multiple BlackBerry 10 tools, each built as a separate Maven module.

For example:

```text
.
├── blackberry-connect/
│   ├── src/
│   ├── pom.xml
│   └── target/
├── blackberry-deploy/
│   ├── src/
│   ├── pom.xml
│   └── target/
├── libs/
├── lib-install.sh
└── pom.xml
```

## Building

The native executables are built as part of the Maven `package` phase.

The project uses the GraalVM Native Image Maven plugin. The native compilation is configured with:

```xml

<executions>
    <execution>
        <id>build-native</id>

        <goals>
            <goal>compile-no-fork</goal>
        </goals>

        <phase>package</phase>
    </execution>
</executions>
```

Therefore, running Maven through the `package` phase automatically invokes the native-image build.

### Build all modules

To build all modules in the repository:

```bash
mvn package
```

For a clean build:

```bash
mvn clean package
```

This compiles the Java sources and then invokes GraalVM Native Image during the Maven `package` phase to produce the
native executable for each module.

### Build an individual module

To build the `blackberry-connect` module:

```bash
mvn -pl blackberry-connect package
```

To build the `blackberry-deploy` module:

```bash
mvn -pl blackberry-deploy package
```

### Build Output

The resulting native executables are placed in the `target` directory of the corresponding Maven module.
For example, after building:

```bash 
mvn -pl blackberry-connect package
```

the executable will be located under:

```text
blackberry-connect/
└── target/
└── ...
```

The exact executable filename depends on the module configuration.


