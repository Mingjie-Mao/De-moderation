package com.campusguard.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionMonitoringConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=prod");

    @Test
    void publicProductionDeploymentCanDisableAnonymousMetrics() {
        context.withPropertyValues("EXPOSE_PROMETHEUS=false").run(application ->
                assertThat(application.getEnvironment().getProperty(
                        "campusguard.security.expose-prometheus", Boolean.class)).isFalse());
    }

    @Test
    void privateProductionDeploymentCanEnablePrometheusScraping() {
        context.withPropertyValues("EXPOSE_PROMETHEUS=true").run(application ->
                assertThat(application.getEnvironment().getProperty(
                        "campusguard.security.expose-prometheus", Boolean.class)).isTrue());
    }
}
