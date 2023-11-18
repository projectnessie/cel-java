# Running CEL-spec conformance tests against CEL-Java

If your environment is already setup, just run the shell script
```shell
./run-conformance-tests.sh
```

## Requirements & Setup

The CEL-spec conformance test suite is written in Go and uses the bazel build tool.

Required tools:
* [Bazel build tool](https://bazel.build/)
  
    See [Bazel web site](https://bazel.build/install) for installation instructions.
    Do **not** use the `bazel-bootstrap` package on Ubuntu, because it comes with
    pre-compiled classes that are built with Java 17 or newer, preventing it from
    running bazel using older Java versions.
* gcc
    
    On Ubuntu run `apt-get install gcc`

Other required dependencies like "Go" will be managed by bazel.

## FAQ

### Bazel build hangs

If the bazel build does not start, i.e. it gets stuck with messages like
```
Starting local Bazel server and connecting to it...
... still trying to connect to local Bazel server after 10 seconds ...
... still trying to connect to local Bazel server after 20 seconds ...
... still trying to connect to local Bazel server after 30 seconds ...
```
then kill all bazel processes make sure that Java 11 is the current one. It seems, that the
above *may* happen when with Java 8.

### Bazel build fails from `./run-conformance-tests.sh`

If the bazel build fails with an error like this (note the `Failed to create temporary file`),
run the bazel build once from the console:

```shell
cd submodules/cel-spec

bazel build ...
```

If the build still fails, try the following options:

```shell
bazel build ... --sandbox_writable_path="${HOME}/.ccache" --strategy=CppCompile=standalone
```

After the build succeeds once from that directory, you can use the `./run-conformance-tests.sh`
script.
