# integration-catalogue-autopublish

A microservice that polls HIP's OAS Discovery service to find services that are either new or have been recently
deployed. For each new or recently deployed service the most recent OAS document is retrieved from HIP and published to
Integration Catalogue.

For more information on the project please visit this space in Confluence:
https://confluence.tools.tax.service.gov.uk/display/AH/The+API+Hub+Home

## Requirements

This service is written in [Scala](http://www.scala-lang.org/) and [Play](http://playframework.com/), so needs at least a [JRE] to run.

## Dependencies
Beyond the typical HMRC Digital platform dependencies this service relies on:
- MongoDb
- OAS Discovery (HIP)
- Integration Catalogue (MDTP)

You can view service dependencies using the Tax Catalogue's Service Relationships
section here:
https://catalogue.tax.service.gov.uk/service/integration-catalogue-autopublish

### MongoDb
This service uses MongoDb to store services and their last known deployment timestamp.

The MongoDb version should be 5.0 and is constrained by the wider platform not this service.

- Database: integration-catalogue-autopublish
- Collection: tbd

### OAS Discovery
This service fetches services and OAS documents from the OAS Discovery service on the HIP platform.

### Integration Catalogue
This service publishes updated OAS documents to Integration Catalogue.

## Using the service

### Running the application

To run the application use `sbt run` to start the service. All local dependencies should be running first.

Once everything is up and running you can access the application at

```
http://localhost:9000/integration-catalogue-autopublish
```

### Authentication
The service uses internal-auth to authenticate requests to Integration Catalogue using the service-to-service
auth pattern. See https://github.com/hmrc/internal-auth

## Building the service
This service can be built on the command line using sbt.
```
sbt compile
```

### Unit tests
This microservice has many unit tests that can be run from the command line:
```
sbt test
```

### Integration tests
This microservice has some integration tests that can be run from the command line:
```
sbt it / test
```

## License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
