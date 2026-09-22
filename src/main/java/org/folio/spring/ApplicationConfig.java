package org.folio.spring;

import static org.folio.rest.exc.RestExceptionHandlers.baseBadRequestHandler;
import static org.folio.rest.exc.RestExceptionHandlers.baseNotFoundHandler;
import static org.folio.rest.exc.RestExceptionHandlers.baseUnauthorizedHandler;
import static org.folio.rest.exc.RestExceptionHandlers.baseUnprocessableHandler;
import static org.folio.rest.exc.RestExceptionHandlers.completionCause;
import static org.folio.rest.exc.RestExceptionHandlers.generalHandler;
import static org.folio.rest.exc.RestExceptionHandlers.logged;
import static org.folio.rest.exceptions.CustomFieldExceptionHandlers.invalidOrderHandler;
import static org.folio.rest.exceptions.CustomFieldExceptionHandlers.invalidValueHandler;
import static org.folio.rest.exceptions.CustomFieldExceptionHandlers.sectionInUseHandler;

import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import javax.ws.rs.core.Response;

import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;

import org.folio.common.pf.PartialFunction;
import org.folio.db.exc.translation.DBExceptionTranslator;
import org.folio.db.exc.translation.DBExceptionTranslatorFactory;
import org.folio.service.NoOpRecordService;
import org.folio.service.RecordService;
import org.folio.service.spi.RecordServiceFactory;

@Log4j2
@Configuration
@ComponentScan(basePackages = {
  "org.folio.repository",
  "org.folio.service",
  "org.folio.validate"})
public class ApplicationConfig {


  @Bean
  public PartialFunction<Throwable, Response> customFieldsExcHandler() {
    return logged(baseBadRequestHandler()
      .orElse(baseNotFoundHandler())
      .orElse(baseUnauthorizedHandler())
      .orElse(invalidValueHandler())
      .orElse(invalidOrderHandler())
      .orElse(sectionInUseHandler())
      .orElse(baseUnprocessableHandler())
      .orElse(generalHandler())
      .compose(completionCause()));
  }

  @Bean
  public DBExceptionTranslator excTranslator(@Value("${db.exception.translator.name:postgresql}") String translatorName) {
    DBExceptionTranslatorFactory factory = DBExceptionTranslatorFactory.instance();
    return factory.create(translatorName);
  }

  @Bean
  public PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
    PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
    configurer.setLocation(new ClassPathResource("custom-fields-application.properties"));
    return configurer;
  }

  @Bean
  public RecordService recordService(Vertx vertx) {
    RecordService rc;

    var serviceLoader = ServiceLoader.load(RecordServiceFactory.class);
    var factories = StreamSupport.stream(serviceLoader.spliterator(), false).toList();

    if (CollectionUtils.isEmpty(factories)) {
      rc = new NoOpRecordService();

      // log warning: No concrete implementation !!
      log.warn("No implementation of {} service provider interface found in the classpath.\n" +
          "Check that the correct implementation class is set in META-INF/services/{} configuration file.\n" +
          "The default No-op service will be used instead: some functions might not work properly!",
          RecordServiceFactory.class.getName(), RecordServiceFactory.class.getName());
    } else {
      RecordServiceFactory factory = factories.iterator().next();
      rc = factory.create(vertx);

      if (factories.size() > 1) {
        // log warning: too many implementations
        log.warn("Too many implementations of {} service provider interface found. The first one will be used: {}",
          RecordServiceFactory.class.getName(), rc.getClass().getName());
      }
    }

    return rc;
  }
}
