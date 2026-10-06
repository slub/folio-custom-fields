package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.CustomFieldsTestUtil.CUSTOM_FIELDS_PATH;
import static org.folio.CustomFieldsTestUtil.CUSTOM_FIELD_SECTIONS_PATH;
import static org.folio.CustomFieldsTestUtil.STUB_FIELD_ID;
import static org.folio.CustomFieldsTestUtil.USER1_HEADER;
import static org.folio.CustomFieldsTestUtil.deleteAllCustomFieldSections;
import static org.folio.CustomFieldsTestUtil.deleteAllCustomFields;
import static org.folio.CustomFieldsTestUtil.getAllCustomFields;
import static org.folio.CustomFieldsTestUtil.itemResourcePath;
import static org.folio.CustomFieldsTestUtil.mockUserRequests;
import static org.folio.CustomFieldsTestUtil.sectionResourcePath;
import static org.folio.repository.CustomFieldsConstants.CUSTOM_FIELDS_TABLE;
import static org.folio.repository.CustomFieldsConstants.CUSTOM_FIELD_SECTIONS_TABLE;
import static org.folio.test.util.TestUtil.readJsonFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.sqlclient.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldSection;
import org.folio.rest.jaxrs.model.CustomFieldSectionCollection;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.PutCustomFieldSectionCollection;
import org.folio.rest.persist.PostgresClient;
import org.folio.test.util.TestBase;

