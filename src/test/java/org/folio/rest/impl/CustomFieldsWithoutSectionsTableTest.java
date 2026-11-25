package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.CustomFieldsTestUtil.CUSTOM_FIELDS_PATH;
import static org.folio.CustomFieldsTestUtil.USER1_HEADER;
import static org.folio.CustomFieldsTestUtil.deleteAllCustomFields;
import static org.folio.CustomFieldsTestUtil.getAllCustomFields;
import static org.folio.CustomFieldsTestUtil.itemResourcePath;
import static org.folio.CustomFieldsTestUtil.mockUserRequests;
import static org.folio.repository.CustomFieldsConstants.CUSTOM_FIELD_SECTIONS_TABLE;
import static org.folio.test.util.TestUtil.readJsonFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.vertx.core.json.Json;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldCollection;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.PutCustomFieldCollection;
import org.folio.rest.persist.PostgresClient;
import org.folio.test.util.TestBase;

/**
 * A host module that upgrades to this library version but doesn't add the {@code create_custom_field_sections_table.sql}
 * snippet has no {@code custom_field_sections} table. The existing {@code /custom-fields} API must keep working for
 * fields without a {@code sectionId}.
 */
@RunWith(VertxUnitRunner.class)
public class CustomFieldsWithoutSectionsTableTest extends TestBase {

  private static final String TENANT = "fs";
  private static final String HIDDEN_SECTIONS_TABLE = CUSTOM_FIELD_SECTIONS_TABLE + "_hidden";

  @Before
  public void setUp() throws Exception {
    deleteAllCustomFields(vertx);
    mockUserRequests();
    renameTable(CUSTOM_FIELD_SECTIONS_TABLE, HIDDEN_SECTIONS_TABLE);
  }

  @After
  public void tearDown() throws Exception {
    renameTable(HIDDEN_SECTIONS_TABLE, CUSTOM_FIELD_SECTIONS_TABLE);
    deleteAllCustomFields(vertx);
  }

  @Test
  public void postAndGetCustomFieldsWithoutSectionsTable() throws IOException, URISyntaxException {
    CustomField created = createField("Department");

    CustomField found = getWithOk(itemResourcePath(created.getId())).as(CustomField.class);
    assertEquals("Department", found.getName());
    assertEquals(1, (int) found.getOrder());

    List<String> names = getWithOk(CUSTOM_FIELDS_PATH).as(CustomFieldCollection.class)
      .getCustomFields().stream().map(CustomField::getName).toList();
    assertThat(names, contains("Department"));
  }

  @Test
  public void putCustomFieldWithoutSectionsTable() throws IOException, URISyntaxException {
    CustomField created = createField("Department");
    CustomField update = readJsonFile("fields/put/putCustomField.json", CustomField.class);

    putWithNoContent(itemResourcePath(created.getId()), Json.encode(update), USER1_HEADER);

    CustomField found = getWithOk(itemResourcePath(created.getId())).as(CustomField.class);
    assertEquals("Department 2", found.getName());
  }

  @Test
  public void putCustomFieldCollectionWithoutSectionsTable() throws IOException, URISyntaxException {
    CustomField kept = createField("Department");
    createField("Removed");
    PutCustomFieldCollection request =
      readJsonFile("fields/put/putCustomFieldCollection.json", PutCustomFieldCollection.class);
    request.getCustomFields().get(1).setId(kept.getId());

    putWithNoContent(CUSTOM_FIELDS_PATH, Json.encode(request), USER1_HEADER);

    List<String> names = getAllCustomFields(vertx).stream().map(CustomField::getName).toList();
    assertThat(names, containsInAnyOrder("New Expiration Date", "Department 2"));
  }

  @Test
  public void deleteCustomFieldWithoutSectionsTable() throws IOException, URISyntaxException {
    CustomField first = createField("Department");
    CustomField second = createField("Office");

    deleteWithNoContent(itemResourcePath(first.getId()));

    List<CustomField> remaining = getAllCustomFields(vertx);
    remaining.sort(Comparator.comparingInt(CustomField::getOrder));
    assertEquals(1, remaining.size());
    assertEquals(second.getId(), remaining.get(0).getId());
    assertEquals(1, (int) remaining.get(0).getOrder());
  }

  @Test
  public void postCustomFieldWithSectionIdShouldReturn422WithoutSectionsTable() throws IOException, URISyntaxException {
    CustomField field = readJsonFile("fields/post/postCustomField.json", CustomField.class)
      .withSectionId(UUID.randomUUID().toString());

    assertSectionsNotEnabled(postWithStatus(CUSTOM_FIELDS_PATH, Json.encode(field), SC_UNPROCESSABLE_ENTITY,
      USER1_HEADER).as(Error.class));
    assertThat(getAllCustomFields(vertx), empty());
  }

  @Test
  public void putCustomFieldWithSectionIdShouldReturn422WithoutSectionsTable() throws IOException, URISyntaxException {
    CustomField created = createField("Department");
    CustomField update = readJsonFile("fields/put/putCustomField.json", CustomField.class)
      .withSectionId(UUID.randomUUID().toString());

    assertSectionsNotEnabled(putWithStatus(itemResourcePath(created.getId()), Json.encode(update),
      SC_UNPROCESSABLE_ENTITY, USER1_HEADER).as(Error.class));
    assertEquals("Department", getAllCustomFields(vertx).get(0).getName());
  }

  @Test
  public void putCustomFieldCollectionWithSectionIdShouldReturn422WithoutSectionsTable()
      throws IOException, URISyntaxException {
    createField("Department");
    PutCustomFieldCollection request =
      readJsonFile("fields/put/putCustomFieldCollection.json", PutCustomFieldCollection.class);
    request.getCustomFields().get(0).setSectionId(UUID.randomUUID().toString());

    assertSectionsNotEnabled(putWithStatus(CUSTOM_FIELDS_PATH, Json.encode(request), SC_UNPROCESSABLE_ENTITY,
      USER1_HEADER).as(Error.class));
    List<String> names = getAllCustomFields(vertx).stream().map(CustomField::getName).toList();
    assertThat(names, contains("Department"));
  }

  private void assertSectionsNotEnabled(Error error) {
    assertThat(error.getMessage(), containsString("Custom field sections are not enabled for this module"));
    assertEquals("sectionId", error.getParameters().get(0).getKey());
  }

  private CustomField createField(String name) throws IOException, URISyntaxException {
    CustomField field = readJsonFile("fields/post/postCustomField.json", CustomField.class).withName(name);
    return postWithStatus(CUSTOM_FIELDS_PATH, Json.encode(field), SC_CREATED, USER1_HEADER).as(CustomField.class);
  }

  private void renameTable(String from, String to) throws Exception {
    String schema = PostgresClient.convertToPsqlStandard(TENANT);
    PostgresClient.getInstance(vertx)
      .execute("ALTER TABLE " + schema + "." + from + " RENAME TO " + to)
      .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
  }
}
