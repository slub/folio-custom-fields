package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.CustomFieldsTestUtil.CUSTOM_FIELDS_PATH;
import static org.folio.CustomFieldsTestUtil.STUB_FIELD_ID;
import static org.folio.CustomFieldsTestUtil.USER1_HEADER;
import static org.folio.CustomFieldsTestUtil.USER1_ID;
import static org.folio.CustomFieldsTestUtil.USER2_HEADER;
import static org.folio.CustomFieldsTestUtil.USER2_ID;
import static org.folio.CustomFieldsTestUtil.USER3_ID;
import static org.folio.CustomFieldsTestUtil.USER4_ID;
import static org.folio.CustomFieldsTestUtil.USER5_ID;
import static org.folio.CustomFieldsTestUtil.CUSTOM_FIELD_SECTIONS_PATH;
import static org.folio.CustomFieldsTestUtil.deleteAllCustomFieldSections;
import static org.folio.CustomFieldsTestUtil.deleteAllCustomFields;
import static org.folio.CustomFieldsTestUtil.getAllCustomFields;
import static org.folio.CustomFieldsTestUtil.itemOptionStatResourcePath;
import static org.folio.CustomFieldsTestUtil.itemResourcePath;
import static org.folio.CustomFieldsTestUtil.itemStatResourcePath;
import static org.folio.CustomFieldsTestUtil.mockUserRequests;
import static org.folio.test.util.TestUtil.readFile;
import static org.folio.test.util.TestUtil.readJsonFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldCollection;
import org.folio.rest.jaxrs.model.CustomFieldOptionStatistic;
import org.folio.rest.jaxrs.model.CustomFieldSection;
import org.folio.rest.jaxrs.model.CustomFieldStatistic;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.PutCustomFieldCollection;
import org.folio.rest.jaxrs.model.TextField;
import org.folio.test.util.TestBase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.restassured.http.Header;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class CustomFieldsImplTest extends TestBase {

  @Before
  public void setUp() throws IOException, URISyntaxException {
    deleteAllCustomFields(vertx);
    deleteAllCustomFieldSections(vertx);
    mockUserRequests();
  }

  @Test
  public void postCustomFields() throws IOException, URISyntaxException {
    CustomField customField = createCustomField(readFile("fields/post/singleSelect/postValidSingleSelect.json"));

    assertEquals("favoriteFood", customField.getRefId());
    assertThat(customField.getSelectField().getOptions().getValues(),
      hasItem(hasProperty("id", equalTo("opt_2")))
    );

    Metadata noteTypeMetadata = customField.getMetadata();

    assertEquals(USER1_ID, noteTypeMetadata.getCreatedByUserId());
    assertEquals("u1", noteTypeMetadata.getCreatedByUsername());
  }

  @Test
  public void postTextboxCustomFieldWithEmptyFormat() throws IOException, URISyntaxException {
    CustomField customField = createCustomField(readFile("fields/post/textbox/postTextBoxShort.json"));
    assertNotNull(customField.getTextField());
    assertEquals(TextField.FieldFormat.TEXT, customField.getTextField().getFieldFormat());
  }

  @Test
  public void postTextboxCustomFieldWithEmailFormat() throws IOException, URISyntaxException {
    CustomField customField = createCustomField(readFile("fields/post/textbox/postTextBoxEmailShort.json"));
    assertNotNull(customField.getTextField());
    assertEquals(TextField.FieldFormat.EMAIL, customField.getTextField().getFieldFormat());
  }

  @Test
  public void postDisplayInAccordion() throws IOException, URISyntaxException {
    var customFieldJson = readFile("fields/post/textbox/postDisplayInAccordion.json");
    CustomField customField = createCustomField(customFieldJson);
    assertNotNull(customField.getDisplayInAccordion());
    assertEquals("fees_fines", customField.getDisplayInAccordion());
  }

  @Test
  public void shouldCreateTwoRefIdOnPostCustomFieldWithSameName() throws IOException, URISyntaxException {
    CustomField cfWithAccentName1 = createCustomField(readFile("fields/post/postCustomFieldWithAccentName.json"));
    CustomField cfWithAccentName2 = createCustomField(readFile("fields/post/postCustomFieldWithAccentNameSecond.json"));
    assertEquals("thisIsATrickyString", cfWithAccentName1.getRefId());
    assertEquals("thisIsATrickyString_2", cfWithAccentName2.getRefId());
  }

  @Test
  public void shouldCreateTwoRefIdOnPostCustomFieldWithSameName2() throws IOException, URISyntaxException {
    CustomField cfWithAccentName = createCustomField(readFile("fields/post/postCustomFieldWithAccentName.json"));
    CustomField cfWithHalfName = createCustomField(readFile("fields/post/postCustomFieldHalfName2.json"));
    assertEquals("thisIsATrickyString", cfWithAccentName.getRefId());
    assertEquals("thisIsA", cfWithHalfName.getRefId());
  }

  @Test
  public void shouldCreateNonEmptyRefIdForNonRomanName() throws IOException, URISyntaxException {
    CustomField field = createCustomField(readFile("fields/post/postCustomFieldWithNonRomanName.json"));
    String expectedRefId = Base64.getEncoder().withoutPadding()
      .encodeToString("汉字".getBytes(StandardCharsets.UTF_8));
    assertNotNull(field.getRefId());
    assertEquals(expectedRefId, field.getRefId());
  }

  @Test
  public void shouldCreateSameBaseRefIdForTwoFieldsWithSameNonRomanName() throws IOException, URISyntaxException {
    String fileContent = readFile("fields/post/postCustomFieldWithNonRomanName.json");
    CustomField first = createCustomField(fileContent);
    CustomField second = createCustomField(fileContent);
    String expectedBase = Base64.getEncoder().withoutPadding()
      .encodeToString("汉字".getBytes(StandardCharsets.UTF_8));
    assertEquals(expectedBase, first.getRefId());
    assertEquals(expectedBase + "_2", second.getRefId());
  }

  @Test
  public void shouldCreateCustomFieldWithRefIdsOnPost() throws IOException, URISyntaxException {

    for (int i = 0; i < 10; i++) {
      createCustomField(readFile("fields/post/postCustomField.json"));
    }

    CustomFieldCollection fields = getWithOk(CUSTOM_FIELDS_PATH + "?limit=500&offset0").as(CustomFieldCollection.class);
    final List<CustomField> customFields = fields.getCustomFields();
    for (int i = 1; i < customFields.size(); i++) {
      assertTrue(customFields.get(i).getRefId().contains(String.valueOf(i + 1)));
    }
  }


  @Test
  public void shouldCreateCustomFieldWithLastOrderOnPost() throws IOException, URISyntaxException {
    CustomField customField1 = createCustomField(readFile("fields/post/postCustomField.json"));
    CustomField customField2 = createCustomField(readFile("fields/post/postCustomField2.json"));
    assertEquals(1, (int) customField1.getOrder());
    assertEquals(2, (int) customField2.getOrder());
  }

  @Test
  public void shouldCreateCustomFieldWithOrderOnPost() throws IOException, URISyntaxException {

    for (int i = 0; i < 10; i++) {
      createCustomField(readFile("fields/post/postCustomField.json"));
    }

    CustomFieldCollection fields = getWithOk(CUSTOM_FIELDS_PATH).as(CustomFieldCollection.class);
    final List<CustomField> customFields = fields.getCustomFields();
    for (int i = 0; i < customFields.size(); i++) {
      assertEquals(i + 1, (int) customFields.get(i).getOrder());
    }
  }

  @Test
  public void shouldNotCreateCustomFieldWhenNameIsTooLongOnPost() throws IOException, URISyntaxException {
    String customField = readFile("fields/post/postCustomNameWithTooLongName.json");
    Error error = postWithStatus(CUSTOM_FIELDS_PATH, customField, SC_UNPROCESSABLE_ENTITY, USER1_HEADER).as(Error.class);
    assertThat(error.getMessage(), containsString("The 'name' length cannot be more than"));
  }

  @Test
  public void shouldReturn422WhenNameIsNullOnPost() throws IOException, URISyntaxException {
    String customField = readFile("fields/post/postCustomFieldEmptyName.json");
    String error = postWithStatus(CUSTOM_FIELDS_PATH, customField, SC_UNPROCESSABLE_ENTITY, USER1_HEADER).asString();
    assertThat(error, containsString("NotNull.message"));
  }

  @Test
  public void shouldReturn422WhenTypeIsNullOnPost() throws IOException, URISyntaxException {
    String customField = readFile("fields/post/postCustomFieldEmptyType.json");
    String error = postWithStatus(CUSTOM_FIELDS_PATH, customField, SC_UNPROCESSABLE_ENTITY).asString();
    assertThat(error, containsString("NotNull.message"));
  }

  @Test
  public void shouldReturn422WhenEntityTypeIsNullOnPost() throws IOException, URISyntaxException {
    String customField = readFile("fields/post/postCustomFieldEmptyEntityType.json");
    String error = postWithStatus(CUSTOM_FIELDS_PATH, customField, SC_UNPROCESSABLE_ENTITY).asString();
    assertThat(error, containsString("NotNull.message"));
  }

  @Test
  public void shouldReturnErrorIfHelpTextTooLong() throws IOException, URISyntaxException {
    String customField = readFile("fields/post/postCustomFieldHelpTextInvalid.json");
    Error error = postWithStatus(CUSTOM_FIELDS_PATH, customField, SC_UNPROCESSABLE_ENTITY).as(Error.class);
    assertThat(error.getMessage(), containsString("The 'helpText' length cannot be more than"));
  }

  @Test
  public void shouldReturnErrorIfInvalidCustomFieldType() throws IOException, URISyntaxException {
    String customField = readFile("fields/post/postInvalidCustomField.json");
    String error = postWithStatus(CUSTOM_FIELDS_PATH, customField, SC_BAD_REQUEST).asString();
    assertThat(error, containsString("Cannot construct instance"));
  }

  @Test
  public void shouldReturn401WhenNoUserHeader() throws IOException, URISyntaxException {
    String customField = readFile("fields/post/postCustomFieldHalfName.json");
    String error = postWithStatus(CUSTOM_FIELDS_PATH, customField, SC_UNAUTHORIZED).asString();
    assertThat(error, containsString("Unauthorized"));
  }

  @Test
  public void shouldReturn422IfOptionIdIsInvalid() throws IOException, URISyntaxException {
    CustomField customField = readJsonFile("fields/post/singleSelect/postValidSingleSelect.json", CustomField.class);
    customField.getSelectField().getOptions().getValues().get(0).setId("opt_12121212121221");
    String error = postWithStatus(CUSTOM_FIELDS_PATH, Json.encode(customField), SC_UNPROCESSABLE_ENTITY).asString();
    assertThat(error, allOf(containsString("\\\"opt_\\\\d{1,5}\\\""), containsString("Pattern.message")));
  }

  @Test
  public void shouldReturn401WhenUserUnauthorized() throws IOException, URISyntaxException {
    Header userWithoutPermission = new Header(XOkapiHeaders.USER_ID, USER3_ID);
    String cfWithHalfName = readFile("fields/post/postCustomFieldHalfName.json");
    String error = postWithStatus(CUSTOM_FIELDS_PATH, cfWithHalfName, SC_UNAUTHORIZED, userWithoutPermission).asString();
    assertThat(error, containsString("Unauthorized"));
  }

  @Test
  public void shouldReturn404WhenUserNotFound() throws IOException, URISyntaxException {
    Header userWithoutPermission = new Header(XOkapiHeaders.USER_ID, USER4_ID);
    String cfWithHalfName = readFile("fields/post/postCustomFieldHalfName.json");
    String error = postWithStatus(CUSTOM_FIELDS_PATH, cfWithHalfName, SC_NOT_FOUND, userWithoutPermission).asString();
    assertThat(error, containsString("User not found"));
  }

  @Test
  public void shouldReturn500WhenUserIsFailedToFind() throws IOException, URISyntaxException {
    Header userWithoutPermission = new Header(XOkapiHeaders.USER_ID, USER5_ID);
    String cfWithHalfName = readFile("fields/post/postCustomFieldHalfName.json");
    String error = postWithStatus(CUSTOM_FIELDS_PATH, cfWithHalfName,
      SC_INTERNAL_SERVER_ERROR, userWithoutPermission).asString();
    assertThat(error, containsString("Internal Server Error, Please contact System Administrator or try again"));
  }

  @Test
  public void shouldReturnAllFieldsOnGetSortedByOrder() throws IOException, URISyntaxException {
    createFieldsMultipleEntityTypes();
    CustomFieldCollection fields = getWithOk(CUSTOM_FIELDS_PATH).as(CustomFieldCollection.class);
    assertEquals(2, fields.getCustomFields().size());
    assertThat(fields.getCustomFields().get(0), is(allOf(
      hasProperty("name", is("Department")),
      hasProperty("helpText", is("Provide a department")),
      hasProperty("entityType", is("user")),
      hasProperty("order", is(1))
    )));
    assertThat(fields.getCustomFields().get(1), is(allOf(
      hasProperty("name", is("Expiration Date")),
      hasProperty("helpText", is("Set expiration date")),
      hasProperty("entityType", is("package")),
      hasProperty("order", is(2))
    )));
  }

  @Test
  public void shouldReturnFieldsByName() throws IOException, URISyntaxException {
    createFieldsMultipleEntityTypes();
    String resourcePath = CUSTOM_FIELDS_PATH + "?query=name==Department";
    CustomFieldCollection fields = getWithOk(resourcePath).as(CustomFieldCollection.class);
    assertEquals(1, fields.getCustomFields().size());
    assertEquals(1, (int) fields.getTotalRecords());
    assertEquals("Department", fields.getCustomFields().get(0).getName());
    assertEquals("Provide a department", fields.getCustomFields().get(0).getHelpText());
    assertEquals("user", fields.getCustomFields().get(0).getEntityType());
  }

  @Test
  public void shouldReturnFieldsByEntityType() throws IOException, URISyntaxException {
    createFieldsMultipleEntityTypes();
    String resourcePath = CUSTOM_FIELDS_PATH + "?query=entityType==package";
    CustomFieldCollection fields = getWithOk(resourcePath).as(CustomFieldCollection.class);
    assertEquals(1, fields.getCustomFields().size());
    assertEquals(1, (int) fields.getTotalRecords());
    assertEquals("Expiration Date", fields.getCustomFields().get(0).getName());
    assertEquals("Set expiration date", fields.getCustomFields().get(0).getHelpText());
  }

  @Test
  public void shouldReturnFieldsWithPagination() throws IOException, URISyntaxException {
    createFieldsMultipleEntityTypes();
    String resourcePath = CUSTOM_FIELDS_PATH + "?offset=0&limit=1&query=cql.allRecords=1 sortby name";
    CustomFieldCollection fields = getWithOk(resourcePath).as(CustomFieldCollection.class);
    assertEquals(1, fields.getCustomFields().size());
    assertEquals(2, (int) fields.getTotalRecords());
    assertEquals("Department", fields.getCustomFields().get(0).getName());
    assertEquals("Provide a department", fields.getCustomFields().get(0).getHelpText());
  }

  @Test
  public void shouldReturn400OnInvalidLimit() {
    String error = getWithStatus(CUSTOM_FIELDS_PATH + "?limit=-1", SC_BAD_REQUEST).asString();
    assertThat(error, containsString("'limit' parameter is incorrect"));
  }

  @Test
  public void shouldReturn400OnInvalidQuery() {
    String error = getWithStatus(CUSTOM_FIELDS_PATH + "?query=name~~abc", SC_BAD_REQUEST).asString();
    assertThat(error, containsString("no serverChoiceIndexes defined"));
  }

  @Test
  public void shouldReturnFieldOnValidId() throws IOException, URISyntaxException {
    CustomField customField = createCustomField(readFile("fields/post/postCustomField.json"));

    CustomField actual = getWithOk(itemResourcePath(customField.getId())).as(CustomField.class);

    assertEquals("Department", actual.getName());
    assertEquals("department", actual.getRefId());
    assertEquals("Provide a department", actual.getHelpText());
    assertEquals(true, actual.getRequired());
    assertEquals(true, actual.getVisible());
    assertEquals(CustomField.Type.SINGLE_CHECKBOX, actual.getType());
  }

  @Test
  public void shouldReturn404OnMissingId() {
    String error = getWithStatus(itemResourcePath(STUB_FIELD_ID), SC_NOT_FOUND).asString();
    assertThat(error, containsString("CustomField not found by id"));
  }

  @Test
  public void getCustomFieldsById() throws IOException, URISyntaxException {
    CustomField customField = createCustomField(readFile("fields/post/postCustomField.json"));

    CustomField actual = getWithOk(itemResourcePath(customField.getId())).as(CustomField.class);

    assertEquals("Department", actual.getName());
    assertNotNull(actual.getRefId());
    assertEquals("Provide a department", actual.getHelpText());
    assertEquals(true, actual.getRequired());
    assertEquals(true, actual.getVisible());
    assertEquals(CustomField.Type.SINGLE_CHECKBOX, actual.getType());
  }

  @Test
  public void shouldUpdateCustomFieldNameAndRemoveOneOptionOnPut() throws IOException, URISyntaxException {
    CustomField customField = createCustomField(readFile("fields/post/radioButton/postValidRadioButton.json"));
    customField.setName("favoriteFood 2");
    customField.getSelectField().getOptions().getValues().remove(0);
    putWithNoContent(itemResourcePath(customField.getId()), Json.encode(customField), USER2_HEADER);

    CustomField actual = getAllCustomFields(vertx).get(0);
    assertEquals("favoriteFood 2", actual.getName());
    assertEquals("favoritefood", actual.getRefId());
    assertEquals("Choose your favorite food", actual.getHelpText());
    assertEquals(true, actual.getRequired());
    assertEquals(true, actual.getVisible());
    assertEquals(1, (int) actual.getOrder());
    assertEquals(CustomField.Type.RADIO_BUTTON, actual.getType());
    assertThat(actual.getSelectField().getOptions().getValues(), hasSize(2));

    Metadata noteTypeMetadata = actual.getMetadata();
    assertEquals(USER1_ID, noteTypeMetadata.getCreatedByUserId());
    assertEquals("u1", noteTypeMetadata.getCreatedByUsername());
    assertEquals(USER2_ID, noteTypeMetadata.getUpdatedByUserId());
    assertEquals("u2", noteTypeMetadata.getUpdatedByUsername());
  }

  @Test
  public void shouldNotChangeRefIdWhenNameIsSameOnPut() throws IOException, URISyntaxException {
    CustomField customField = createCustomField(readFile("fields/post/postCustomField2.json"));
    String resourcePath = itemResourcePath(customField.getId());
    putWithNoContent(resourcePath, readFile("fields/put/putCustomField2.json"), USER2_HEADER);

    CustomField field = getAllCustomFields(vertx).get(0);
    assertEquals("Expiration Date", field.getName());
    assertEquals("expirationDate", field.getRefId());
    assertEquals("Set expiration date", field.getHelpText());
    assertEquals(true, field.getRequired());
    assertEquals(true, field.getVisible());
    assertEquals(CustomField.Type.TEXTBOX_SHORT, field.getType());

    Metadata noteTypeMetadata = field.getMetadata();

    assertEquals(USER1_ID, noteTypeMetadata.getCreatedByUserId());
    assertEquals("u1", noteTypeMetadata.getCreatedByUsername());

    assertEquals(USER2_ID, noteTypeMetadata.getUpdatedByUserId());
    assertEquals("u2", noteTypeMetadata.getUpdatedByUsername());
  }

  @Test
  public void shouldReturn422WhenCustomFieldTypeChangedOnPut() throws IOException, URISyntaxException {
    CustomField customField = createCustomField(readFile("fields/post/postCustomField.json"));
    String resourcePath = itemResourcePath(customField.getId());
    String putBody = readFile("fields/put/putCustomField2.json");
    Error error = putWithStatus(resourcePath, putBody, SC_UNPROCESSABLE_ENTITY, USER2_HEADER).as(Error.class);
    assertThat(error.getMessage(), containsString("The type of the custom field can not be changed"));
  }

  @Test
  public void shouldReturn422WhenNameIsNullOnPut() throws IOException, URISyntaxException {
    String postBody = readFile("fields/post/postCustomField.json");
    String putBody = readFile("fields/put/putCustomFieldEmptyName.json");

    CustomField customField = createCustomField(postBody);
    String resourcePath = itemResourcePath(customField.getId());
    String error = putWithStatus(resourcePath, putBody, SC_UNPROCESSABLE_ENTITY, USER1_HEADER).asString();
    assertThat(error, containsString("NotNull.message"));
  }

  @Test
  public void shouldReturn422WhenTypeIsNullOnPut() throws IOException, URISyntaxException {
    String postBody = readFile("fields/post/postCustomField.json");
    String putBody = readFile("fields/put/putCustomFieldEmptyType.json");

    CustomField customField = createCustomField(postBody);
    String resourcePath = itemResourcePath(customField.getId());
    String error = putWithStatus(resourcePath, putBody, SC_UNPROCESSABLE_ENTITY).asString();
    assertThat(error, containsString("NotNull.message"));
  }

  @Test
  public void shouldReturn422WhenEntityTypeIsNullOnPut() throws IOException, URISyntaxException {
    String postBody = readFile("fields/post/postCustomField.json");
    String putBody = readFile("fields/put/putCustomFieldEmptyEntityType.json");

    CustomField customField = createCustomField(postBody);
    String resourcePath = itemResourcePath(customField.getId());
    String error = putWithStatus(resourcePath, putBody, SC_UNPROCESSABLE_ENTITY).asString();
    assertThat(error, containsString("NotNull.message"));
  }

  @Test
  public void putCustomFieldsShouldReturn422WhenCollectionEntityTypeIsNull()
      throws IOException, URISyntaxException {
    CustomField field = readJsonFile("fields/post/postCustomField.json", CustomField.class);

    PutCustomFieldCollection request =
        new PutCustomFieldCollection().withCustomFields(List.of(field));
    String error =
        putWithStatus(CUSTOM_FIELDS_PATH, Json.encode(request), SC_UNPROCESSABLE_ENTITY).asString();
    assertThat(error, containsString("NotNull.message"));
  }

  @Test
  public void putCustomFieldsShouldReturn422OnMultipleEntityTypes()
      throws IOException, URISyntaxException {
    PutCustomFieldCollection request =
        readJsonFile("fields/put/putCustomFieldCollection.json", PutCustomFieldCollection.class);
    request.getCustomFields().get(0).setEntityType("other");
    String error =
        putWithStatus(
                CUSTOM_FIELDS_PATH, Json.encode(request), SC_UNPROCESSABLE_ENTITY, USER1_HEADER)
            .asString();
    assertThat(error, containsString("Multiple entityTypes found"));
  }

  @Test
  public void putCustomFieldsShouldReturn422WhenCollectionEntityTypeDiffers()
      throws IOException, URISyntaxException {
    PutCustomFieldCollection request =
        readJsonFile("fields/put/putCustomFieldCollection.json", PutCustomFieldCollection.class);
    request.setEntityType("other");
    String error =
        putWithStatus(
                CUSTOM_FIELDS_PATH, Json.encode(request), SC_UNPROCESSABLE_ENTITY, USER1_HEADER)
            .asString();
    assertThat(
        error,
        containsString(
            "Collection entityType 'other' does not match custom fields entityType 'user'"));
  }

  @Test
  public void shouldReturn422WhenFieldFormatIsNullOnPut() throws IOException, URISyntaxException {
    CustomField field = readJsonFile("fields/post/textbox/postTextBoxShort.json", CustomField.class);
    String postBody = Json.encode(field);
    CustomField fieldWithEmailFormat = field.withTextField(new TextField().withFieldFormat(TextField.FieldFormat.EMAIL));
    String putBody = Json.encode(fieldWithEmailFormat);

    CustomField customField = createCustomField(postBody);
    String resourcePath = itemResourcePath(customField.getId());
    String error = putWithStatus(resourcePath, putBody, SC_UNPROCESSABLE_ENTITY).asString();
    assertThat(error, containsString("The format of the custom field can not be changed"));
  }

  @Test
  public void shouldUpdateAllCustomFields() throws IOException, URISyntaxException {
    final CustomField field1 = createCustomField(readFile("fields/post/postCustomField.json"));
    createCustomField(readFile("fields/post/postCustomField2.json"));

    List<CustomField> customFieldBeforeUpdate = getAllCustomFields(vertx);
    customFieldBeforeUpdate.sort(Comparator.comparingInt(CustomField::getOrder));
    CustomField firstField = customFieldBeforeUpdate.get(0);
    CustomField secondField = customFieldBeforeUpdate.get(1);
    assertEquals(1, (int) firstField.getOrder());
    assertEquals(2, (int) secondField.getOrder());

    PutCustomFieldCollection request = readJsonFile("fields/put/putCustomFieldCollection.json", PutCustomFieldCollection.class);
    request.getCustomFields().get(1).setId(field1.getId());
    putWithNoContent(CUSTOM_FIELDS_PATH, Json.encode(request), USER2_HEADER);

    List<CustomField> customFieldsAfterUpdate = getAllCustomFields(vertx);
    customFieldsAfterUpdate.sort(Comparator.comparingInt(CustomField::getOrder));

    CustomField firstFieldUpdated = customFieldsAfterUpdate.get(0);
    assertEquals("New Expiration Date", firstFieldUpdated.getName());
    assertEquals("newExpirationDate", firstFieldUpdated.getRefId());
    assertEquals("Set new expiration date", firstFieldUpdated.getHelpText());
    assertEquals(true, firstFieldUpdated.getRequired());
    assertEquals(true, firstFieldUpdated.getVisible());
    assertEquals(1, (int) firstFieldUpdated.getOrder());
    assertEquals(CustomField.Type.TEXTBOX_SHORT, firstFieldUpdated.getType());
    Metadata firstFieldUpdatedMetadata = firstFieldUpdated.getMetadata();
    assertEquals(USER2_ID, firstFieldUpdatedMetadata.getCreatedByUserId());
    assertEquals("u2", firstFieldUpdatedMetadata.getCreatedByUsername());
    assertEquals(USER2_ID, firstFieldUpdatedMetadata.getUpdatedByUserId());
    assertEquals("u2", firstFieldUpdatedMetadata.getUpdatedByUsername());

    CustomField secondFieldUpdated = customFieldsAfterUpdate.get(1);
    assertEquals("Department 2", secondFieldUpdated.getName());
    assertEquals("department", secondFieldUpdated.getRefId());
    assertEquals("Provide a second department", secondFieldUpdated.getHelpText());
    assertEquals(false, secondFieldUpdated.getRequired());
    assertEquals(false, secondFieldUpdated.getVisible());
    assertEquals(2, (int) secondFieldUpdated.getOrder());
    assertEquals(CustomField.Type.SINGLE_CHECKBOX, secondFieldUpdated.getType());
    Metadata secondFieldUpdatedMetadata = secondFieldUpdated.getMetadata();
    assertEquals(USER1_ID, secondFieldUpdatedMetadata.getCreatedByUserId());
    assertEquals("u1", secondFieldUpdatedMetadata.getCreatedByUsername());
    assertEquals(USER2_ID, secondFieldUpdatedMetadata.getUpdatedByUserId());
    assertEquals("u2", secondFieldUpdatedMetadata.getUpdatedByUsername());
  }

  @Test
  public void putCustomFieldsShouldHaveNoSideEffectsOnOtherEntityTypes()
      throws IOException, URISyntaxException {
    createFieldsMultipleEntityTypes();
    long distinctEntityTypeCount =
        getAllCustomFields(vertx).stream().map(CustomField::getEntityType).distinct().count();
    assertEquals(2, distinctEntityTypeCount);

    String newHelpText = "new help text";
    CustomField updatedCustomField =
        readJsonFile("fields/post/postCustomField.json", CustomField.class)
            .withHelpText(newHelpText);
    PutCustomFieldCollection request =
        new PutCustomFieldCollection()
            .withCustomFields(List.of(updatedCustomField))
            .withEntityType("user");
    putWithNoContent(CUSTOM_FIELDS_PATH, Json.encode(request), USER2_HEADER);

    List<CustomField> results = getAllCustomFields(vertx);
    results.sort(Comparator.comparingInt(CustomField::getOrder));
    long distinctResultEntityTypeCount =
        results.stream().map(CustomField::getEntityType).distinct().count();
    assertEquals(2, distinctResultEntityTypeCount);
    assertEquals(newHelpText, results.get(0).getHelpText());
  }

  @Test
  public void shouldUpdateCustomFieldsWhenReOrderAndFieldRemoved() throws IOException, URISyntaxException {
    createCustomField(readFile("fields/post/postCustomField.json"));
    final CustomField field2 = createCustomField(readFile("fields/post/postCustomField2.json"));
    final CustomField field3 = createCustomField(readFile("fields/post/postCustomField2.json"));

    List<CustomField> customFieldBeforeUpdate = getAllCustomFields(vertx);
    customFieldBeforeUpdate.sort(Comparator.comparingInt(CustomField::getOrder));
    CustomField firstField = customFieldBeforeUpdate.get(0);
    CustomField secondField = customFieldBeforeUpdate.get(1);
    CustomField thirdField = customFieldBeforeUpdate.get(2);

    assertEquals(3, customFieldBeforeUpdate.size());
    assertEquals(1, (int) firstField.getOrder());
    assertEquals(2, (int) secondField.getOrder());
    assertEquals(3, (int) thirdField.getOrder());

    final String cfNameUpdated = "Expiration Date updated";
    field2.setName(cfNameUpdated);
    field3.setRefId("some-ref-id_1");
    PutCustomFieldCollection request = new PutCustomFieldCollection().withCustomFields(Arrays.asList(field3, field2)).withEntityType("user");
    putWithNoContent(CUSTOM_FIELDS_PATH, Json.encode(request), USER2_HEADER);

    List<CustomField> customFieldsAfterUpdate = getAllCustomFields(vertx);
    customFieldsAfterUpdate.sort(Comparator.comparingInt(CustomField::getOrder));
    CustomField firstFieldUpdated = customFieldsAfterUpdate.get(0);
    CustomField secondFieldUpdated = customFieldsAfterUpdate.get(1);
    assertEquals(2, customFieldsAfterUpdate.size());

    assertEquals(1, (int) firstFieldUpdated.getOrder());
    assertEquals("expirationDate_2",  firstFieldUpdated.getRefId());

    assertEquals(2, (int) secondFieldUpdated.getOrder());
    assertEquals(cfNameUpdated, secondFieldUpdated.getName());
  }

  @Test
  public void shouldCreateCustomFieldWhenIdIsEmptyAndDeleteOldOnCollectionPut() throws IOException, URISyntaxException {
    final CustomField field1 = createCustomField(readFile("fields/post/postCustomField.json"));
    final CustomField field2 = createCustomField(readFile("fields/post/postCustomField2.json"));
    String field2Id = field2.getId();

    List<CustomField> customFieldBeforeUpdate = getAllCustomFields(vertx);
    customFieldBeforeUpdate.sort(Comparator.comparingInt(CustomField::getOrder));
    CustomField firstField = customFieldBeforeUpdate.get(0);
    CustomField secondField = customFieldBeforeUpdate.get(1);
    assertEquals(2, customFieldBeforeUpdate.size());
    assertEquals(CustomField.Type.SINGLE_CHECKBOX, firstField.getType());
    assertEquals(CustomField.Type.TEXTBOX_SHORT, secondField.getType());

    //update cf id
    field2.setId(null);

    PutCustomFieldCollection request = new PutCustomFieldCollection().withCustomFields(Arrays.asList(field1, field2)).withEntityType("user");
    putWithNoContent(CUSTOM_FIELDS_PATH, Json.encode(request), USER2_HEADER);

    List<CustomField> customFieldsAfterUpdate = getAllCustomFields(vertx);
    customFieldsAfterUpdate.sort(Comparator.comparingInt(CustomField::getOrder));
    CustomField firstFieldUpdated = customFieldsAfterUpdate.get(0);
    CustomField secondFieldUpdated = customFieldsAfterUpdate.get(1);
    assertEquals(2, customFieldsAfterUpdate.size());
    assertEquals(field1.getId(), firstFieldUpdated.getId());
    assertNotEquals(field2Id, secondFieldUpdated.getId());
  }

  @Test
  public void shouldReturn422WhenChangedTypeOnPutCollection() throws IOException, URISyntaxException {
    final CustomField field1 = createCustomField(readFile("fields/post/postCustomField.json"));
    final CustomField field2 = createCustomField(readFile("fields/post/postCustomField2.json"));

    List<CustomField> customFieldBeforeUpdate = getAllCustomFields(vertx);
    customFieldBeforeUpdate.sort(Comparator.comparingInt(CustomField::getOrder));
    CustomField firstField = customFieldBeforeUpdate.get(0);
    CustomField secondField = customFieldBeforeUpdate.get(1);
    assertEquals(2, customFieldBeforeUpdate.size());
    assertEquals(CustomField.Type.SINGLE_CHECKBOX, firstField.getType());
    assertEquals(CustomField.Type.TEXTBOX_SHORT, secondField.getType());

    //update cf type
    field2.setType(CustomField.Type.TEXTBOX_LONG);

    PutCustomFieldCollection request = new PutCustomFieldCollection().withCustomFields(Arrays.asList(field1, field2)).withEntityType("user");
    String error = putWithStatus(CUSTOM_FIELDS_PATH, Json.encode(request), SC_UNPROCESSABLE_ENTITY, USER2_HEADER).asString();
    assertThat(error, containsString("The type of the custom field can not be changed"));
  }

  @Test
  public void deleteCustomFieldsById() throws IOException, URISyntaxException {
    String postBody = readFile("fields/post/postCustomField.json");
    CustomField customField = createCustomField(postBody);

    deleteWithNoContent(itemResourcePath(customField.getId()));
    String error = deleteWithStatus(itemResourcePath(STUB_FIELD_ID), SC_NOT_FOUND).asString();
    assertThat(error, containsString("CustomField not found by id"));
  }

  @Test
  public void deleteCustomFieldAndReorderLast() throws IOException, URISyntaxException {
    String postBody1 = readFile("fields/post/postCustomField.json");
    String postBody2 = readFile("fields/post/postCustomFieldOrder2.json");
    String postBody3 = readFile("fields/post/postCustomFieldOrder3.json");
    CustomField customFieldOne = createCustomField(postBody1);
    createCustomField(postBody2);
    createCustomField(postBody3);

    deleteWithNoContent(itemResourcePath(customFieldOne.getId()));

    CustomFieldCollection fields = getWithOk(CUSTOM_FIELDS_PATH).as(CustomFieldCollection.class);
    assertEquals(2, fields.getCustomFields().size());
    assertEquals(1, (int) fields.getCustomFields().get(0).getOrder());
    assertEquals(2, (int) fields.getCustomFields().get(1).getOrder());
  }

  @Test
  public void deleteCustomFieldAndReorderLast1212() throws IOException, URISyntaxException {
    createCustomField(readFile("fields/post/postCustomFieldOrder3.json"));
    createCustomField(readFile("fields/post/postCustomField.json"));
    createCustomField(readFile("fields/post/postCustomFieldOrder2.json"));

    deleteWithStatus(CUSTOM_FIELDS_PATH + "/11111111-2222-3333-a444-555555555555", SC_NOT_FOUND);

    String resourcePath = CUSTOM_FIELDS_PATH + "?query=cql.allRecords=1 sortby order";
    CustomFieldCollection fields = getWithOk(resourcePath).as(CustomFieldCollection.class);
    assertEquals(3, fields.getCustomFields().size());
    assertEquals(1, (int) fields.getCustomFields().get(0).getOrder());
    assertEquals(2, (int) fields.getCustomFields().get(1).getOrder());
    assertEquals(3, (int) fields.getCustomFields().get(2).getOrder());
  }

  @Test
  public void shouldReturn404WhenDeleteRequestHasNotExistingUUID() {
    String resourcePath = CUSTOM_FIELDS_PATH + "/11111111-2222-3333-a444-555555555555";
    String error = deleteWithStatus(resourcePath, SC_NOT_FOUND).asString();
    assertThat(error, containsString("CustomField not found by id"));
  }

  @Test
  public void shouldReturn400WhenDeleteRequestHasInvalidUUID() {
    String resourcePath = CUSTOM_FIELDS_PATH + "/11111111-3-1111-333-111111111111";
    String error = deleteWithStatus(resourcePath, SC_BAD_REQUEST).asString();
    assertThat(error, containsString("'id' parameter is incorrect"));
  }

  @Test
  public void shouldDeleteAndGenerateNewRefId() throws IOException, URISyntaxException {
    CustomField customFieldOne = createCustomField(readFile("fields/post/postCustomField.json"));

    createCustomField(readFile("fields/post/postCustomFieldOrder2.json"));

    String id = customFieldOne.getId();
    deleteWithNoContent(itemResourcePath(id));

    CustomField customFieldThree = createCustomField(readFile("fields/post/postCustomFieldOrder3.json"));

    assertTrue(customFieldThree.getRefId().endsWith("3"));
  }

  @Test
  public void shouldReturnEmptyStatsForExistingField() throws IOException, URISyntaxException {
    CustomField field = createCustomField(readFile("fields/post/postCustomField.json"));

    String resourcePath = itemStatResourcePath(field.getId());
    CustomFieldStatistic stats = getWithOk(resourcePath).as(CustomFieldStatistic.class);

    assertEquals(stats, new CustomFieldStatistic()
      .withFieldId(field.getId())
      .withEntityType(field.getEntityType())
      .withCount(0));
  }

  @Test
  public void shouldReturnEmptyOptionStatsForExistingField() throws IOException, URISyntaxException {
    CustomField field = createCustomField(readFile("fields/post/singleSelect/postValidSingleSelect.json"));
    String optId = field.getSelectField().getOptions().getValues().get(0).getId();
    String resourcePath = itemOptionStatResourcePath(field.getId(), optId);
    CustomFieldOptionStatistic stats = getWithOk(resourcePath).as(CustomFieldOptionStatistic.class);

    assertEquals(stats, new CustomFieldOptionStatistic()
      .withOptionId(optId)
      .withCustomFieldId(field.getId())
      .withEntityType(field.getEntityType())
      .withCount(0));
  }

  @Test
  @SuppressWarnings("squid:S2699")
  public void shouldReturn404OnGetOptionStatsForNotExistingField() {
    String fakeFieldId = "11111111-2222-3333-a444-555555555555";
    String fakeOptId = "opt_0";
    String resourcePath = itemOptionStatResourcePath(fakeFieldId, fakeOptId);
    getWithStatus(resourcePath, SC_NOT_FOUND);
  }

  @Test
  @SuppressWarnings("squid:S2699")
  public void shouldReturn422OnGetOptionStatsForNotSelectableField() throws IOException, URISyntaxException {
    CustomField field = createCustomField(readFile("fields/post/postCustomField.json"));
    String fakeOptId = "opt_0";
    String resourcePath = itemOptionStatResourcePath(field.getId(), fakeOptId);
    getWithStatus(resourcePath, SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @SuppressWarnings("squid:S2699")
  public void shouldReturn422OnGetOptionStatsForNotExistingFieldOption() throws IOException, URISyntaxException {
    CustomField field = createCustomField(readFile("fields/post/singleSelect/postValidSingleSelect.json"));
    String fakeOptId = "opt_10";
    String resourcePath = itemOptionStatResourcePath(field.getId(), fakeOptId);
    getWithStatus(resourcePath, SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldFailWith404WhenStatsRequestedForNonExistingField() {
    String error = getWithStatus(itemStatResourcePath(STUB_FIELD_ID), SC_NOT_FOUND).asString();
    assertThat(error, containsString("CustomField not found by id"));
  }

  private void createFieldsMultipleEntityTypes() throws IOException, URISyntaxException {
    createCustomField(readFile("fields/post/postCustomField.json"));
    createCustomField(readFile("fields/post/postCustomField-package.json"));
  }

  @Test
  public void postCustomFieldWithSectionOfSameEntityType() throws IOException, URISyntaxException {
    CustomFieldSection section = createSection("Contact", "user");
    CustomField field = readJsonFile("fields/post/postCustomField.json", CustomField.class)
      .withSectionId(section.getId());

    CustomField created = createCustomField(Json.encode(field));

    assertEquals(section.getId(), created.getSectionId());
    assertEquals(section.getId(), getAllCustomFields(vertx).get(0).getSectionId());
  }

  @Test
  public void postCustomFieldShouldReturn422WhenSectionNotFound() throws IOException, URISyntaxException {
    CustomField field = readJsonFile("fields/post/postCustomField.json", CustomField.class)
      .withSectionId(STUB_FIELD_ID);

    String error = postWithStatus(CUSTOM_FIELDS_PATH, Json.encode(field), SC_UNPROCESSABLE_ENTITY, USER1_HEADER)
      .asString();

    assertThat(error, containsString("Custom field section with id '" + STUB_FIELD_ID + "' not found"));
    assertTrue(getAllCustomFields(vertx).isEmpty());
  }

  @Test
  public void postCustomFieldShouldReturn422WhenSectionHasOtherEntityType() throws IOException, URISyntaxException {
    CustomFieldSection section = createSection("Package info", "package");
    CustomField field = readJsonFile("fields/post/postCustomField.json", CustomField.class)
      .withSectionId(section.getId());

    String error = postWithStatus(CUSTOM_FIELDS_PATH, Json.encode(field), SC_UNPROCESSABLE_ENTITY, USER1_HEADER)
      .asString();

    assertThat(error, containsString("belongs to entityType 'package', but the custom field has entityType 'user'"));
  }

  @Test
  public void putCustomFieldShouldAssignAndUnassignSection() throws IOException, URISyntaxException {
    CustomFieldSection section = createSection("Contact", "user");
    CustomField created = createCustomField(readFile("fields/post/postCustomField.json"));

    putWithNoContent(itemResourcePath(created.getId()), Json.encode(created.withSectionId(section.getId())), USER1_HEADER);
    assertEquals(section.getId(), getAllCustomFields(vertx).get(0).getSectionId());

    putWithNoContent(itemResourcePath(created.getId()), Json.encode(created.withSectionId(null)), USER1_HEADER);
    assertEquals(null, getAllCustomFields(vertx).get(0).getSectionId());
  }

  @Test
  public void putCustomFieldShouldReturn422WhenSectionNotFound() throws IOException, URISyntaxException {
    CustomField created = createCustomField(readFile("fields/post/postCustomField.json"));

    String error = putWithStatus(itemResourcePath(created.getId()),
      Json.encode(created.withSectionId(STUB_FIELD_ID)), SC_UNPROCESSABLE_ENTITY, USER1_HEADER).asString();

    assertThat(error, containsString("Custom field section with id '" + STUB_FIELD_ID + "' not found"));
    assertEquals(null, getAllCustomFields(vertx).get(0).getSectionId());
  }

  @Test
  public void putCustomFieldCollectionShouldValidateSections() throws IOException, URISyntaxException {
    CustomFieldSection section = createSection("Contact", "user");
    PutCustomFieldCollection request =
      readJsonFile("fields/put/putCustomFieldCollection.json", PutCustomFieldCollection.class);
    request.getCustomFields().forEach(field -> field.setSectionId(section.getId()));

    putWithNoContent(CUSTOM_FIELDS_PATH, Json.encode(request), USER1_HEADER);

    List<CustomField> fields = getAllCustomFields(vertx);
    assertThat(fields, hasSize(2));
    fields.forEach(field -> assertEquals(section.getId(), field.getSectionId()));
  }

  @Test
  public void putCustomFieldCollectionShouldReturn422WhenSectionNotFound() throws IOException, URISyntaxException {
    createCustomField(readFile("fields/post/postCustomField.json"));
    PutCustomFieldCollection request =
      readJsonFile("fields/put/putCustomFieldCollection.json", PutCustomFieldCollection.class);
    request.getCustomFields().get(1).setSectionId(STUB_FIELD_ID);

    String error = putWithStatus(CUSTOM_FIELDS_PATH, Json.encode(request), SC_UNPROCESSABLE_ENTITY, USER1_HEADER)
      .asString();

    assertThat(error, containsString("Custom field section with id '" + STUB_FIELD_ID + "' not found"));
    List<CustomField> fields = getAllCustomFields(vertx);
    assertThat(fields, hasSize(1));
    assertEquals("Department", fields.get(0).getName());
  }

  private CustomFieldSection createSection(String name, String entityType) {
    CustomFieldSection section = new CustomFieldSection().withName(name).withEntityType(entityType);
    return postWithStatus(CUSTOM_FIELD_SECTIONS_PATH, Json.encode(section), SC_CREATED, USER1_HEADER)
      .as(CustomFieldSection.class);
  }

  private CustomField createCustomField(String postBody) {
    return postWithStatus(CUSTOM_FIELDS_PATH, postBody, SC_CREATED, USER1_HEADER).as(CustomField.class);
  }
}
