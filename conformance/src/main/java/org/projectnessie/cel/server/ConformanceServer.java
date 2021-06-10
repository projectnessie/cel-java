/*
 * Copyright (C) 2021 The Authors of CEL-Java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.cel.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

public class ConformanceServer implements AutoCloseable {

  private final Server server;

  public ConformanceServer(Server server) {
    this.server = server;
  }

  public static String getListenHost(Server server) {
    List<? extends SocketAddress> addrs = server.getListenSockets();
    SocketAddress addr = addrs.get(0);
    InetSocketAddress ia = (InetSocketAddress) addr;
    InetAddress a = ia.getAddress();

    String host;
    if (a instanceof Inet6Address) {
      if (a.isAnyLocalAddress()) {
        host = "::1";
      } else {
        host = a.getCanonicalHostName();
      }
    } else {
      if (a.isAnyLocalAddress()) {
        host = "127.0.0.1";
      } else {
        host = a.getCanonicalHostName();
      }
    }

    return host;
  }

  public void blockUntilShutdown() throws InterruptedException {
    server.awaitTermination();
  }

  @Override
  public void close() throws Exception {
    server.shutdown().awaitTermination();
  }

  public static void main(String[] args) throws Exception {
    ConformanceServiceImpl service = new ConformanceServiceImpl();

    for (String arg : args) {
      if ("--verbose".equals(arg) || "-v".equals(arg)) {
        service.setVerboseEvalErrors(true);
      }
    }

    Server c = ServerBuilder.forPort(0).addService(service).build();

    Thread hook = new Thread(c::shutdown);

    try (ConformanceServer cs = new ConformanceServer(c.start())) {
      System.out.printf("Listening on %s:%d%n", getListenHost(cs.server), cs.server.getPort());

      Runtime.getRuntime().addShutdownHook(hook);
      cs.blockUntilShutdown();
    } finally {
      try {
        Runtime.getRuntime().removeShutdownHook(hook);
      } catch (IllegalStateException e) {
        // ignore (might happen, when a JVM shutdown is already in progress)
      }
    }
  }
}
