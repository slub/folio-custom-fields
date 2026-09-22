package org.folio.validate.section;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import org.folio.rest.jaxrs.model.CustomFieldSection;
import org.folio.spring.TestConfiguration;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestConfiguration.class)
public class CustomFieldSectionValidatorTest {

  @Autowired
  private CustomFieldSectionValidator validator;

  @Test
  public void shouldAcceptValidSection() {
    validator.validate(section("Contact", "user"));
  }

  @Test
  public void shouldRejectBlankName() {
    IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
      () -> validator.validate(section(" ", "user")));
    assertThat(exc.getMessage(), containsString("The 'name' of the section must not be blank"));
  }

  @Test
  public void shouldRejectTooLongName() {
    IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
      () -> validator.validate(section("x".repeat(66), "user")));
    assertThat(exc.getMessage(), containsString("The 'name' length cannot be more than 65"));
  }

  @Test
  public void shouldRejectBlankEntityType() {
    IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
      () -> validator.validate(section("Contact", "")));
    assertThat(exc.getMessage(), containsString("The 'entityType' of the section must not be blank"));
  }

  private CustomFieldSection section(String name, String entityType) {
    return new CustomFieldSection().withName(name).withEntityType(entityType);
  }
}
