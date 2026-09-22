## 4.0.0-SNAPSHOT In progress

### New APIs versions
* Provides `custom-fields v3.2`

### Features
* Add `LOOKUP` custom field type storing the UUID of a record in another FOLIO app ([UXPROD-5934](https://folio-org.atlassian.net/browse/UXPROD-5934)). Values are validated for UUID shape only; existence and entity type of the referenced record are not verified (no referential integrity).
* Add custom field sections: `/custom-field-sections` CRUD and optional `sectionId` on custom fields; a section in use cannot be deleted ([FCFIELDS-95](https://issues.folio.org/browse/FCFIELDS-95))

### Bug fixes
* Update example for putCustomFieldCollection ([FCFIELDS-73](https://issues.folio.org/browse/FCFIELDS-73))
* Set "permissions: contents: read" in maven.yml ([FOLIO-4553](https://folio-org.atlassian.net/browse/FOLIO-4553))

## 3.0.0 2026-04-15

### Breaking changes
* Upgrade to Vert.x `v5.0.6` ([FCFIELDS-72](https://issues.folio.org/browse/FCFIELDS-72))

### New APIs versions
* Provides `custom-fields v3.1`

### Features
* Add `displayInAccordion` property to custom field definition ([FCFIELDS-54](https://issues.folio.org/browse/FCFIELDS-54))
* Sensitive data cleanup ([FCFIELDS-58](https://issues.folio.org/browse/FCFIELDS-58))
* Use GitHub Workflows for Maven ([FCFIELDS-79](https://issues.folio.org/browse/FCFIELDS-79))

### Bug fixes
* Fix empty refId when custom field name contains only non-Roman characters ([FCFIELDS-70](https://issues.folio.org/browse/FCFIELDS-70))
* Remove SQL injection "WHERE id='%s'" ([FCFIELDS-62](https://issues.folio.org/browse/FCFIELDS-62))

### Tech Dept
* Remove useless Promise, remove duplicate PostgresClient code ([FCFIELDS-63](https://issues.folio.org/browse/FCFIELDS-63))
* Run removeValues in transaction ([FCFIELDS-68](https://issues.folio.org/browse/FCFIELDS-68))
* replaceAll: remove values in transaction ([FCFIELDS-61](https://issues.folio.org/browse/FCFIELDS-61))
* Exclude vulnerable commons-beanutils dependency ([FCFIELDS-67](https://issues.folio.org/browse/FCFIELDS-67))
* Upgrade `folio-di-support` to `v4.0.0` ([FCFIELDS-83](https://issues.folio.org/browse/FCFIELDS-83))

### Dependencies
* Bump `raml-module-builder` from `35.4.0` to `36.0.0`
* Bump `vertx` from `4.5.13` to `5.0.10`
* Bump `folio-service-tools` from `5.0.0` to `6.0.0`
* Bump `folio-di-support` from `3.0.0` to `4.0.0`
* Bump `spring` from `6.2.3` to `7.0.6`
* Bump `lombok` from `1.18.36` to `1.18.44`
* Bump `commons-validator` from `1.9.0` to `1.10.1`
* Bump `log4j-bom` from `2.24.0` to `2.25.4`
* Bump `junit` from `5.10.0` to `6.0.3`
* Bump `rest-assured` from `5.5.0` to `6.0.0`

---

## 2.3.0 2025-03-12
* Added logs for debugging ([#56](https://github.com/folio-org/folio-custom-fields/pull/56))
* Update to folio-custom-fields Java 21 ([FOLIO-4230](https://folio-org.atlassian.net/browse/FOLIO-4230))

## 2.2.0 2024-10-30
* Upgrade rmb version to 35.3.0 ([FCFIELDS-55](https://issues.folio.org/browse/FCFIELDS-55))

## 2.1.0 2024-03-19
* Update spring and folio-di-support versions.

## 2.0.0 2024-03-18
* Attribute helpText of customField cannot be null ([FCFIELDS-43](https://issues.folio.org/browse/FCFIELDS-43))
* 'undefined' metadata values causing validation errors ([FCFIELDS-47](https://issues.folio.org/browse/FCFIELDS-47))
* Custom Field Input Type: Date Picker ([FCFIELDS-46](https://issues.folio.org/browse/FCFIELDS-46))
* Enhance RecordUpdate entity with updated custom field info ([FCFIELDS-48](https://issues.folio.org/browse/FCFIELDS-48))
* Provide useful message in CustomFieldValidationException ([FCFIELDS-49](https://issues.folio.org/browse/FCFIELDS-49))
* folio-custom-fields Quesnelia 2024 R1 - RMB v35.2.x update ([FCFIELDS-51](https://issues.folio.org/browse/FCFIELDS-51))
* Provide a generic implementation for RecordService ([FCFIELDS-52](https://issues.folio.org/browse/FCFIELDS-52))
* PUT /custom-fields does not handle multiple entity types ([FCFIELDS-44](https://issues.folio.org/browse/FCFIELDS-44))


## 1.10.0 2023-10-11
* Logging improvement ([FCFIELDS-25](https://issues.folio.org/browse/FCFIELDS-25))
* Mention required X-Okapi-Module-Id header in API docs ([FCFIELDS-37](https://issues.folio.org/browse/FCFIELDS-37))
* Update to Java 17 ([FCFIELDS-38](https://issues.folio.org/browse/FCFIELDS-38))
* Update the codeowners file ([FCFIELDS-39](https://issues.folio.org/browse/FCFIELDS-39))
* Use GitHub Workflows api-lint and api-schema-lint and api-doc ([FCFIELDS-36](https://issues.folio.org/browse/FCFIELDS-36))
* Update the folio-di-support in the pom.xml ([FCFIELDS-40](https://issues.folio.org/browse/FCFIELDS-40))

## 1.9.1 2023-02-14
### Bug Fixes
* ModuleName overwrites actual ModuleName ([FCFIELDS-34](https://issues.folio.org/browse/FCFIELDS-34))

### Tech Dept
* Align logging configuration with common Folio solution ([FCFIELDS-32](https://issues.folio.org/browse/FCFIELDS-32))

### Dependencies
* Bump `folio-service-tools` from `1.10.0` to `1.10.1`
* Bump `raml-module-builder` from `35.0.0` to `35.0.6`
* Bump `vertx` from `4.3.3` to `4.3.4`
* Bump `lombok` from `1.18.24` to `1.18.26`
* Bump `spring` from `5.3.23` to `5.3.25`
* Bump `rest-assured` from `5.2.0` to `5.3.0`

## 1.9.0 2022-10-21
* FCFIELDS-29 Upgrade to RMB 35.0.0

## 1.8.0 2022-06-17
* FCFIELDS-23 Upgrade to RMB 34.0.0
* MODCFIELDS-69: Upgrade Spring, RMB, folio-di-support (CVE-2022-22965)
* MODCFIELDS-68: Locale independent unit tests

## 1.7.0 2022-02-23
* FCFIELDS-23 Upgrade to RMB 33.2.6

## 1.6.1 2021-10-04
* FCFIELDS-19 Upgrade to RMB 33.1.1, Vert.x 4.1.4, folio-service-tools 1.7.1

## 1.6.0 2021-06-09
* FCFIELDS-17 Upgrade to RMB 33 and Vert.X 4.1.0.CR1

## 1.5.2 2021-02-18
* Update RMB to v32.1.0
* Add PERSONAL_DATA_DISCLOSURE

## 1.5.1 2020-11-04
* Update RMB to v31.1.5

## 1.5.0 2020-10-05
* FCFIELDS-1 Migrate to JDK 11 and RMB 31.x
* Rename project to "folio-custom-fields"
* MODCFIELDS-56 - Use camel-case names for auto-generated refIds

## 1.4.1 2020-07-06
* MODCFIELDS-57 - Incorrect order of Custom Fields when drag-n-drop fields
* MODCFIELDS-55 - Incorrect order of Custom Fields when adding a 10th field
* MODCFIELDS-42 - Custom Field: Text Field Format Validation
* MODCFIELDS-46 - Custom Field Option: Provide an indication that an option has been saved to a record	

## 1.4.0
* MODCFIELDS-37 Implement repeatable field support
* MODCFIELDS-43 Add support of custom field option IDs
* Upgrade RMB to v39.0.2
* Upgrade folio-service-tools to v1.5.0

## 1.3.0
* Fix invalid jsonb order conversion
* MODCFIELDS-40 Make RefId a read-only field

## 1.2.0
* MODCFIELDS-15 Custom Fields: Text field/area Values Validation
* MODCFIELDS-36 Text field can be created without requried "textField" attribute

## 1.1.1
* MODCFIELDS-27 Add PUT /custom-fields endpoint for updating all fields at once

## 1.0.3
* upgrade to folio-service-tools 1.3.1

## 1.0.2
* MODCFIELDS-29 Upgrade to RMB v29.1.1 

## 1.0.1
* First major release

## 0.1.0
* Initial implementation: basic CRUD operations
* validation of CF definition and assignment
* support CF ordering
