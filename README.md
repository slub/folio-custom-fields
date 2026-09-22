# folio-custom-fields

Copyright (C) 2019-2024 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

<!-- ../../okapi/doc/md2toc -l 2 -h 4 README.md -->
* [Introduction](#introduction)
* [Module Deployment](#module-deployment)
* [Issue tracker](#issue-tracker)
* [Code analysis](#code-analysis)

## Introduction

The FOLIO module to store and maintain custom fields. The folio-custom-fields is designed to be a
library that any module can add and use to store custom fields definitions as well as their values.

The custom fields module implements a simple CRUD interface POST/PUT/GET/DELETE on /custom-fields and /custom-fields/$id endpoints. See the ramls/custom-fields.json for precise definitions.

Written in Java, using the raml-module-builder and uses Maven as its build system.

The general design of the custom fields is following

![](images/custom-fields-design.png)

The module has a common interface which can be used by several modules. 
This feature called *Multiple interfaces* and helps OKAPI to dispatch the request to any number of modules with the same interface just use "interfaceType": "multiple"  in the "Provides" section of the ModuleDescriptor.json file
To define a particular module to be called to, declare `X-Okapi-Module-Id` header, detailed information is able via the link to [Okapi documentation](https://github.com/folio-org/okapi/blob/master/doc/guide.md#multiple-interfaces).
The additional information about folio-custom-fields and sample request collection can be found in [Confluence](https://wiki.folio.org/pages/viewpage.action?spaceKey=FOLIJET&title=MODCFIELDS-39+-+Custom+Field+backend+demo)

## Module Deployment

The custom fields functionality can be added to any module as a jar file.
The target module needs to carry out the changes described below. 
Please note that indicated module version and ModuleDescriptor.json file serve informational purpose.  
  1. Add maven dependency for `folio-custom-fields` in project pom file.
  Example:
   ~~~~
   <dependency>
     <groupId>org.folio</groupId>
     <artifactId>folio-custom-fields</artifactId>
     <version>1.4.1</version>
   </dependency>
   ~~~~
  We recommend using the latest released version. The list of released versions is available via the [link](https://github.com/folio-org/folio-custom-fields/releases).
  2. Modify the section `scripts` in schema.json file to include `create_custom_fields_table.sql`, which will created a table for storing module-specific custom fields and additional triggers.
  Example:
  ~~~~
  "scripts" : [
      {
        "run": "after",
        "snippetPath": "create_custom_fields_table.sql",
        "fromModuleVersion": "1.0"
      }
    ]
  ~~~~
  3. Modify the ModuleDescriptor.json file by including the custom-fields interface
  Example:   
  ~~~~
  {
    "id": "custom-fields",
    "version": "1.0",
    "interfaceType" : "multiple",
    "handlers": [
      {
        "methods": ["GET"],
        "pathPattern": "/custom-fields",
        "permissionsRequired": ["custom.fields.collection.get"]
      },
      {
        "methods": ["POST"],
        "pathPattern": "/custom-fields",
        "permissionsRequired": ["custom.fields.item.post"]
      },
      {
        "methods": ["GET"],
        "pathPattern": "/custom-fields/{id}",
        "permissionsRequired": ["custom.fields.item.get"]
      },
      {
        "methods": ["PUT"],
        "pathPattern": "/custom-fields/{id}",
        "permissionsRequired": ["custom.fields.item.put"]
      },
      {
        "methods": ["DELETE"],
        "pathPattern": "/custom-fields/{id}",
        "permissionsRequired": ["custom.fields.item.delete"]
      }
    ]
   }
   ~~~~ 
  The permission name inside of the `permissionsRequired` section can be modified to represent the module purpose.
  See [mod-users](https://github.com/folio-org/mod-users/pull/136/files) as an example of `folio-custom-fields` integration.
  4. Since `custom-fields` interface version 3.2 (folio-custom-fields 4.0.0): custom field sections.
  Add the `create_custom_field_sections_table.sql` snippet to the `scripts` section of schema.json, with the host
  module's own next version as `fromModuleVersion`, so that it also runs for existing tenants on upgrade:
  ~~~~
  {
    "run": "after",
    "snippetPath": "create_custom_field_sections_table.sql",
    "fromModuleVersion": "<host-module-name>-<next-version>"
  }
  ~~~~
  Add the handlers for `GET`/`POST` `/custom-field-sections` and `GET`/`PUT`/`DELETE` `/custom-field-sections/{id}`
  to the `custom-fields` interface in the ModuleDescriptor, with (host-prefixed) permissions for them, see
  [ModuleDescriptor-template.json](descriptors/ModuleDescriptor-template.json).
  Without the snippet, calls to the section endpoints and saving a custom field with a `sectionId` fail with
  `relation "custom_field_sections" does not exist`.

## Custom field sections

A custom field section (`id`, `name`, `entityType`) groups the custom fields of one entity type. A custom field is
assigned to a section with its optional `sectionId`:

* The section must exist and must have the same `entityType` as the custom field, otherwise saving the field fails with 422.
* The section `name` is unique per `entityType` (case-insensitive).
* A section belongs to exactly one `entityType`; fields of different entity types cannot share a section.

### Deleting a section

A section that is still assigned to at least one custom field **cannot be deleted**, and its `entityType` cannot be
changed. Removing a section is a manual process, nothing is moved or unassigned automatically:

1. Find the custom fields assigned to the section: `GET /custom-fields?query=sectionId==<id>`.
2. Assign each of them to another section, or remove its `sectionId` (`PUT /custom-fields/{id}` or `PUT /custom-fields`).
3. Delete the section, which is now empty: `DELETE /custom-field-sections/{id}` → 204.

If the section is still assigned, `DELETE` responds with 422, code `sectionInUse`, and lists the assigned
custom fields as parameters (`key` = field id, `value` = field name). The section and the custom fields are unchanged.
The check runs in a transaction that locks the section row, so a custom field can't be assigned to the section while
it is being deleted.

## RecordService

The [`RecordService`](src/main/java/org/folio/service/RecordService.java) is responsible for updating entities when
custom fields are updated. This means that whenever a custom field is modified, the `RecordService` ensures that these
changes are reflected in the corresponding entities. Additionally, the `RecordService` provides statistics on how 
many entities are using a particular custom field or option.

Two different implementations of the `RecordService` interface are available:

By default the [`NoOpRecordService`](src/main/java/org/folio/service/NoOpRecordService.java) is used. This is a basic 
implementation of the `RecordService` that does not update any entities and returns statistics with zero counts.

The [`RecordServiceImpl`](src/main/java/org/folio/service/RecordServiceImpl.java) implementation does update 
entities and returns statistics based on the actual usage of custom fields or their options. It can be configured to 
support multiple tables for each `entityType`.

To use the `RecordServiceImpl`, follow these steps:

1. Implement the `RecordServiceFactory` interface to return a new instance of the 
   `RecordServiceImpl` implementation with the specified entity types and tables. The map takes 
   entity types as key and the corresponding table name as value.

    ```java
    package org.folio.services.record;
    
    import io.vertx.core.Vertx;
    import java.util.Map;
    import org.folio.service.RecordService;
    import org.folio.service.RecordServiceImpl;
    import org.folio.service.spi.RecordServiceFactory;
    
    public class RecordServiceFactoryImpl implements RecordServiceFactory {
    
      @Override
      public RecordService create(Vertx vertx) {
        return RecordServiceImpl.createForSingleTable(
          vertx, Map.of("po_line", "po_line", "purchase_order", "purchase_order"));
      }
    }
    ```
2. Make the implementation available by updating the `META-INF/services/org.folio.service.
   RecordServiceFactory` file to contain the fully qualified name of the implementation class
  
    ```
    org.folio.services.record.RecordServiceFactoryImpl
    ```
3. Optional. For efficient data handling, add a GIN index on the `jsonb->'customFields'`
   expression to each entity table.
    ```sql
    CREATE INDEX purchase_order_customfields_recordservice_idx_gin
    ON ${myuniversity}_${mymodule}.purchase_order USING GIN ((jsonb->'customFields'));
    ```
If neither of the above implementations are suitable, the steps above can also be followed to use a 
different implementation.

## Issue tracker

See project [FCFIELDS](https://issues.folio.org/browse/FCFIELDS)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

## Code analysis
[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Afolio-custom-fields).