import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class CustomFieldSectionsImplTest extends TestBase {

  private static final String TENANT = "fs";

  @Before
  public void setUp() throws IOException, URISyntaxException {
    deleteAllCustomFields(vertx);
    deleteAllCustomFieldSections(vertx);
    mockUserRequests();
  }

  @Test
  public void postSection() {
    CustomFieldSection created = createSection("Contact", "user");

    assertEquals("Contact", created.getName());
    assertEquals("user", created.getEntityType());
    CustomFieldSection found = getWithOk(sectionResourcePath(created.getId())).as(CustomFieldSection.class);
    assertEquals(created.getId(), found.getId());
    assertEquals("Contact", found.getName());
  }

  @Test
  public void postSectionShouldKeepGivenId() {
    String id = UUID.randomUUID().toString();
    CustomFieldSection section = new CustomFieldSection().withId(id).withName("Contact").withEntityType("user");

    CustomFieldSection created = postWithStatus(CUSTOM_FIELD_SECTIONS_PATH, Json.encode(section), SC_CREATED)
      .as(CustomFieldSection.class);

    assertEquals(id, created.getId());
  }

  @Test
  public void postSectionShouldReturn422OnDuplicateNameForSameEntityTypeIgnoringCase() {
    createSection("Contact", "user");

    postWithStatus(CUSTOM_FIELD_SECTIONS_PATH, sectionJson("contact", "user"), SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void postSectionShouldAllowSameNameForOtherEntityType() {
    CustomFieldSection userSection = createSection("General", "user");
    CustomFieldSection packageSection = createSection("General", "package");

    assertFalse(userSection.getId().equals(packageSection.getId()));
  }

  @Test
  public void postSectionShouldReturn422WhenNameMissing() {
    String error = postWithStatus(CUSTOM_FIELD_SECTIONS_PATH, "{\"entityType\": \"user\"}", SC_UNPROCESSABLE_ENTITY)
      .asString();
    assertThat(error, containsString("name"));
  }

  @Test
  public void postSectionShouldReturn422WhenEntityTypeMissing() {
    String error = postWithStatus(CUSTOM_FIELD_SECTIONS_PATH, "{\"name\": \"Contact\"}", SC_UNPROCESSABLE_ENTITY)
      .asString();
    assertThat(error, containsString("entityType"));
  }

  @Test
  public void postSectionShouldReturn422WhenNameBlank() {
    String error = postWithStatus(CUSTOM_FIELD_SECTIONS_PATH, sectionJson("  ", "user"), SC_UNPROCESSABLE_ENTITY)
      .asString();
    assertThat(error, containsString("The 'name' of the section must not be blank"));
  }

  @Test
  public void postSectionShouldReturn422WhenNameTooLong() {
    String error = postWithStatus(CUSTOM_FIELD_SECTIONS_PATH, sectionJson("x".repeat(66), "user"),
      SC_UNPROCESSABLE_ENTITY).asString();
    assertThat(error, containsString("The 'name' length cannot be more than 65"));
  }

  @Test
  public void getSectionsFilteredByEntityType() {
    createSection("Contact", "user");
    createSection("Address", "user");
    createSection("Package info", "package");

    CustomFieldSectionCollection sections =
      getWithOk(CUSTOM_FIELD_SECTIONS_PATH + "?query=entityType==user").as(CustomFieldSectionCollection.class);

    assertEquals(2, (int) sections.getTotalRecords());
    assertThat(sections.getCustomFieldSections().stream().map(CustomFieldSection::getName).toList(),
      containsInAnyOrder("Contact", "Address"));
  }

  @Test
  public void postSectionShouldAppendOrderPerEntityType() {
    CustomFieldSection first = createSection("Contact", "user");
    CustomFieldSection second = createSection("Address", "user");
    CustomFieldSection otherEntityType = createSection("Package info", "package");

    assertEquals(1, (int) first.getOrder());
    assertEquals(2, (int) second.getOrder());
    assertEquals(1, (int) otherEntityType.getOrder());
  }

  @Test
  public void postSectionShouldIgnoreGivenOrder() {
    createSection("Address", "user");

    CustomFieldSection created = createSection("Contact", "user", 7);

    assertEquals(2, (int) created.getOrder());
  }

  @Test
  public void getSectionsShouldBeSortedByOrder() {
    CustomFieldSection contact = createSection("Contact", "user");
    CustomFieldSection address = createSection("Address", "user");
    CustomFieldSection general = createSection("General", "user");
    putSectionsWithNoContent("user", address, general, contact);

    assertThat(getSectionNames("?query=entityType==user"), contains("Address", "General", "Contact"));
  }

  @Test
  public void getSectionsShouldSortByOrderAfterGivenSortBy() {
    createSection("Contact", "user");
    createSection("Package info", "package");
    createSection("Address", "user");

    assertThat(getSectionNames("?query=cql.allRecords=1 sortby entityType/sort.descending"),
      contains("Contact", "Address", "Package info"));
  }

  @Test
  public void getSectionByIdShouldReturn404WhenNotFound() {
    String error = getWithStatus(sectionResourcePath(STUB_FIELD_ID), SC_NOT_FOUND).asString();
    assertThat(error, containsString("CustomFieldSection not found by id"));
  }

  @Test
  public void putSection() {
    CustomFieldSection created = createSection("Contact", "user");

    putWithNoContent(sectionResourcePath(created.getId()), sectionJson("Contacts", "user"));

    CustomFieldSection found = getWithOk(sectionResourcePath(created.getId())).as(CustomFieldSection.class);
    assertEquals("Contacts", found.getName());
  }

  @Test
  public void putSectionShouldKeepOrder() {
    createSection("Address", "user");
    CustomFieldSection created = createSection("Contact", "user");

    putWithNoContent(sectionResourcePath(created.getId()), sectionJson("Contacts", "user", 7));

    assertEquals(2, (int) getSection(created.getId()).getOrder());
  }

  @Test
  public void putSectionShouldAppendToNewEntityTypeAndRenumberOldOne() {
    CustomFieldSection first = createSection("Contact", "user");
    CustomFieldSection moved = createSection("Address", "user");
    CustomFieldSection last = createSection("General", "user");
    createSection("Package info", "package");

    putWithNoContent(sectionResourcePath(moved.getId()), sectionJson("Address", "package"));

    assertEquals(2, (int) getSection(moved.getId()).getOrder());
    assertEquals(1, (int) getSection(first.getId()).getOrder());
    assertEquals(2, (int) getSection(last.getId()).getOrder());
  }

  @Test
  public void putSectionShouldReturn404WhenNotFound() {
    putWithStatus(sectionResourcePath(STUB_FIELD_ID), sectionJson("Contacts", "user"), SC_NOT_FOUND);
  }

  @Test
  public void putSectionShouldReturn422WhenBodyIdDiffersFromPathId() {
    CustomFieldSection created = createSection("Contact", "user");
    CustomFieldSection other = new CustomFieldSection().withId(STUB_FIELD_ID).withName("Contact").withEntityType("user");

    String error = putWithStatus(sectionResourcePath(created.getId()), Json.encode(other), SC_UNPROCESSABLE_ENTITY)
      .asString();
    assertThat(error, containsString("does not match the id"));
  }

  @Test
  public void putSectionShouldReturn422OnDuplicateName() {
    createSection("Contact", "user");
    CustomFieldSection address = createSection("Address", "user");

    putWithStatus(sectionResourcePath(address.getId()), sectionJson("CONTACT", "user"), SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void putSectionShouldChangeEntityTypeWhenUnused() {
    CustomFieldSection created = createSection("Contact", "user");

    putWithNoContent(sectionResourcePath(created.getId()), sectionJson("Contact", "package"));

    assertEquals("package",
      getWithOk(sectionResourcePath(created.getId())).as(CustomFieldSection.class).getEntityType());
  }

  @Test
  public void putSectionShouldReturn422WhenEntityTypeChangesWhileInUse() throws IOException, URISyntaxException {
    CustomFieldSection section = createSection("Contact", "user");
    CustomField field = createField("Department", section.getId());

    Errors errors = putWithStatus(sectionResourcePath(section.getId()), sectionJson("Contact", "package"),
      SC_UNPROCESSABLE_ENTITY).as(Errors.class);

    Error error = errors.getErrors().get(0);
    assertEquals("sectionInUse", error.getCode());
    assertThat(error.getMessage(), containsString("Section 'Contact' cannot change entityType"));
    assertEquals(field.getId(), error.getParameters().get(0).getKey());
    assertEquals("user",
      getWithOk(sectionResourcePath(section.getId())).as(CustomFieldSection.class).getEntityType());
  }

  @Test
  public void deleteSection() {
    CustomFieldSection created = createSection("Contact", "user");

    deleteWithNoContent(sectionResourcePath(created.getId()));

    getWithStatus(sectionResourcePath(created.getId()), SC_NOT_FOUND);
  }

  @Test
  public void deleteSectionShouldRenumberRemainingSectionsOfEntityType() {
    CustomFieldSection first = createSection("Contact", "user");
    CustomFieldSection deleted = createSection("Address", "user");
    CustomFieldSection last = createSection("General", "user");
    CustomFieldSection otherEntityType = createSection("Package info", "package");

    deleteWithNoContent(sectionResourcePath(deleted.getId()));

    assertEquals(1, (int) getSection(first.getId()).getOrder());
    assertEquals(2, (int) getSection(last.getId()).getOrder());
    assertEquals(1, (int) getSection(otherEntityType.getId()).getOrder());
  }

  @Test
  public void putSectionsShouldSetOrderByPosition() {
    CustomFieldSection contact = createSection("Contact", "user");
    CustomFieldSection address = createSection("Address", "user");
    CustomFieldSection general = createSection("General", "user");

    putSectionsWithNoContent("user", general.withOrder(1), contact.withOrder(5), address.withOrder(1));

    assertEquals(1, (int) getSection(general.getId()).getOrder());
    assertEquals(2, (int) getSection(contact.getId()).getOrder());
    assertEquals(3, (int) getSection(address.getId()).getOrder());
  }

  @Test
  public void putSectionsShouldCreateUpdateAndDeleteSections() {
    CustomFieldSection kept = createSection("Contact", "user");
    CustomFieldSection removed = createSection("Address", "user");
    CustomFieldSection added = new CustomFieldSection().withName("General").withEntityType("user");

    putSectionsWithNoContent("user", added, kept.withName("Contacts"));

    assertThat(getSectionNames("?query=entityType==user"), contains("General", "Contacts"));
    getWithStatus(sectionResourcePath(removed.getId()), SC_NOT_FOUND);
    assertEquals(2, (int) getSection(kept.getId()).getOrder());
  }

  @Test
  public void putSectionsShouldNotChangeOtherEntityTypes() {
    CustomFieldSection packageSection = createSection("Package info", "package");
    createSection("Contact", "user");

    putSectionsWithNoContent("user");

    assertThat(getSectionNames(""), contains("Package info"));
    assertEquals(1, (int) getSection(packageSection.getId()).getOrder());
  }

  @Test
  public void putSectionsShouldReturn422WhenDeletedSectionIsAssigned() throws IOException, URISyntaxException {
    CustomFieldSection assigned = createSection("Contact", "user");
    CustomFieldSection kept = createSection("Address", "user");
    CustomField field = createField("Phone", assigned.getId());

    Errors errors = putWithStatus(CUSTOM_FIELD_SECTIONS_PATH, sectionsJson("user", kept.withName("Addresses")),
      SC_UNPROCESSABLE_ENTITY).as(Errors.class);

    assertEquals("sectionInUse", errors.getErrors().get(0).getCode());
    assertEquals(field.getId(), errors.getErrors().get(0).getParameters().get(0).getKey());
    assertThat(getSectionNames("?query=entityType==user"), contains("Contact", "Address"));
  }

  @Test
  public void putSectionsShouldReturn422WhenEntityTypeDiffers() {
    CustomFieldSection section = new CustomFieldSection().withName("Contact").withEntityType("package");

    String error = putWithStatus(CUSTOM_FIELD_SECTIONS_PATH, sectionsJson("user", section), SC_UNPROCESSABLE_ENTITY)
      .asString();

    assertThat(error, containsString("does not match entityType"));
  }

  @Test
  public void putSectionsShouldReturn422OnDuplicateIds() {
    CustomFieldSection section = createSection("Contact", "user");
    CustomFieldSection copy = new CustomFieldSection().withId(section.getId()).withName("Address").withEntityType("user");

    String error = putWithStatus(CUSTOM_FIELD_SECTIONS_PATH, sectionsJson("user", section, copy),
      SC_UNPROCESSABLE_ENTITY).asString();

    assertThat(error, containsString("Duplicate section id"));
  }

  @Test
  public void putSectionsShouldReturn422OnDuplicateNamesIgnoringCase() {
    String error = putWithStatus(CUSTOM_FIELD_SECTIONS_PATH, sectionsJson("user",
        new CustomFieldSection().withName("Contact").withEntityType("user"),
        new CustomFieldSection().withName("contact").withEntityType("user")),
      SC_UNPROCESSABLE_ENTITY).asString();

    assertThat(error, containsString("Duplicate section name"));
  }

  @Test
  public void deleteSectionShouldReturn404WhenNotFound() {
    String error = deleteWithStatus(sectionResourcePath(STUB_FIELD_ID), SC_NOT_FOUND).asString();
    assertThat(error, containsString("CustomFieldSection not found by id"));
  }

  @Test
  public void deleteSectionShouldReturn422WhenAssignedToField() throws IOException, URISyntaxException {
    CustomFieldSection section = createSection("Contact", "user");
    CustomField field = createField("Department", section.getId());

    Errors errors = deleteWithStatus(sectionResourcePath(section.getId()), SC_UNPROCESSABLE_ENTITY).as(Errors.class);

    Error error = errors.getErrors().get(0);
    assertEquals("sectionInUse", error.getCode());
    assertEquals("Section 'Contact' cannot be deleted: it is used by 1 custom field(s). "
      + "Move or unassign them first.", error.getMessage());
    assertThat(error.getParameters(), hasSize(1));
    assertEquals(field.getId(), error.getParameters().get(0).getKey());
    assertEquals("Department", error.getParameters().get(0).getValue());

    // nothing changed: the section still exists and the field is still assigned
    getWithStatus(sectionResourcePath(section.getId()), SC_OK);
    assertEquals(section.getId(), getAllCustomFields(vertx).get(0).getSectionId());
  }

  @Test
  public void deleteSectionShouldListAllAssignedFields() throws IOException, URISyntaxException {
    CustomFieldSection section = createSection("Contact", "user");
    CustomField first = createField("Department", section.getId());
    CustomField second = createField("Phone", section.getId());

    Errors errors = deleteWithStatus(sectionResourcePath(section.getId()), SC_UNPROCESSABLE_ENTITY).as(Errors.class);

    List<Parameter> parameters = errors.getErrors().get(0).getParameters();
    assertThat(parameters.stream().map(Parameter::getKey).toList(),
      containsInAnyOrder(first.getId(), second.getId()));
    assertThat(errors.getErrors().get(0).getMessage(), containsString("used by 2 custom field(s)"));
  }

  @Test
  public void deleteSectionAfterManuallyUnassigningAllFields() throws IOException, URISyntaxException {
    CustomFieldSection section = createSection("Contact", "user");
    CustomField first = createField("Department", section.getId());
    CustomField second = createField("Phone", section.getId());

    putWithNoContent(itemResourcePath(first.getId()), Json.encode(first.withSectionId(null)), USER1_HEADER);

    Errors errors = deleteWithStatus(sectionResourcePath(section.getId()), SC_UNPROCESSABLE_ENTITY).as(Errors.class);
    List<Parameter> parameters = errors.getErrors().get(0).getParameters();
    assertThat(parameters, hasSize(1));
    assertEquals(second.getId(), parameters.get(0).getKey());

    putWithNoContent(itemResourcePath(second.getId()), Json.encode(second.withSectionId(null)), USER1_HEADER);

    deleteWithNoContent(sectionResourcePath(section.getId()));
    getWithStatus(sectionResourcePath(section.getId()), SC_NOT_FOUND);
    assertThat(getAllCustomFields(vertx), hasSize(2));
  }

  @Test
  public void deleteSectionAfterMovingFieldToOtherSection() throws IOException, URISyntaxException {
    CustomFieldSection oldSection = createSection("Contact", "user");
    CustomFieldSection newSection = createSection("Contacts", "user");
    CustomField field = createField("Department", oldSection.getId());

    putWithNoContent(itemResourcePath(field.getId()), Json.encode(field.withSectionId(newSection.getId())),
      USER1_HEADER);

    deleteWithNoContent(sectionResourcePath(oldSection.getId()));
    assertEquals(newSection.getId(), getAllCustomFields(vertx).get(0).getSectionId());
  }

  /**
   * A transaction that assigns a field to the section holds a FOR SHARE lock on the section row. The section delete
   * (FOR UPDATE) must wait for it, and then see the assigned field and fail with sectionInUse.
   */
  @Test
  public void deleteSectionShouldWaitForConcurrentFieldAssignment() throws Exception {
    CustomFieldSection section = createSection("Contact", "user");
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT);
    String sectionsTable = PostgresClient.convertToPsqlStandard(TENANT) + "." + CUSTOM_FIELD_SECTIONS_TABLE;
    CustomField field = readJsonFile("fields/post/postCustomField.json", CustomField.class)
      .withId(UUID.randomUUID().toString())
      .withRefId("department")
      .withSectionId(section.getId());

    Promise<Void> lockAcquired = Promise.promise();
    Promise<Void> releaseLock = Promise.promise();
    Future<String> assignment = pgClient.withTrans(conn -> conn
      .execute("SELECT id FROM " + sectionsTable + " WHERE id = $1 FOR SHARE", Tuple.of(UUID.fromString(section.getId())))
      .compose(rows -> {
        lockAcquired.complete();
        return releaseLock.future();
      })
      .compose(v -> conn.save(CUSTOM_FIELDS_TABLE, field.getId(), field)));
    lockAcquired.future().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

    CompletableFuture<ExtractableResponse<Response>> delete = CompletableFuture.supplyAsync(
      () -> deleteWithStatus(sectionResourcePath(section.getId()), SC_UNPROCESSABLE_ENTITY));
    TimeUnit.SECONDS.sleep(1);
    assertFalse("delete must wait for the transaction that assigns the section", delete.isDone());

    releaseLock.complete();
    assignment.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

    Errors errors = delete.get(10, TimeUnit.SECONDS).as(Errors.class);
    assertEquals("sectionInUse", errors.getErrors().get(0).getCode());
    assertEquals(field.getId(), errors.getErrors().get(0).getParameters().get(0).getKey());
    getWithStatus(sectionResourcePath(section.getId()), SC_OK);
  }

  private CustomFieldSection createSection(String name, String entityType) {
    return postWithStatus(CUSTOM_FIELD_SECTIONS_PATH, sectionJson(name, entityType), SC_CREATED)
      .as(CustomFieldSection.class);
  }

  private CustomFieldSection createSection(String name, String entityType, int order) {
    return postWithStatus(CUSTOM_FIELD_SECTIONS_PATH, sectionJson(name, entityType, order), SC_CREATED)
      .as(CustomFieldSection.class);
  }

  private CustomFieldSection getSection(String id) {
    return getWithOk(sectionResourcePath(id)).as(CustomFieldSection.class);
  }

  private List<String> getSectionNames(String query) {
    return getWithOk(CUSTOM_FIELD_SECTIONS_PATH + query).as(CustomFieldSectionCollection.class)
      .getCustomFieldSections().stream().map(CustomFieldSection::getName).toList();
  }

  private void putSectionsWithNoContent(String entityType, CustomFieldSection... sections) {
    putWithNoContent(CUSTOM_FIELD_SECTIONS_PATH, sectionsJson(entityType, sections));
  }

  private String sectionsJson(String entityType, CustomFieldSection... sections) {
    return Json.encode(new PutCustomFieldSectionCollection()
      .withEntityType(entityType)
      .withCustomFieldSections(List.of(sections)));
  }

  private CustomField createField(String name, String sectionId) throws IOException, URISyntaxException {
    CustomField field = readJsonFile("fields/post/postCustomField.json", CustomField.class)
      .withName(name)
      .withSectionId(sectionId);
    return postWithStatus(CUSTOM_FIELDS_PATH, Json.encode(field), SC_CREATED, USER1_HEADER).as(CustomField.class);
  }

  private String sectionJson(String name, String entityType) {
    return Json.encode(new CustomFieldSection().withName(name).withEntityType(entityType));
  }

  private String sectionJson(String name, String entityType, int order) {
    return Json.encode(new CustomFieldSection().withName(name).withEntityType(entityType).withOrder(order));
  }
}
