# docker-host-java

`docker-host-java` is a small experimental Java library for working with Docker Engine resources from Java code.

The core idea is to use a Docker host as the main entry point, and then work with containers, images, volumes, and networks as objects that belong to that host.

> Status: early design / prototype stage.
> The API is not stable yet, and this project is not ready for production use.

## Why I started this project

This project is not trying to replace Docker Compose, Kubernetes, Testcontainers, or the existing Docker Java client ecosystem.

Instead of focusing only on calling remote Docker APIs directly, this project tries to focus on objects and hosts as the main model.

For example, I would like code like this to be possible:

```java
Container created = host.createContainer(container);

created.start();

ContainerLogs logs = created.logs();

created.remove();
```

The main focus is readability and lifecycle management.

## Basic idea

A `DockerHost` represents one Docker Engine connection.

```text
DockerHost
├── Container
├── Image
├── Volume
└── Network
```

Containers, images, volumes, and networks are created or looked up through a `DockerHost`.

Once a resource is connected to a real Docker resource, it can expose operations such as `start`, `remove`, `inspect`, or collecting `logs`.

## Example API

The API below is only a rough preview.

Names and behavior may change as the project develops.

```java
DockerHost host = DockerHost.connect("tcp://localhost:2375");

Container container = Container.builder("alpine:3.20")
        .name("hello")
        .command("echo", "hello")
        .build();

Container created = host.createContainer(container);

created.start();

ContainerLogs logs = created.logs();

System.out.println(logs.stdout());

created.remove();
```

## What this project may be useful for

This library may be useful when a Java application needs to create and control Docker resources directly.

Possible use cases include:

* running short-lived containers from Java code
* collecting container logs or execution results
* building code runners, online judges, or sandbox-style systems
* managing resources across more than one Docker host
* experimenting with Docker Engine integration in Spring Boot applications

These are still target use cases, not guaranteed features.

## Core concepts

### DockerHost

`DockerHost` is the starting point of the API.

It represents a connection to one Docker Engine, for example:

```text
tcp://localhost:2375
unix:///var/run/docker.sock
npipe:////./pipe/docker_engine
```

Most resource operations will start from this object.

### Container

`Container` represents a Docker container.

One design question in this project is how to separate a container definition from a container that already exists on a Docker host.

At the moment, the planned model has two states:

* `unbound`: the container has been described in Java, but has not been created on a `DockerHost` yet
* `bound`: the container exists on a Docker host and has a real Docker container ID

This is intended to avoid nullable container IDs in normal user code.

### Image

`Image` represents a Docker image.

Planned operations include pulling, listing, inspecting, removing images, and creating containers from an image.

### Volume

`Volume` represents a Docker volume.

Planned operations include creating, listing, inspecting, removing, and mounting volumes to containers.

### Network

`Network` represents a Docker network.

Planned operations include creating, listing, inspecting, removing, and connecting or disconnecting containers.

## Current limitations

This project is still in an early stage.

Some important parts are not decided yet, including:

* error handling
* async or blocking API design
* Docker API compatibility range
* authentication and remote Docker host support
* Spring Boot integration style
* test strategy for real Docker environments

Because of this, this README should be treated as a design note rather than final documentation.

## License

This project is licensed under the Apache License 2.0.
