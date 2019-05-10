# authoring-server

[![Open Learning Initiative](https://oli.cmu.edu/wp-content/uploads/2018/10/oli-logo-78px-high-1.svg)](http://oli.cmu.edu/)

[![Build Status](https://dalaran.oli.cmu.edu/jenkins/buildStatus/icon?job=authoring-server)](https://dalaran.oli.cmu.edu/jenkins/job/authoring-server/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/Simon-Initiative/authoring-server/blob/master/LICENSE)

Java EE OLI Content Service Maven project

This includes:
* Maven for build system
* JUnit for unit testing
* Arquillian for Integration Testing
* Dockerized Wildfly testing 
* Jenkins for testing and continuous integration

## Related repositories
* [authoring-dev](https://github.com/Simon-Initiative/authoring-dev) - Docker development environment for the course authoring platform
* [authoring-client](https://github.com/Simon-Initiative/authoring-client) - Typescript/React/Redux editing client
* [authoring-admin](https://github.com/Simon-Initiative/authoring-admin) - Elm admin client
* [authoring-eval](https://github.com/Simon-Initiative/authoring-eval) - Typescript/Node dynamic question evaluation engine

## Dependencies
* Maven
* Docker
* Docker Compose (Linux).

## How to Install

Clone this repository:

```
$ git clone https://github.com/Simon-Initiative/authoring-server
```

Next change the values in the `service.envs` file (database names, passwords, ports etc). Note that the 
values for ports and names should be unique to this project, otherwise there is a
chance of name collision with other docker projects in you system.  

## How to Run

First, to build the docker images, you will need to run the following at least once.

```
$ docker-compose build
```

Then, launch the docker containers 
```
$ docker-compose up
```
This will run a containerized remote Wildfly application server as well as other support services (databases, distributed cache etc). 

Note that this being a typical JEE maven project, you may build and debug your web
application (via IDE or command line) without interacting with the remote application 
server.

```
$ mvn clean package
```
You also have, with proper setup, the option of deploying and testing the web application in a locally managed,
Wildfly application server.
```
$ mvn clean test -Parq-wildfly-managed
```

To deploy the web application to the remote docker containerized Wildfly issue the command.
```
$ mvn clean install
```
Note that this command will run your JUnit tests before deploying the .war file to the 
application server.

For tests beyond unit tests (integration tests), run the following maven command
```
$ mvn clean test -Parq-wildfly-remote
```

## Updating variables

If you edit service.envs, Dockerfile or docker-compose.yml files, stop, rebuild and restart the container:

```
$ docker-compose down

$ docker-compose up --build
```

## License
This software is licensed under the [MIT License](./LICENSE) © 2019 Carnegie Mellon University
