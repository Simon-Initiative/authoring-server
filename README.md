# authoring-server
Java EE OLI Content Service Maven project

This includes:
* Maven for build system
* JUnit for unit testing
* Arquillian for Integration Testing
* Dockerized Wildfly testing 
* Jenkins for testing and continuous integration

## Dependencies
* Maven
* Docker
* Docker Compose (Linux).

## How to Install

Clone this repository:

```
$ git clone https://github.com/Simon-Initiative/content-service
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
